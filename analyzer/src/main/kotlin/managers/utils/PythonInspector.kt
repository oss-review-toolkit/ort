/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import com.vdurmont.semver4j.Requirement

import java.io.File
import java.util.SortedSet

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.ort.createOrtTempFile

private val json = Json { ignoreUnknownKeys = true }

internal object PythonInspector : CommandLineTool {
    override fun command(workingDir: File?) = "python-inspector"

    override fun transformVersion(output: String) = output.removePrefix("Python-inspector version: ")

    override fun getVersionRequirement(): Requirement = Requirement.buildIvy("[0.8.1,)")

    fun run(
        workingDir: File,
        definitionFile: File,
        pythonVersion: String = "38",
        operatingSystem: String = "linux"
    ): Result {
        val outputFile = createOrtTempFile(prefix = "python-inspector", suffix = ".json")

        val commandLineOptions = buildList {
            add("--python-version")
            add(pythonVersion)

            add("--operating-system")
            add(operatingSystem)

            add("--json-pdt")
            add(outputFile.absolutePath)

            add("--analyze-setup-py-insecurely")

            if (definitionFile.name == "setup.py") {
                add("--setup-py")
            } else {
                add("--requirement")
            }

            add(definitionFile.absolutePath)

            if (definitionFile.name != "setup.py") {
                // If a setup.py file exists, add it to the analysis to capture additional project metadata.
                val setupFile = definitionFile.resolveSibling("setup.py")
                if (setupFile.isFile) {
                    add("--setup-py")
                    add(setupFile.absolutePath)
                }
            }
        }

        return try {
            run(workingDir, *commandLineOptions.toTypedArray())

            outputFile.inputStream().use { json.decodeFromStream(it) }
        } finally {
            outputFile.delete()
        }
    }

    @Serializable
    internal data class Result(
        @SerialName("files") val projects: List<Project>,
        @SerialName("resolved_dependencies_graph") val resolvedDependenciesGraph: List<ResolvedDependency>,
        val packages: List<Package>
    )

    @Serializable
    internal data class Project(
        val path: String,
        @SerialName("package_data") val packageData: List<PackageData>
    )

    @Serializable
    internal data class PackageData(
        val namespace: String?,
        val name: String?,
        val version: String?,
        val description: String?,
        val parties: List<Party>,
        @SerialName("homepage_url") val homepageUrl: String?,
        @SerialName("declared_license") val declaredLicense: DeclaredLicense?
    )

    @Serializable
    internal data class DeclaredLicense(
        val license: String,
        val classifiers: List<String>
    )

    @Serializable
    internal data class ResolvedDependency(
        val key: String,
        @SerialName("package_name") val packageName: String,
        @SerialName("installed_version") val installedVersion: String,
        val dependencies: List<ResolvedDependency>
    )

    @Serializable
    internal data class Package(
        val type: String,
        val namespace: String?,
        val name: String,
        val version: String,
        val description: String,
        val parties: List<Party>,
        @SerialName("homepage_url") val homepageUrl: String,
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
        @SerialName("declared_license") val declaredLicense: String,
        @SerialName("source_packages") val sourcePackages: List<String>,
        @SerialName("repository_homepage_url") val repositoryHomepageUrl: String?,
        @SerialName("repository_download_url") val repositoryDownloadUrl: String?,
        @SerialName("api_data_url") val apiDataUrl: String,
        val purl: String
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

private const val TYPE = "PyPI"

internal fun List<PythonInspector.Package>.toOrtPackages(): SortedSet<Package> =
    groupBy { "${it.name}:${it.version}" }.mapTo(sortedSetOf()) { (_, packages) ->
        // The python inspector currently often contains two entries for a package where the only difference is the
        // download URL. In this case, one package contains the URL of the binary artifact, the other for the source
        // artifact. So take all metadata from the first package except for the artifacts.
        val pkg = packages.first()

        fun PythonInspector.Package.getHash(): Hash = Hash.create(sha512 ?: sha256 ?: sha1 ?: md5 ?: "")

        fun getArtifact(fileExtension: String) =
            packages.find { it.downloadUrl.endsWith(fileExtension) }?.let {
                RemoteArtifact(
                    url = it.downloadUrl,
                    hash = it.getHash()
                )
            } ?: RemoteArtifact.EMPTY

        fun PythonInspector.Package.getDeclaredLicenses() =
            listOfNotNull(declaredLicense.takeIf { it.isNotBlank() && it != "UNKNOWN" }).toSortedSet()

        Package(
            // The package has a namespace property which is currently always empty. Deliberately set the namespace to
            // an empty string here to be consistent with the resolved packages which do not have a namespace property.
            id = Identifier(type = TYPE, namespace = "", name = pkg.name, version = pkg.version),
            purl = pkg.purl,
            authors = pkg.parties.toAuthors(),
            declaredLicenses = pkg.getDeclaredLicenses(),
            // Only use the first line of the description because the descriptions provided by python-inspector are
            // currently far too long, see: https://github.com/nexB/python-inspector/issues/74
            description = pkg.description.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
            homepageUrl = pkg.homepageUrl,
            binaryArtifact = getArtifact(".whl"),
            sourceArtifact = getArtifact(".tar.gz"),
            vcs = VcsInfo.EMPTY.copy(url = pkg.vcsUrl.orEmpty()),
            vcsProcessed = PackageManager.processPackageVcs(
                VcsInfo(VcsType.UNKNOWN, pkg.vcsUrl.orEmpty(), revision = ""),
                fallbackUrls = listOfNotNull(
                    pkg.codeViewUrl,
                    pkg.homepageUrl
                ).toTypedArray()
            )
        )
    }

private fun List<PythonInspector.Party>.toAuthors(): SortedSet<String> =
    filter { it.role == "author" }.mapNotNullTo(sortedSetOf()) { party ->
        buildString {
            party.name?.let { append(it) }
            party.email?.let {
                append(if (party.name != null) " <$it>" else it)
            }
        }.takeIf { it.isNotBlank() }
    }

internal fun List<PythonInspector.ResolvedDependency>.toPackageReferences(): SortedSet<PackageReference> =
    mapTo(sortedSetOf()) { it.toPackageReference() }

private fun PythonInspector.ResolvedDependency.toPackageReference() =
    PackageReference(
        id = Identifier(type = TYPE, namespace = "", name = packageName, version = installedVersion),
        dependencies = dependencies.toPackageReferences()
    )
