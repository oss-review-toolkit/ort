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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.xml.XmlFactory
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

import kotlinx.coroutines.runBlocking

import okhttp3.CacheControl
import okhttp3.Request

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

// See https://docs.microsoft.com/en-us/nuget/api/overview.
private const val SERVICE_INDEX_URL = "https://api.nuget.org/v3/index.json"

private const val REGISTRATIONS_BASE_URL_TYPE = "RegistrationsBaseUrl/3.6.0"

class NuGetSupport {
    companion object {
        val JSON_MAPPER = JsonMapper().registerKotlinModule()

        val XML_MAPPER = XmlMapper(XmlFactory().apply {
            // Work-around for https://github.com/FasterXML/jackson-module-kotlin/issues/138.
            xmlTextElementName = "value"
        }).registerKotlinModule()
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

    private val serviceIndex = runBlocking { mapFromUrl<ServiceIndex>(JSON_MAPPER, SERVICE_INDEX_URL) }

    // Note: Remove a trailing slash as one is always added later to separate from the path, and a double-slash would
    // break the URL!
    private val registrationsBaseUrl = serviceIndex.resources.single {
        it.type == REGISTRATIONS_BASE_URL_TYPE
    }.id.removeSuffix("/")

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
        val lowerId = id.name.toLowerCase()
        val dataUrl = "$registrationsBaseUrl/$lowerId/${id.version}.json"
        val data = runBlocking { mapFromUrl<PackageData>(JSON_MAPPER, dataUrl) }

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
        references.forEach { id ->
            try {
                val (all, pkg) = packageMap.getOrPut(id) {
                    val all = getAllPackageData(id)
                    all to getPackage(all)
                }

                val pkgRef = pkg.toReference()
                dependencies += pkgRef

                // As NuGet dependencies are very repetitive, truncate the tree at already known branches to avoid it to
                // grow really huge.
                if (packages.add(pkg)) {
                    // TODO: Consider mapping dependency groups to scopes.
                    val referredDependencies =
                        all.details.dependencyGroups.flatMapTo(mutableSetOf()) { it.dependencies }

                    buildDependencyTree(
                        referredDependencies.map {
                            // Simply take the minimum of the Ivy-style version range.
                            val version = it.range.removePrefix("[").substringBefore(",").trim()

                            getIdentifier(it.id, version)
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
        scopes = sortedSetOf(scope)
    )

    return ProjectAnalyzerResult(project, packages, issues)
}
