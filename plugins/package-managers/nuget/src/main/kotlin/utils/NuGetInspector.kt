/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.nuget.utils

import com.fasterxml.jackson.module.kotlin.readValue

import java.io.File
import java.util.SortedSet

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

import org.ossreviewtoolkit.analyzer.PackageManager
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
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.ort.createOrtTempFile

private val json = Json { ignoreUnknownKeys = true }

internal object NuGetInspector : CommandLineTool {
    override fun command(workingDir: File?) = "nuget-inspector"

    private fun inspect(definitionFile: File, nugetConfig: String?): Result {
        val workingDir = definitionFile.parentFile
        val outputFile = createOrtTempFile(prefix = "nuget-inspector", suffix = ".json")

        val commandLineOptions = buildList<String> {
            add("--with-details")
            add("--verbose")
            add("--project-file")
            add(definitionFile.absolutePath)
            add("--json")
            add(outputFile.absolutePath)

            if (nugetConfig != null) {
                add("--nuget-config")
                add(workingDir.resolve(nugetConfig).absolutePath)
            }
        }

        return try {
            run(workingDir, *commandLineOptions.toTypedArray())
            outputFile.inputStream().use { json.decodeFromStream(it) }
        } finally {
            workingDir.resolve(".cache").safeDeleteRecursively(force = true)
            outputFile.delete()
        }
    }

    fun resolveDependencies(
        definitionFile: File,
        managerName: String,
        analysisRoot: File,
        nugetConfig: String?
    ): List<ProjectAnalyzerResult> {
        val result = inspect(definitionFile, nugetConfig)
        val project = result.toOrtProject(managerName, analysisRoot, definitionFile)
        val packages = result.dependencies.toOrtPackages()
        val errorMessage = collectErrorMessage(result)
        val issues = if (errorMessage.isNotBlank()) {
            listOf(
                Issue(
                    message = errorMessage,
                    source = managerName
                )
            )
        } else {
            emptyList()
        }

        return listOf(ProjectAnalyzerResult(project, packages, issues))
    }

    @Serializable
    internal data class Result(
        val packages: List<PackageData>,
        val dependencies: List<PackageData>,
        val headers: List<Header>
    )

    @Serializable
    internal data class Header(
        @SerialName("project_framework") val projectFramework: String,
        val errors: List<String>
    )

    @Serializable
    internal data class PackageData(
        val type: String,
        val namespace: String?,
        val name: String,
        val version: String?,
        val description: String,
        val parties: List<Party>,
        @SerialName("homepage_url") val homepageUrl: String?,
        @SerialName("download_url") val downloadUrl: String,
        val size: Long,
        val sha1: String?,
        val md5: String?,
        val sha256: String?,
        val sha512: String?,
        @SerialName("code_view_url") val codeViewUrl: String?,
        @SerialName("vcs_url") val vcsUrl: String?,
        val copyright: String?,
        @SerialName("license_expression") val licenseExpression: String?,
        @SerialName("declared_license") val declaredLicense: String?,
        @SerialName("source_packages") val sourcePackages: List<String>,
        @SerialName("repository_homepage_url") val repositoryHomepageUrl: String?,
        @SerialName("repository_download_url") val repositoryDownloadUrl: String?,
        @SerialName("api_data_url") val apiDataUrl: String,
        val purl: String,
        val dependencies: List<PackageData>,
        val errors: List<String>,
        val warnings: List<String>
    )

    @Serializable
    internal class Party(
        val type: String,
        val role: String,
        val name: String?,
        val email: String?,
        val url: String?
    )
}

private const val TYPE = "NuGet"

private fun List<NuGetInspector.Party>.toAuthors(): SortedSet<String> =
    filter { it.role == "author" }.mapNotNullTo(sortedSetOf()) { party ->
        buildString {
            party.name?.let { append(it) }
            party.email?.let {
                append(if (party.name != null && it.isNotBlank()) " <$it>" else it)
            }
        }.takeIf { it.isNotBlank() }
    }

