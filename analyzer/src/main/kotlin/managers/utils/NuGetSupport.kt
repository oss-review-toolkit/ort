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
import java.util.SortedMap
import java.util.concurrent.TimeUnit

import javax.xml.bind.annotation.XmlRootElement

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

import okhttp3.CacheControl
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.managers.utils.NuGetAllPackageData.PackageData
import org.ossreviewtoolkit.analyzer.managers.utils.NuGetAllPackageData.PackageDetails
import org.ossreviewtoolkit.analyzer.managers.utils.NuGetAllPackageData.PackageSpec
import org.ossreviewtoolkit.analyzer.managers.utils.NuGetAllPackageData.ServiceIndex
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
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

    private val client = OkHttpClientHelper.buildClient()

    private val serviceIndices = runBlocking(Dispatchers.IO) {
        serviceIndexUrls.map { indexUrl ->
            async { download(indexUrl) }
        }.awaitAll().map { body ->
            body?.let { JSON_MAPPER.readValue<ServiceIndex>(it.string()) }
                ?: throw IOException("Unable to get service indices from $serviceIndexUrls.")
        }
    }

    // Note: Remove a trailing slash as one is always added later to separate from the path, and a double-slash would
    // break the URL!
    private val registrationsBaseUrls = serviceIndices
        .flatMap { it.resources }
        .filter { it.type == REGISTRATIONS_BASE_URL_TYPE }
        .map { it.id.removeSuffix("/") }

    private val packageMap = mutableMapOf<Identifier, Pair<NuGetAllPackageData, Package>>()

    private suspend fun download(url: String): ResponseBody? {
        val request = Request.Builder()
            .get()
            .url(url)
            .build()

        val response = client.newCall(request).await()

        log.debug {
            if (response.cacheResponse != null) {
                "Retrieved '$url' response from local cache."
            } else {
                "Retrieved '$url' response from remote server."
            }
        }

        return response.body?.takeIf { response.isSuccessful }
    }

    private fun getAllPackageData(id: Identifier): NuGetAllPackageData {
        // Note: The package name in the URL is case-sensitive and must be lower-case!
        val lowerId = id.name.toLowerCase()

        val data = runBlocking(Dispatchers.IO) {
            registrationsBaseUrls.map { baseUrl ->
                async { download("$baseUrl/$lowerId/${id.version}.json") }
            }.awaitAll().mapNotNull { body ->
                body?.let { JSON_MAPPER.readValue<PackageData>(it.string()) }
            }.firstOrNull()
        } ?: throw IOException("Failed to retrieve package data for '$lowerId' from any of $registrationsBaseUrls.")

        val nupkgUrl = data.packageContent
        val nuspecUrl = nupkgUrl.replace(".${id.version}.nupkg", ".nuspec")

        return runBlocking(Dispatchers.IO) {
            val deferredCatalogEntryBody = async { download(data.catalogEntry) }
            val deferredNuspecBody = async { download(nuspecUrl) }

            val catalogEntry = deferredCatalogEntryBody.await()?.string()
                ?: throw IOException("Unable to get catalog entry from ${data.catalogEntry}.")
            val nuspec = deferredNuspecBody.await()?.string()
                ?: throw IOException("Unable to get nuspec from $nuspecUrl")

            val packageDetails = JSON_MAPPER.readValue<PackageDetails>(catalogEntry)
            val packageSpec = XML_MAPPER.readValue<PackageSpec>(nuspec)

            NuGetAllPackageData(data, packageDetails, packageSpec)
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

    fun buildDependencyTree(
        references: Collection<Identifier>,
        dependencies: MutableCollection<PackageReference>,
        packages: MutableCollection<Package>,
        issues: MutableCollection<OrtIssue>
    ) {
        runBlocking(Dispatchers.IO) {
            references.map { id ->
                async {
                    packageMap.computeIfAbsent(id) {
                        val all = getAllPackageData(id)
                        all to getPackage(all)
                    }
                }
            }.awaitAll()
        }

        references.forEach { id ->
            try {
                val (all, pkg) = packageMap.getValue(id)

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
                        "Truncating dependencies for '${id.toCoordinates()}' which were already determined."
                    }
                }
            } catch (e: IOException) {
                issues += createAndLogIssue(
                    source = "NuGet",
                    message = "Failed to get package data for '${id.toCoordinates()}': ${e.collectMessagesAsString()}"
                )
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
    support.buildDependencyTree(references, dependencies, packages, issues)

    val project = Project(
        id = Identifier(
            type = managerName,
            namespace = "",
            name = definitionFile.relativeTo(analysisRoot).invariantSeparatorsPath,
            version = ""
        ),
        definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
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
