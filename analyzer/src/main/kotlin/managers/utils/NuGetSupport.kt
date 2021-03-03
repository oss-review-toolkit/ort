/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
 * Copyright (C) 2020 Bosch.IO GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package org.ossreviewtoolkit.analyzer.managers.utils

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.xml.XmlFactory
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

import java.io.File
import java.io.IOException
import java.io.StringReader
import java.util.SortedMap
import java.util.concurrent.TimeUnit

import javax.xml.bind.annotation.XmlRootElement

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

import okhttp3.CacheControl
import okhttp3.Request

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.managers.utils.NuGetAllPackageData.PackageData
import org.ossreviewtoolkit.analyzer.managers.utils.NuGetAllPackageData.PackageDetails
import org.ossreviewtoolkit.analyzer.managers.utils.NuGetAllPackageData.PackageSpec
import org.ossreviewtoolkit.analyzer.managers.utils.NuGetAllPackageData.ServiceIndex
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Failure
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Result
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.Success
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.utils.OkHttpClientHelper
import org.ossreviewtoolkit.utils.await
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.logOnce
import org.ossreviewtoolkit.utils.searchUpwardsForFile

// See https://docs.microsoft.com/en-us/nuget/api/overview.
private const val DEFAULT_SERVICE_INDEX_URL = "https://api.nuget.org/v3/index.json"
private const val REGISTRATIONS_BASE_URL_TYPE = "RegistrationsBaseUrl/3.6.0"

private val VERSION_RANGE_CHARS = charArrayOf('[', ']', '(', ')', ',')

class NuGetSupport(serviceIndexUrls: List<String> = listOf(DEFAULT_SERVICE_INDEX_URL)) {
    companion object {
        val JSON_MAPPER = JsonMapper().registerKotlinModule()

        val XML_MAPPER = XmlMapper(XmlFactory().apply {
            // Work-around for https://github.com/FasterXML/jackson-module-kotlin/issues/138.
            xmlTextElementName = "value"
        }).registerKotlinModule()

        fun create(definitionFile: File): NuGetSupport {
            val configXmlReader = NuGetConfigFileReader()
            val configFile = definitionFile.parentFile.searchUpwardsForFile("nuget.config", ignoreCase = true)
            val serviceIndexUrls = configFile?.let { configXmlReader.getRegistrationsBaseUrls(it) }

            return serviceIndexUrls?.let { NuGetSupport(it) } ?: NuGetSupport()
        }
    }

