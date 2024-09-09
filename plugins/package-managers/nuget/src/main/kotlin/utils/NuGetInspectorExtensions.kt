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

import java.io.File

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.fromYaml
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor

private const val TYPE = "NuGet"

private fun List<NuGetInspector.Party>.toAuthors(): Set<String> =
    filter { it.role == "author" }.mapNotNullTo(mutableSetOf()) { party ->
        buildString {
            party.name?.let { append(it) }
            party.email?.let {
                append(if (party.name != null && it.isNotBlank()) " <$it>" else it)
            }
        }.takeIf { it.isNotBlank() }
    }

internal fun NuGetInspector.Result.toOrtProject(
    managerName: String,
    analysisRoot: File,
    definitionFile: File
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
    val scopes = setOf(Scope(headers.first().projectFramework, packageReferences))

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

private fun NuGetInspector.PackageData.getIdentifierWithNamespace(): Identifier =
    if (namespace.isNullOrEmpty()) {
        getIdentifierWithNamespace(TYPE, name, version.orEmpty())
    } else {
        Identifier(TYPE, namespace, name, version.orEmpty())
    }

private fun List<NuGetInspector.PackageData>.toPackageReferences(): Set<PackageReference> =
    mapTo(mutableSetOf()) { data ->
        val errors = data.errors.map {
            Issue(source = TYPE, message = it.lineSequence().first(), severity = Severity.ERROR)
        }

        val warnings = data.warnings.map {
            Issue(source = TYPE, message = it.lineSequence().first(), severity = Severity.WARNING)
        }

        PackageReference(
            id = data.getIdentifierWithNamespace(),
            dependencies = data.dependencies.toPackageReferences(),
            issues = errors + warnings
        )
    }

internal fun Collection<NuGetInspector.PackageData>.toOrtPackages(): Set<Package> =
    groupBy { "${it.name}:${it.version}" }.mapTo(mutableSetOf()) { (_, packages) ->
        val pkg = packages.first()

        fun NuGetInspector.PackageData.getHash(): Hash =
            Hash.create(
                @Suppress("UseOrEmpty")
                (sha512 ?: sha256 ?: sha1 ?: md5 ?: "").lowercase()
            )

        val id = pkg.getIdentifierWithNamespace()

        val declaredLicenses = mutableSetOf<String>()
        val pkgDeclaredLicense = pkg.declaredLicense.orEmpty()

        if (pkgDeclaredLicense.isNotBlank()) {
            val licenseData = pkgDeclaredLicense.fromYaml<Map<String, String>>()
            val type = licenseData["LicenseType"].orEmpty()

            listOfNotNull(
                licenseData["LicenseExpression"]?.takeIf { type.equals("expression", ignoreCase = true) },
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
            purl = pkg.purl.ifEmpty { id.toPurl() },
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