private fun collectErrorMessage(result: NuGetInspector.Result): String {
    var errorMessage = ""
    result.headers.first().errors.forEach {
        errorMessage += it + "\n"
    }
    result.packages.first().errors.forEach {
        errorMessage += it + "\n"
    }
    result.dependencies.forEach { dep -> dep.errors.forEach { errorMessage += it + "\n" } }
    return errorMessage
}

internal fun NuGetInspector.Result.toOrtProject(
    managerName: String,
    analysisRoot: File,
    definitionFile: File,
): Project {
    val id = Identifier(
        type = managerName,
        namespace = "",
        name = definitionFile.relativeTo(analysisRoot).invariantSeparatorsPath,
        version = ""
    )

    val nestedPackages = mutableListOf<NuGetInspector.PackageData>()

    packages.forEach { pkg ->
        pkg.dependencies.forEach { dep -> nestedPackages += dep }
    }

    val packageReferences = nestedPackages.toPackageReferences()
    val scopes = sortedSetOf(Scope(headers.first().projectFramework, packageReferences))

    return Project(
        id = id,
        definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
        vcs = VcsInfo.EMPTY,
        authors = emptySet(),
        vcsProcessed = PackageManager.processProjectVcs(definitionFile.parentFile),
        declaredLicenses = emptySet(),
        homepageUrl = "",
        scopeDependencies = scopes
    )
}

private fun List<NuGetInspector.PackageData>.toPackageReferences(): SortedSet<PackageReference> =
    mapTo(sortedSetOf()) { it.toPackageReference() }

private fun NuGetInspector.PackageData.toPackageReference() =
    PackageReference(
        id = Identifier(type = TYPE, namespace = "", name = name, version = version.orEmpty()),
        dependencies = dependencies.toPackageReferences()
    )

internal fun Collection<NuGetInspector.PackageData>.toOrtPackages(): Set<Package> =
    groupBy { "${it.name}:${it.version}" }.mapTo(mutableSetOf()) { (_, packages) ->
        val pkg = packages.first()

        fun NuGetInspector.PackageData.getHash(): Hash = Hash.create(
            (sha512 ?: sha256 ?: sha1 ?: md5 ?: "").lowercase()
        )

        val id = Identifier(
            type = TYPE,
            namespace = pkg.namespace.orEmpty(),
            name = pkg.name,
            version = pkg.version.orEmpty()
        )

        val declaredLicenses = sortedSetOf<String>()
        val pkgDeclaredLicense = pkg.declaredLicense.orEmpty()

        if (pkgDeclaredLicense.isNotBlank()) {
            val licenseData = yamlMapper.readValue<Map<String, String>>(pkgDeclaredLicense)
            val type = licenseData["LicenseType"].orEmpty()

            listOfNotNull(
                licenseData["LicenseExpression"]?.takeIf { type.lowercase() == "expression" },
                licenseData["LicenseUrl"]
            ).firstOrNull()?.also { declaredLicenses += it }
        }

        var vcsUrl = pkg.vcsUrl.orEmpty()
        var commit = ""

        val segments = vcsUrl.split("@", limit = 2)
        if (segments.size == 2) {
            vcsUrl = segments[0]
            commit = segments[1]
        }

        Package(
            id = id,
            purl = pkg.purl,
            authors = pkg.parties.toAuthors(),
            declaredLicenses = declaredLicenses,
            declaredLicensesProcessed = DeclaredLicenseProcessor.process(declaredLicenses),
            description = pkg.description.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
            homepageUrl = pkg.homepageUrl.orEmpty(),
            binaryArtifact = RemoteArtifact(
                url = pkg.downloadUrl,
                hash = pkg.getHash()
            ),
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = VcsInfo.EMPTY.copy(url = vcsUrl, revision = commit),
            vcsProcessed = PackageManager.processPackageVcs(
                VcsInfo(type = VcsType.UNKNOWN, url = vcsUrl, revision = commit),
                fallbackUrls = listOfNotNull(
                    pkg.codeViewUrl,
                    pkg.homepageUrl
                ).toTypedArray()
            )
        )
    }
