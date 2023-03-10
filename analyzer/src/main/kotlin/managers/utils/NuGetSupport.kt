/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.xml.XmlFactory
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

import java.io.File
import java.util.LinkedList
import java.util.SortedSet
import java.util.concurrent.TimeUnit

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

import okhttp3.CacheControl

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManager.Companion.processPackageVcs
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
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
import org.ossreviewtoolkit.utils.common.searchUpwardsForFile
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper
import org.ossreviewtoolkit.utils.ort.downloadText

// See https://docs.microsoft.com/en-us/nuget/api/overview.
private const val DEFAULT_SERVICE_INDEX_URL = "https://api.nuget.org/v3/index.json"
private const val REGISTRATIONS_BASE_URL_TYPE = "RegistrationsBaseUrl/3.6.0"
private val VERSION_RANGE_CHARS = charArrayOf('[', ']', '(', ')', ',', '*', '+')

private val JSON_MAPPER = JsonMapper().registerKotlinModule()

// TODO: Add support for lock files, see
//       https://devblogs.microsoft.com/nuget/enable-repeatable-package-restores-using-a-lock-file/.
class NuGetSupport(
    private val managerName: String,
    private val analysisRoot: File,
    private val reader: XmlPackageFileReader
) {
    companion object : Logging {
        const val OPTION_DIRECT_DEPENDENCIES_ONLY = "directDependenciesOnly"

        val XML_MAPPER = XmlMapper(
            XmlFactory().apply {
                // Work-around for https://github.com/FasterXML/jackson-module-kotlin/issues/138.
                xmlTextElementName = "value"
            }
        ).registerKotlinModule()
    }

    /**
     * A class that bundles all metadata for NuGet packages.
     */
    private class AllPackageData(
        val data: PackageData,
        val details: PackageDetails,
        val spec: PackageSpec
    )

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

    private inline fun <reified T> ObjectMapper.readValueFromUrl(url: String): T {
        val text = client.downloadText(url).getOrThrow()
        return readValue(text)
    }

    private fun getPackageData(id: Identifier, registrationBaseUrl: String): PackageData? =
        runCatching {
            // Note: The package name in the URL is case-sensitive and must be lower-case!
            val lowerId = id.name.lowercase()
            val dataUrl = "$registrationBaseUrl/$lowerId/${id.version}.json"
            JSON_MAPPER.readValueFromUrl<PackageData>(dataUrl)
        }.getOrNull()

    private fun getAllPackageData(
        directDependencies: Collection<Identifier>,
        registrationsBaseUrls: Collection<String>,
    ): Pair<Map<Identifier, AllPackageData>, Set<Issue>> {
        val issues = mutableSetOf<Issue>()
        val result = mutableMapOf<Identifier, AllPackageData>()
        val queue = LinkedList(directDependencies)

        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (id in result) continue

            val packageData = registrationsBaseUrls.firstNotNullOfOrNull { getPackageData(id, it) }

            if (packageData == null) {
                val hint = " Support for non-fixed version '${id.version}' is not implemented.".takeIf {
                    !id.version.isFixedVersion()
                }.orEmpty()

                issues += createAndLogIssue(
                    source = "NuGet",
                    message = "Failed to get package data for '${id.toCoordinates()}' from any of " +
                            "$registrationsBaseUrls.$hint"
                )

                continue
            }

            val allPackageData = runBlocking {
                val packageDetails = async { JSON_MAPPER.readValueFromUrl<PackageDetails>(packageData.catalogEntry) }
                val nuspecUrl = packageData.packageContent.replace(".${id.version}.nupkg", ".nuspec")
                val packageSpec = async { XML_MAPPER.readValueFromUrl<PackageSpec>(nuspecUrl) }

                AllPackageData(packageData, packageDetails.await(), packageSpec.await())
            }

            val dependencies = allPackageData.details.dependencyGroups.flatMapTo(mutableSetOf()) { group ->
                group.dependencies.map { it.getId() }
            }

            result[id] = allPackageData
            queue += dependencies.filterNot { it in result }
        }

        return result to issues
    }

    private fun getPackage(all: AllPackageData): Package {
        val vcs = all.spec.metadata.repository?.let {
            VcsInfo(
                type = VcsType.forName(it.type.orEmpty()),
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

    private fun buildDependencyTree(
        references: Collection<Identifier>,
        packageMap: Map<Identifier, Pair<AllPackageData, Package>>,
        dependencies: MutableCollection<PackageReference>,
        packages: MutableCollection<Package>,
        recursive: Boolean
    ) {
        references.forEach { id ->
            val (all, pkg) = packageMap[id] ?: return@forEach

            val pkgRef = pkg.toReference()
            dependencies += pkgRef

            val packageIsNew = packages.add(pkg)
            if (!recursive) return@forEach

            // As NuGet dependencies are very repetitive, truncate the tree at already known branches to avoid it to
            // grow really huge.
            if (packageIsNew) {
                // TODO: Consider mapping dependency groups to scopes.
                val dependencyReferences = all.details.dependencyGroups.flatMapTo(mutableSetOf()) { group ->
                    group.dependencies.map { it.getId() }
                }

                buildDependencyTree(
                    dependencyReferences,
                    packageMap,
                    pkgRef.dependencies,
                    packages,
                    recursive = true
                )
            } else {
                logger.debug {
                    "Truncating dependencies for '${id.toCoordinates()}' which were already determined."
                }
            }
        }
    }

    fun resolveDependencies(definitionFile: File, directDependenciesOnly: Boolean): ProjectAnalyzerResult {
        val workingDir = definitionFile.parentFile

        val packages = mutableSetOf<Package>()

        val references = reader.getDependencies(definitionFile)
        val referencesByFramework = references.groupBy { it.targetFramework }
        val referencesForAllFrameworks = referencesByFramework[""].orEmpty()

        val registrationsBaseUrls = getRegistrationsBaseUrls(definitionFile)
        val (allPackageData, issues) = getAllPackageData(references.map { it.getId() }, registrationsBaseUrls)
        val packageMap = allPackageData.mapValues { it.value to getPackage(it.value) }

        val scopes = referencesByFramework.flatMapTo(sortedSetOf()) { (targetFramework, frameworkDependencies) ->
            frameworkDependencies.groupBy { it.developmentDependency }.map { (isDevDependency, dependencies) ->
                val allDependencies = buildSet {
                    addAll(dependencies)
                    // Add dependencies without a specified target framework to all scopes.
                    addAll(referencesForAllFrameworks.filter { it.developmentDependency == isDevDependency })
                }

                val packageReferences = sortedSetOf<PackageReference>()

                buildDependencyTree(
                    references = allDependencies.map { it.getId() },
                    packageMap = packageMap,
                    dependencies = packageReferences,
                    packages = packages,
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

        return ProjectAnalyzerResult(project, packages, issues.toList())
    }

    private fun getProject(definitionFile: File, workingDir: File, scopes: SortedSet<Scope>): Project {
        val spec = resolveLocalSpec(definitionFile)?.let { XML_MAPPER.readValue<PackageSpec>(it) }

        return Project(
            id = Identifier(
                type = managerName,
                namespace = "",
                name = spec?.metadata?.id ?: PackageManager.getFallbackProjectName(analysisRoot, definitionFile),
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

    private fun getRegistrationsBaseUrls(definitionFile: File): List<String> {
        val configFile = definitionFile.parentFile.searchUpwardsForFile("nuget.config", ignoreCase = true)
        val serviceIndexUrls = configFile?.let { NuGetConfigFileReader.getRegistrationsBaseUrls(it) }
            ?: listOf(DEFAULT_SERVICE_INDEX_URL)
        val serviceIndices = runBlocking {
            serviceIndexUrls.map {
                async { JSON_MAPPER.readValueFromUrl<ServiceIndex>(it) }
            }.awaitAll()
        }

        return serviceIndices
            .flatMap { it.resources }
            .filter { it.type == REGISTRATIONS_BASE_URL_TYPE }
            // Note: Remove a trailing slash as one is always added later to separate from the path, and a double-slash
            // would break the URL!
            .map { it.id.removeSuffix("/") }
    }
}

/**
 * Parse information about the licenses of a package from the given [spec].
 */
private fun parseLicenses(spec: PackageSpec?): Set<String> {
    val data = spec?.metadata ?: return emptySet()

    // Prefer "license" over "licenseUrl" as the latter has been deprecated, see
    // https://docs.microsoft.com/en-us/nuget/reference/nuspec#licenseurl
    val license = data.license?.value?.takeUnless { data.license.type == "file" }
    if (license != null) return setOf(license)

    val licenseUrl = data.licenseUrl?.takeUnless { it == "https://aka.ms/deprecateLicenseUrl" } ?: return emptySet()
    return setOf(licenseUrl)
}

/**
 * Parse information about the authors of a package from the given [spec].
 */
private fun parseAuthors(spec: PackageSpec?): Set<String> =
    spec?.metadata?.authors?.split(',', ';').orEmpty()
        .map(String::trim)
        .filterNot(String::isEmpty)
        .toSet()

/**
 * Try to find a .nuspec file for the given [definitionFile]. The file is looked up in the same directory.
 */
private fun resolveLocalSpec(definitionFile: File): File? =
    definitionFile.parentFile?.resolve(".nuspec")?.takeIf { it.isFile }

private fun getIdentifier(name: String, version: String) =
    Identifier(type = "NuGet", namespace = "", name = name, version = version)

private fun NuGetDependency.getId() = getIdentifier(name, version)

private fun Dependency.getId(): Identifier {
    // Resolve to the lowest applicable version, see
    // https://docs.microsoft.com/en-us/nuget/concepts/dependency-resolution#lowest-applicable-version.
    val version = range.trim { it.isWhitespace() || it in VERSION_RANGE_CHARS }
        .split(',').first().trim()

    // TODO: Add support for resolving floating versions to the highest version, see
    //       https://docs.microsoft.com/en-us/nuget/concepts/dependency-resolution#floating-versions.

    return getIdentifier(id, version)
}

/**
 * A class that bundles properties of a single NuGet dependency.
 */
data class NuGetDependency(
    val name: String,
    val version: String,
    val targetFramework: String,
    val developmentDependency: Boolean = false
)

/**
 * An interface to be implemented by different XML file format readers that declare NuGet dependencies.
 */
interface XmlPackageFileReader {
    /**
     * Return the set of [NuGet dependencies][NuGetDependency] declared in the given [definitionFile].
     */
    fun getDependencies(definitionFile: File): Set<NuGetDependency>
}

private fun String.isFixedVersion() = VERSION_RANGE_CHARS.none { it in this } && isNotBlank()
