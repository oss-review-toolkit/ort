/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
 * Copyright (C) 2020-2022 Bosch.IO GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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
import java.util.SortedSet
import java.util.concurrent.TimeUnit

import javax.xml.bind.annotation.XmlRootElement

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

import okhttp3.CacheControl
import okhttp3.Request

import org.apache.logging.log4j.Level

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManager.Companion.processPackageVcs
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
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.utils.common.collectMessagesAsString
import org.ossreviewtoolkit.utils.common.searchUpwardsForFile
import org.ossreviewtoolkit.utils.core.OkHttpClientHelper
import org.ossreviewtoolkit.utils.core.await
import org.ossreviewtoolkit.utils.core.log
import org.ossreviewtoolkit.utils.core.logOnce

internal const val OPTION_DIRECT_DEPENDENCIES_ONLY = "directDependenciesOnly"

// See https://docs.microsoft.com/en-us/nuget/api/overview.
private const val DEFAULT_SERVICE_INDEX_URL = "https://api.nuget.org/v3/index.json"
private const val REGISTRATIONS_BASE_URL_TYPE = "RegistrationsBaseUrl/3.6.0"

private val VERSION_RANGE_CHARS = charArrayOf('[', ']', '(', ')', ',')

class NuGetSupport(serviceIndexUrls: List<String> = listOf(DEFAULT_SERVICE_INDEX_URL)) {
    companion object {
        val JSON_MAPPER = JsonMapper().registerKotlinModule()

        val XML_MAPPER = XmlMapper(
            XmlFactory().apply {
                // Work-around for https://github.com/FasterXML/jackson-module-kotlin/issues/138.
                xmlTextElementName = "value"
            }
        ).registerKotlinModule()

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
        }.awaitAll()
    }

    // Note: Remove a trailing slash as one is always added later to separate from the path, and a double-slash would
    // break the URL!
    private val registrationsBaseUrls = serviceIndices
        .flatMap { it.resources }
        .filter { it.type == REGISTRATIONS_BASE_URL_TYPE }
        .map { it.id.removeSuffix("/") }

    private val packageMap = mutableMapOf<Identifier, Pair<NuGetAllPackageData, Package>>()

    private suspend inline fun <reified T> mapFromUrl(mapper: ObjectMapper, url: String): T {
        val request = Request.Builder()
            .get()
            .url(url)
            .build()

        val response = client.newCall(request).await()
        if (response.cacheResponse != null) {
            log.debug { "Retrieved '$url' response from local cache." }
        } else {
            log.debug { "Retrieved '$url' response from remote server." }
        }

        val body = response.body?.string()?.takeIf { response.isSuccessful }
            ?: throw IOException("Failed to get a response body from '$url'.")

        return mapper.readValue(body)
    }

    private fun getAllPackageData(id: Identifier): NuGetAllPackageData {
        // Note: The package name in the URL is case-sensitive and must be lower-case!
        val lowerId = id.name.lowercase()

        val data = registrationsBaseUrls.asSequence().firstNotNullOfOrNull { baseUrl ->
            runCatching {
                val dataUrl = "$baseUrl/$lowerId/${id.version}.json"
                runBlocking { mapFromUrl<PackageData>(JSON_MAPPER, dataUrl) }
            }.getOrNull()
        } ?: throw IOException("Failed to retrieve package data for '$lowerId' from any of $registrationsBaseUrls.")

        val nupkgUrl = data.packageContent
        val nuspecUrl = nupkgUrl.replace(".${id.version}.nupkg", ".nuspec")

        return runBlocking {
            val packageDetails = mapFromUrl<PackageDetails>(JSON_MAPPER, data.catalogEntry)
            val packageSpec = mapFromUrl<PackageSpec>(XML_MAPPER, nuspecUrl)
            NuGetAllPackageData(data, packageDetails, packageSpec)
        }
    }

    private fun getPackage(all: NuGetAllPackageData): Package {
        val vcs = all.spec.metadata.repository?.let {
            VcsInfo(
                type = VcsType(it.type.orEmpty()),
                url = it.url.orEmpty(),
                revision = (it.commit ?: it.branch).orEmpty()
            )
        }.orEmpty()

        return with(all.details) {
            val homepageUrl = projectUrl.orEmpty()

            Package(
                id = getIdentifier(id, version),
                authors = parseAuthors(all.spec),
                declaredLicenses = parseLicenses(all.spec),
                description = description.orEmpty(),
                homepageUrl = homepageUrl,
                binaryArtifact = RemoteArtifact(
                    url = all.data.packageContent,
                    hash = Hash.create("$packageHashAlgorithm-$packageHash")
                ),
                sourceArtifact = RemoteArtifact.EMPTY,
                vcs = vcs,
                vcsProcessed = processPackageVcs(vcs, homepageUrl)
            )
        }
    }

    fun buildDependencyTree(
        references: Collection<Identifier>,
        dependencies: MutableCollection<PackageReference>,
        packages: MutableCollection<Package>,
        issues: MutableCollection<OrtIssue>,
        recursive: Boolean
    ) {
        references.forEach { id ->
            try {
                val (all, pkg) = packageMap.getOrPut(id) {
                    val all = getAllPackageData(id)
                    all to getPackage(all)
                }

                val pkgRef = pkg.toReference()
                dependencies += pkgRef

                val packageIsNew = packages.add(pkg)
                if (!recursive) return@forEach

                // As NuGet dependencies are very repetitive, truncate the tree at already known branches to avoid it to
                // grow really huge.
                if (packageIsNew) {
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
                                .split(',').first().trim()

                            // TODO: Add support resolving to the highest version for floating versions, see
                            //       https://docs.microsoft.com/en-us/nuget/concepts/dependency-resolution#floating-versions.

                            getIdentifier(dependency.id, version)
                        },
                        pkgRef.dependencies,
                        packages,
                        issues,
                        recursive = true
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

/**
 * Parse information about the licenses of a package from the given [spec].
 */
private fun parseLicenses(spec: PackageSpec?): SortedSet<String> {
    val data = spec?.metadata ?: return sortedSetOf()

    // Prefer "license" over "licenseUrl" as the latter has been deprecated, see
    // https://docs.microsoft.com/en-us/nuget/reference/nuspec#licenseurl
    val license = data.license?.value?.takeUnless { data.license.type == "file" }
    if (license != null) return sortedSetOf(license)

    val licenseUrl = data.licenseUrl?.takeUnless { it == "https://aka.ms/deprecateLicenseUrl" } ?: return sortedSetOf()
    return sortedSetOf(licenseUrl)
}

/**
 * Parse information about the authors of a package from the given [spec].
 */
private fun parseAuthors(spec: PackageSpec?): SortedSet<String> =
    spec?.metadata?.authors?.split(',', ';').orEmpty()
        .map(String::trim)
        .filterNot(String::isEmpty)
        .toSortedSet()

/**
 * Try to find a .nuspec file for the given [definitionFile]. The file is looked up in the same directory.
 */
private fun resolveLocalSpec(definitionFile: File): File? =
    definitionFile.parentFile?.resolve(".nuspec")?.takeIf { it.isFile }

private fun getIdentifier(name: String, version: String) =
    Identifier(type = "NuGet", namespace = "", name = name, version = version)

data class NuGetDependency(
    val name: String,
    val version: String,
    val targetFramework: String,
    val developmentDependency: Boolean = false
)

interface XmlPackageFileReader {
    fun getDependencies(definitionFile: File): Set<NuGetDependency>
}

fun PackageManager.resolveNuGetDependencies(
    definitionFile: File,
    reader: XmlPackageFileReader,
    support: NuGetSupport,
    directDependenciesOnly: Boolean
): ProjectAnalyzerResult {
    val workingDir = definitionFile.parentFile

    val packages = sortedSetOf<Package>()
    val issues = mutableListOf<OrtIssue>()

    val references = reader.getDependencies(definitionFile)
    val referencesByFramework = references.groupBy { it.targetFramework }
    val referencesForAllFrameworks = referencesByFramework[""].orEmpty()

    val scopes = referencesByFramework.flatMapTo(sortedSetOf()) { (targetFramework, frameworkDependencies) ->
        frameworkDependencies.groupBy { it.developmentDependency }.map { (isDevDependency, dependencies) ->
            val allDependencies = buildSet {
                addAll(dependencies)
                // Add dependencies without a specified target framework to all scopes.
                addAll(referencesForAllFrameworks.filter { it.developmentDependency == isDevDependency })
            }

            val packageReferences = sortedSetOf<PackageReference>()

            support.buildDependencyTree(
                references = allDependencies.map { Identifier("NuGet::${it.name}:${it.version}") },
                dependencies = packageReferences,
                packages = packages,
                issues = issues,
                recursive = !directDependenciesOnly
            )

            val scopeName = buildString {
                if (targetFramework.isEmpty()) append("allTargetFrameworks") else append(targetFramework)
                if (isDevDependency) append("-dev")
            }

            Scope(scopeName, packageReferences)
        }
    }

    val project = getProject(definitionFile, workingDir, scopes)

    return ProjectAnalyzerResult(project, packages, issues)
}

private fun PackageManager.getProject(
    definitionFile: File,
    workingDir: File,
    scopes: SortedSet<Scope>
): Project {
    val spec = resolveLocalSpec(definitionFile)?.let { NuGetSupport.XML_MAPPER.readValue<PackageSpec>(it) }

    return Project(
        id = Identifier(
            type = managerName,
            namespace = "",
            name = spec?.metadata?.id ?: definitionFile.relativeTo(analysisRoot).invariantSeparatorsPath,
            version = spec?.metadata?.version.orEmpty()
        ),
        definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
        authors = parseAuthors(spec),
        declaredLicenses = parseLicenses(spec),
        vcs = VcsInfo.EMPTY,
        vcsProcessed = PackageManager.processProjectVcs(workingDir),
        homepageUrl = "",
        scopeDependencies = scopes
    )
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