    private val client = OkHttpClientHelper.buildClient {
        // Cache responses more aggressively than the NuGet registry's default of "max-age=120, must-revalidate"
        // (for the index URL) or even "no-store" (for the package data). More or less arbitrarily choose 7 days /
        // 1 week as dependencies of a released package should actually not change at all. But refreshing after a
        // few days when the typical incremental scanning / curation creating cycle is through should be fine.
        addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val cacheControl = CacheControl.Builder()
                .maxAge(7, TimeUnit.DAYS)
                .build()
            response.newBuilder()
                .header("cache-control", cacheControl.toString())
                .build()
        }
    }

    private val serviceIndices = runBlocking {
        serviceIndexUrls.map {
            async { mapFromUrl<ServiceIndex>(JSON_MAPPER, it) }
        }.awaitAll().filterIsInstance<Success<ServiceIndex>>().map { it.result }
    }

    // Note: Remove a trailing slash as one is always added later to separate from the path, and a double-slash would
    // break the URL!
    private val registrationsBaseUrls = serviceIndices
        .flatMap { it.resources }
        .filter { it.type == REGISTRATIONS_BASE_URL_TYPE }
        .map { it.id.removeSuffix("/") }

    private val packageMap = mutableMapOf<Identifier, Pair<NuGetAllPackageData, Package>>()

    /**
     * Send an HTTP GET request to the given [url] and use the provided [mapper] to deserialize the response to the
     * desired target type [T]. As exceptions in coroutines are difficult to deal with, return a [Result] that
     * wraps an error message in case an error was encountered.
     * NOTE: It would be easier to use the standard Kotlin _Result_ class here, which offers advanced functionality
     * to process results. There is, however, currently a bug in Kotlin, which prevents that this function can
     * return such a _Result_. Details (including links to bug tickets) can be found here:
     * https://medium.com/@amatkivskiy/classcastexception-type-cannot-be-cast-to-kotlin-result-507e7a824a81.
     * TODO: Change return type to kotlin.Result when the bug mentioned above is fixed; then also the extension
     * functions defined on [Result] are obsolete.
     */
    private suspend inline fun <reified T> mapFromUrl(mapper: ObjectMapper, url: String): Result<T> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .build()

            try {
                client.newCall(request).await().use { response ->
                    if (response.cacheResponse != null) {
                        NuGetSupport.log.debug { "Retrieved '$url' response from local cache." }
                    } else {
                        NuGetSupport.log.debug {
                            "Retrieved '$url' response from remote server with status ${response.code}."
                        }
                    }

                    if (!response.isSuccessful) {
                        throw IOException("Got non-success response status ${response.code} for '$url'.")
                    }

                    val bodyReader = response.body?.charStream() ?: StringReader("")
                    Success(mapper.readValue(bodyReader))
                }
            } catch (e: IOException) {
                Failure(e.collectMessagesAsString())
            }
        }

    private suspend fun getAllPackageData(id: Identifier): Result<NuGetAllPackageData> {
        // Note: The package name in the URL is case-sensitive and must be lower-case!
        val lowerId = id.name.toLowerCase()

        return registrationsBaseUrls.asSequence().mapNotNull { baseUrl ->
            val dataUrl = "$baseUrl/$lowerId/${id.version}.json"
            runBlocking { mapFromUrl<PackageData>(JSON_MAPPER, dataUrl) }
        }.filterIsInstance<Success<PackageData>>().firstOrNull()?.let { enrichPackageData(id, it.result) }
            ?: Failure(
                "Failed to get package data for '${id.toCoordinates()}': " +
                        "Could not retrieve package data for '$lowerId' from any of $registrationsBaseUrls."
            )
    }

    private suspend fun enrichPackageData(id: Identifier, data: PackageData): Result<NuGetAllPackageData> {
        val nupkgUrl = data.packageContent
        val nuspecUrl = nupkgUrl.replace(".${id.version}.nupkg", ".nuspec")

        return mapFromUrl<PackageDetails>(JSON_MAPPER, data.catalogEntry).flatMap { packageDetails ->
            mapFromUrl<PackageSpec>(XML_MAPPER, nuspecUrl).map { packageSpec ->
                NuGetAllPackageData(data, packageDetails, packageSpec) }
        }
    }

    private fun getPackage(all: NuGetAllPackageData): Package {
        val vcs = all.spec.metadata.repository?.let {
            VcsInfo(
                type = VcsType(it.type.orEmpty()),
                url = it.url.orEmpty(),
                revision = (it.branch ?: it.commit).orEmpty(),
                resolvedRevision = it.commit,
                path = ""
            )
        } ?: VcsInfo.EMPTY

        val license = with(all.spec.metadata) {
            // Note: "licenseUrl" has been deprecated in favor of "license", see
            // https://docs.microsoft.com/en-us/nuget/reference/nuspec#licenseurl
            val licenseValue = license?.value?.takeUnless { license.type == "file" }
            licenseValue ?: licenseUrl?.takeUnless { it == "https://aka.ms/deprecateLicenseUrl" }
        }

        return with(all.details) {
            Package(
                id = getIdentifier(id, version),
                // TODO: Find a way to track authors.
                authors = sortedSetOf(),
                declaredLicenses = setOfNotNull(license).toSortedSet(),
                description = description.orEmpty(),
                homepageUrl = projectUrl.orEmpty(),
                binaryArtifact = RemoteArtifact(
                    url = all.data.packageContent,
                    hash = Hash.create("$packageHashAlgorithm-$packageHash")
                ),
                sourceArtifact = RemoteArtifact.EMPTY,
                vcs = vcs
            )
        }
    }

    /**
     * Load package information for the given [references]. Fetch the data for the packages that have not been loaded
     * yet in parallel.
     */
    private suspend fun loadPackages(references: Collection<Identifier>, issues: MutableCollection<OrtIssue>):
            List<Pair<NuGetAllPackageData, Package>> = withContext(Dispatchers.IO) {
        val (idsAvailable, idsToLoad) = references.partition { packageMap.containsKey(it) }

        val loadResults = idsToLoad.associateWith { async { getAllPackageData(it) } }
            .mapValues { entry ->
                entry.value.await().map { it to getPackage(it) }
            }

        val loadedPackages = loadResults.mapNotNull { entry ->
            entry.value.getOrNull()?.let { entry.key to it }
        }.toMap()

        packageMap += loadedPackages
        loadResults.mapNotNull { it.value.exceptionOrNull() }.forEach { msg ->
            issues += NuGetSupport.createAndLogIssue(
                source = "NuGet",
                message = msg
            )
        }

        idsAvailable.mapNotNull { packageMap[it] } + loadedPackages.values
    }

    suspend fun buildDependencyTree(
        references: Collection<Identifier>,
        dependencies: MutableCollection<PackageReference>,
        packages: MutableCollection<Package>,
        issues: MutableCollection<OrtIssue>
    ) {
        val pkgData = loadPackages(references, issues)

        pkgData.forEach { (all, pkg) ->
            val pkgRef = pkg.toReference()
            dependencies += pkgRef

            // As NuGet dependencies are very repetitive, truncate the tree at already known branches to avoid it to
            // grow really huge.
            if (packages.add(pkg)) {
                // TODO: Consider mapping dependency groups to scopes.
                val referredDependencies =
                    all.details.dependencyGroups.flatMapTo(mutableSetOf()) { it.dependencies }

                buildDependencyTree(
                    referredDependencies.map { dependency ->
                        // TODO: Add support for lock files, see
                        //       https://devblogs.microsoft.com/nuget/enable-repeatable-package-restores-using-a-lock-file/.

                        // Resolve to the lowest applicable version, see
                        // https://docs.microsoft.com/en-us/nuget/concepts/dependency-resolution#lowest-applicable-version.
                        val version = dependency.range.trim { it.isWhitespace() || it in VERSION_RANGE_CHARS }
                            .split(",").first().trim()

                        // TODO: Add support resolving to the highest version for floating versions, see
                        //       https://docs.microsoft.com/en-us/nuget/concepts/dependency-resolution#floating-versions.

                        getIdentifier(dependency.id, version)
                    },
                    pkgRef.dependencies,
                    packages,
                    issues
                )
            } else {
                logOnce(Level.DEBUG) {
                    "Truncating dependencies for '${pkg.id.toCoordinates()}' which were already determined."
                }
            }
        }
    }
}

