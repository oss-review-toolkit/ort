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
import org.ossreviewtoolkit.model.utils.toPurl

private const val TYPE = "PyPI"

internal fun PythonInspector.Result.toOrtProject(
    projectType: String,
    analysisRoot: File,
    definitionFile: File
): Project {
    val id = resolveIdentifier(projectType, analysisRoot, definitionFile)

    val setupProject = projects.find { it.path.endsWith("/setup.py") }
    val projectData = setupProject?.packageData?.singleOrNull()
    val homepageUrl = projectData?.homepageUrl.orEmpty()

    val scopes = setOf(Scope("install", resolvedDependenciesGraph.toPackageReferences()))

    return Project(
        id = id,
        definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
        authors = projectData?.parties?.toAuthors().orEmpty(),
        declaredLicenses = projectData?.declaredLicense?.getDeclaredLicenses().orEmpty(),
        vcs = VcsInfo.EMPTY,
        vcsProcessed = PackageManager.processProjectVcs(definitionFile.parentFile, VcsInfo.EMPTY, homepageUrl),
        homepageUrl = homepageUrl,
        scopeDependencies = scopes
    )
}

private fun PythonInspector.Result.resolveIdentifier(
    projectType: String,
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

    val projectVersion = setupVersion.ifEmpty { requirementsVersion }

    return Identifier(
        type = projectType,
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

internal fun List<PythonInspector.Package>.toOrtPackages(): Set<Package> =
    groupBy { "${it.name}:${it.version}" }.mapTo(mutableSetOf()) { (_, packages) ->
        // The python inspector currently often contains two entries for a package where the only difference is the
        // download URL. In this case, one package contains the URL of the binary artifact, the other for the source
        // artifact. So take all metadata from the first package except for the artifacts.
        val pkg = packages.first()

        @Suppress("UseOrEmpty")
        fun PythonInspector.Package.getHash(): Hash = Hash.create(sha512 ?: sha256 ?: sha1 ?: md5 ?: "")

        fun getArtifact(vararg fileExtensions: String) =
            packages.find { pkg -> fileExtensions.any { pkg.downloadUrl.endsWith(it) } }?.let {
                RemoteArtifact(
                    url = it.downloadUrl,
                    hash = it.getHash()
                )
            } ?: RemoteArtifact.EMPTY

        val id = Identifier(type = TYPE, namespace = "", name = pkg.name, version = pkg.version)
        val declaredLicenses = pkg.declaredLicense?.getDeclaredLicenses().orEmpty()
        val declaredLicensesProcessed = processDeclaredLicenses(id, declaredLicenses)

        Package(
            // The package has a namespace property which is currently always empty. Deliberately set the namespace to
            // an empty string here to be consistent with the resolved packages which do not have a namespace property.
            id = id,
            purl = pkg.purl ?: id.toPurl(),
            authors = pkg.parties.toAuthors(),
            declaredLicenses = declaredLicenses,
            declaredLicensesProcessed = declaredLicensesProcessed,
            // Only use the first line of the description because the descriptions provided by python-inspector are
            // currently far too long, see: https://github.com/aboutcode-org/python-inspector/issues/74
            description = pkg.description.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
            homepageUrl = pkg.homepageUrl.orEmpty(),
            binaryArtifact = getArtifact(".whl"),
            sourceArtifact = getArtifact(".tar.gz", ".zip"),
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

internal fun List<PythonInspector.ResolvedDependency>.toPackageReferences(): Set<PackageReference> =
    mapTo(mutableSetOf()) { it.toPackageReference() }

private fun PythonInspector.ResolvedDependency.toPackageReference() =
    PackageReference(
        id = Identifier(type = TYPE, namespace = "", name = packageName, version = installedVersion),
        dependencies = dependencies.toPackageReferences()
    )
