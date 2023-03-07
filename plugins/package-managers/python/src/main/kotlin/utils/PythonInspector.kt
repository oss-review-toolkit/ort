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

package org.ossreviewtoolkit.plugins.packagemanagers.python.utils

import java.io.File
import java.util.SortedSet

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.ort.ProcessedDeclaredLicense
import org.ossreviewtoolkit.utils.ort.createOrtTempFile
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseIdExpression

import org.semver4j.RangesList
import org.semver4j.RangesListFactory

private const val GENERIC_BSD_LICENSE = "BSD License"
private const val SHORT_STRING_MAX_CHARS = 200

private val json = Json { ignoreUnknownKeys = true }

internal object PythonInspector : CommandLineTool, Logging {
    override fun command(workingDir: File?) = "python-inspector"

    override fun transformVersion(output: String) = output.removePrefix("Python-inspector version: ")

    override fun getVersionRequirement(): RangesList = RangesListFactory.create("[0.9.2,)")

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

            add("--verbose")
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
        val license: String? = null,
        val classifiers: List<String> = emptyList()
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
        @SerialName("declared_license") val declaredLicense: DeclaredLicense?,
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

internal fun PythonInspector.Result.toOrtProject(
    managerName: String,
    analysisRoot: File,
    definitionFile: File
): Project {
    val id = resolveIdentifier(managerName, analysisRoot, definitionFile)

    val setupProject = projects.find { it.path.endsWith("/setup.py") }
    val projectData = setupProject?.packageData?.singleOrNull()
    val homepageUrl = projectData?.homepageUrl.orEmpty()

    val scopes = sortedSetOf(Scope("install", resolvedDependenciesGraph.toPackageReferences()))

    return Project(
        id = id,
        definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
        authors = projectData?.parties?.toAuthors() ?: emptySet(),
        declaredLicenses = projectData?.declaredLicense?.getDeclaredLicenses() ?: emptySet(),
        vcs = VcsInfo.EMPTY,
        vcsProcessed = PackageManager.processProjectVcs(definitionFile.parentFile, VcsInfo.EMPTY, homepageUrl),
        homepageUrl = homepageUrl,
        scopeDependencies = scopes
    )
}

private fun PythonInspector.Result.resolveIdentifier(
    managerName: String,
    analysisRoot: File,
    definitionFile: File
): Identifier {
    // First try to get identifier components from "setup.py" in any case, even for "requirements.txt" projects.
    val (setupName, setupVersion) = projects.find { it.path.endsWith("/setup.py") }
        ?.let { project ->
            listOf(
                project.packageData.single().name.orEmpty(),
                project.packageData.single().version.orEmpty()
            )
        } ?: listOf("", "")

    val (requirementsName, requirementsVersion) = projects.find { !it.path.endsWith("/setup.py") }
        ?.let {
            // In case of "requirements*.txt" there is no metadata at all available, so use the parent directory name
            // plus what "*" expands to as the project name and the VCS revision, if any, as the project version.
            val suffix = definitionFile.name.removePrefix("requirements").removeSuffix(".txt")
            val name = if (definitionFile.parentFile != analysisRoot) {
                definitionFile.parentFile.name + suffix
            } else {
                PackageManager.getFallbackProjectName(analysisRoot, definitionFile)
            }

            val version = VersionControlSystem.getCloneInfo(definitionFile.parentFile).revision

            listOf(name, version)
        } ?: listOf("", "")

    val hasSetupName = setupName.isNotEmpty()
    val hasRequirementsName = requirementsName.isNotEmpty()

    val projectName = when {
        hasSetupName && !hasRequirementsName -> setupName
        !hasSetupName && hasRequirementsName -> requirementsName
        hasSetupName && hasRequirementsName -> "$setupName-with-requirements-$requirementsName"
        else -> PackageManager.getFallbackProjectName(analysisRoot, definitionFile)
    }

    val projectVersion = setupVersion.takeIf { it.isNotEmpty() } ?: requirementsVersion

    return Identifier(
        type = managerName,
        namespace = "",
        name = projectName,
        version = projectVersion
    )
}

private fun PythonInspector.DeclaredLicense.getDeclaredLicenses() =
    buildSet {
        getLicenseFromLicenseField(license)?.let { add(it) }
        addAll(classifiers.mapNotNull { getLicenseFromClassifier(it) })
    }

private fun processDeclaredLicenses(id: Identifier, declaredLicenses: Set<String>): ProcessedDeclaredLicense {
    var declaredLicensesProcessed = DeclaredLicenseProcessor.process(declaredLicenses)

    // Python's classifiers only support a coarse license declaration of "BSD License". So if there is another
    // more specific declaration of a BSD license, align on that one.
    if (GENERIC_BSD_LICENSE in declaredLicensesProcessed.unmapped) {
        declaredLicensesProcessed.spdxExpression?.decompose()?.singleOrNull {
            it is SpdxLicenseIdExpression && it.isValid() && it.toString().startsWith("BSD-")
        }?.let { license ->
            PythonInspector.logger.debug { "Mapping '$GENERIC_BSD_LICENSE' to '$license' for '${id.toCoordinates()}'." }

            declaredLicensesProcessed = declaredLicensesProcessed.copy(
                mapped = declaredLicensesProcessed.mapped + mapOf(GENERIC_BSD_LICENSE to license),
                unmapped = declaredLicensesProcessed.unmapped - GENERIC_BSD_LICENSE
            )
        }
    }

    return declaredLicensesProcessed
}

internal fun List<PythonInspector.Package>.toOrtPackages(): Set<Package> =
    groupBy { "${it.name}:${it.version}" }.mapTo(mutableSetOf()) { (_, packages) ->
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

        val id = Identifier(type = TYPE, namespace = "", name = pkg.name, version = pkg.version)
        val declaredLicenses = pkg.declaredLicense?.getDeclaredLicenses() ?: emptySet()
        val declaredLicensesProcessed = processDeclaredLicenses(id, declaredLicenses)

        Package(
            // The package has a namespace property which is currently always empty. Deliberately set the namespace to
            // an empty string here to be consistent with the resolved packages which do not have a namespace property.
            id = id,
            purl = pkg.purl,
            authors = pkg.parties.toAuthors(),
            declaredLicenses = declaredLicenses,
            declaredLicensesProcessed = declaredLicensesProcessed,
            // Only use the first line of the description because the descriptions provided by python-inspector are
            // currently far too long, see: https://github.com/nexB/python-inspector/issues/74
            description = pkg.description.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
            homepageUrl = pkg.homepageUrl.orEmpty(),
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

private fun List<PythonInspector.Party>.toAuthors(): Set<String> =
    filter { it.role == "author" }.mapNotNullTo(mutableSetOf()) { party ->
        buildString {
            party.name?.let { append(it) }
            party.email?.let {
                append(if (party.name != null) " <$it>" else it)
            }
        }.takeIf { it.isNotBlank() }
    }

private fun List<PythonInspector.ResolvedDependency>.toPackageReferences(): SortedSet<PackageReference> =
    mapTo(sortedSetOf()) { it.toPackageReference() }

private fun PythonInspector.ResolvedDependency.toPackageReference() =
    PackageReference(
        id = Identifier(type = TYPE, namespace = "", name = packageName, version = installedVersion),
        dependencies = dependencies.toPackageReferences()
    )

private fun getLicenseFromClassifier(classifier: String): String? {
    // Example license classifier (also see https://pypi.org/classifiers/):
    // "License :: OSI Approved :: GNU Library or Lesser General Public License (LGPL)"
    val classifiers = classifier.split(" :: ").map { it.trim() }
    val licenseClassifiers = listOf("License", "OSI Approved")
    val license = classifiers.takeIf { it.first() in licenseClassifiers }?.last()
    return license?.takeUnless { it in licenseClassifiers }
}

private fun getLicenseFromLicenseField(value: String?): String? {
    if (value.isNullOrBlank() || value == "UNKNOWN") return null

    // See https://docs.python.org/3/distutils/setupscript.html#additional-meta-data for what a "short string" is.
    val isShortString = value.length <= SHORT_STRING_MAX_CHARS && value.lines().size == 1
    if (!isShortString) return null

    // Apply a work-around for projects that declare licenses in classifier-syntax in the license field.
    return getLicenseFromClassifier(value) ?: value
}