private fun getIdentifier(name: String, version: String) =
    Identifier(type = "NuGet", namespace = "", name = name, version = version)

interface XmlPackageFileReader {
    fun getPackageReferences(definitionFile: File): Set<Identifier>
}

fun PackageManager.resolveNuGetDependencies(
    definitionFile: File,
    reader: XmlPackageFileReader,
    support: NuGetSupport
): ProjectAnalyzerResult {
    val workingDir = definitionFile.parentFile

    val dependencies = sortedSetOf<PackageReference>()
    val scope = Scope("dependencies", dependencies)

    val packages = sortedSetOf<Package>()
    val issues = mutableListOf<OrtIssue>()

    val references = reader.getPackageReferences(definitionFile)
    runBlocking { support.buildDependencyTree(references, dependencies, packages, issues) }

    val project = Project(
        id = Identifier(
            type = managerName,
            namespace = "",
            name = definitionFile.relativeTo(analysisRoot).invariantSeparatorsPath,
            version = ""
        ),
        definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
        // TODO: Find a way to track authors.
        authors = sortedSetOf(),
        declaredLicenses = sortedSetOf(),
        vcs = VcsInfo.EMPTY,
        vcsProcessed = PackageManager.processProjectVcs(workingDir),
        homepageUrl = "",
        scopeDependencies = sortedSetOf(scope)
    )

    return ProjectAnalyzerResult(project, packages, issues)
}

/**
 * A reader for XML-based NuGet configuration files, see
 * https://docs.microsoft.com/en-us/nuget/reference/nuget-config-file
 */
class NuGetConfigFileReader {
    @JsonIgnoreProperties(ignoreUnknown = true)
    @XmlRootElement(name = "configuration")
    private data class NuGetConfig(
        val packageSources: List<SortedMap<String, String>>
    )

    fun getRegistrationsBaseUrls(configFile: File): List<String> {
        val nuGetConfig = NuGetSupport.XML_MAPPER.readValue<NuGetConfig>(configFile)

        val (remotes, locals) = nuGetConfig.packageSources
            .mapNotNull { it["value"] }
            .partition { it.startsWith("http") }

        if (locals.isNotEmpty()) {
            // TODO: Handle local package sources.
            log.warn { "Ignoring local NuGet package sources $locals." }
        }

        return remotes
    }
}

/**
 * Execute a transformation function [f] (which may fail) on this [Result] object if it is a [Success] or return
 * this object unchanged if it is a [Failure].
 */
private inline fun <T, U> Result<T>.flatMap(f: (T) -> Result<U>): Result<U> =
    when (this) {
        is Success -> f(this.result)
        is Failure -> Failure(this.error)
    }

/**
 * Execute a transformation function [f] on this [Result] object if it is a [Success] or return this object unchanged
 * if it is a [Failure].
 */
private inline fun <T, U> Result<T>.map(f: (T) -> U): Result<U> =
    when (this) {
        is Success -> Success(f(this.result))
        is Failure -> Failure(this.error)
    }

/**
 * Return the value stored in this [Result] if it is a [Success] or *null* if it is a [Failure].
 */
private fun <T> Result<T>.getOrNull(): T? =
    when (this) {
        is Success -> result
        else -> null
    }

/**
 * Return the error message stored in this [Result] if it is a [Failure] or *null* if it is a [Success].
 */
private fun <T> Result<T>.exceptionOrNull(): String? =
    when (this) {
        is Failure -> error
        else -> null
    }
