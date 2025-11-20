/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.ortproject

import com.github.packageurl.MalformedPackageURLException
import com.github.packageurl.PackageURL

import java.io.File

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.model.utils.toIdentifier
import org.ossreviewtoolkit.model.utils.toPurl

internal object OrtProjectFileMapper {
    private const val DEFAULT_PROJECT_NAME = "unknown"
    private const val PROJECT_TYPE = "OrtProjectFile"

    internal fun extractAndMapProject(projectDto: OrtProjectFileDto, vcsInfo: VcsInfo, definitionFile: File) =
        Project(
            id = Identifier(
                name = projectDto.projectName?.takeUnless { it.isBlank() } ?: DEFAULT_PROJECT_NAME,
                type = PROJECT_TYPE,
                namespace = "",
                version = ""
            ),
            vcs = vcsInfo,
            description = projectDto.description.orEmpty(),
            authors = projectDto.authors,
            homepageUrl = projectDto.homepageUrl.orEmpty(),
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            declaredLicenses = projectDto.declaredLicenses,
            scopeDependencies = projectDto.dependencies.toScopes()
        )

    internal fun extractAndMapPackages(project: OrtProjectFileDto): Pair<Set<Package>, List<Issue>> {
        val issues = mutableListOf<Issue>()
        val packages = mutableSetOf<Package>()

        project.dependencies.forEach {
            val packageWithIssues = it.toPackage()

            packageWithIssues.first?.let { pkg -> packages.add(pkg) }
            issues.addAll(packageWithIssues.second)
        }

        return Pair(packages, issues)
    }

    private fun DependencyDto.toPackage(): Pair<Package?, List<Issue>> {
        try {
            val identifiers = getIdentifiers()
            val pkg = Package(
                id = identifiers.first,
                purl = identifiers.second,
                sourceArtifact = sourceArtifact?.toRemoteArtifact().orEmpty(),
                vcs = vcs?.toVcsInfo().orEmpty(),
                declaredLicenses = declaredLicenses,
                description = description.orEmpty(),
                homepageUrl = homepageUrl.orEmpty(),
                binaryArtifact = RemoteArtifact.EMPTY,
                authors = authors,
                labels = labels
            )

            return Pair(pkg, emptyList())
        } catch (ex: IllegalArgumentException) {
            val issue = Issue(
                message = ex.message.orEmpty(),
                source = "OrtProjectFile"
            )
            return Pair(null, listOf(issue))
        }
    }

    private fun VcsDto.toVcsInfo(): VcsInfo =
        VcsInfo(
            type = VcsType.forName(type.uppercase()),
            url = url,
            revision = revision,
            path = path
        )

    private fun SourceArtifactDto.toRemoteArtifact(): RemoteArtifact =
        RemoteArtifact(
            url = url,
            hash = Hash(hash.value.lowercase(), hash.algorithm)
        )

    private fun DependencyDto.getIdentifiers(): Pair<Identifier, String> {
        this.validateIdentifiers()

        when {
            id.isNullOrBlank() && purl.isNullOrBlank() ->
                throw IllegalArgumentException("There is no id or purl defined for the package.")

            !id.isNullOrBlank() && !purl.isNullOrBlank() ->
                return Pair(Identifier(id), purl)

            !id.isNullOrBlank() -> {
                val identifier = Identifier(id)
                return Pair(identifier, identifier.toPurl())
            }

            !purl.isNullOrBlank() ->
                return Pair(purl.toIdentifier(), purl)

            else ->
                throw IllegalArgumentException(
                    "There is something wrong in dependency id/purl declaration for '$this'."
                )
        }
    }

    private fun DependencyDto.validateIdentifiers() {
        if (!id.isNullOrBlank()) {
            val identifier = Identifier(id)
            require(!identifier.type.isBlank() && !identifier.name.isBlank() && !identifier.version.isBlank()) {
                "The id '$id' is not a valid Identifier."
            }
        }

        if (!purl.isNullOrBlank()) {
            try {
                PackageURL(purl)
            } catch (e: MalformedPackageURLException) {
                throw IllegalArgumentException("The purl '$purl' is not a valid PackageURL.", e)
            }
        }
    }

    private fun Collection<DependencyDto>.toScopes(): Set<Scope> {
        val scopeMap = mutableMapOf<String, MutableList<Identifier>>()
        this.forEach { dependency ->
            if (dependency.scopes?.isNotEmpty() == true) {
                dependency.scopes.forEach { scopeName ->
                    scopeMap.getOrPut(scopeName) { mutableListOf() }
                        .add(dependency.getIdentifiers().first)
                }
            }
        }

        return scopeMap.mapTo(mutableSetOf()) { (scopeName, identifiers) ->
            Scope(
                name = scopeName,
                dependencies = identifiers.map { id -> PackageReference(id = id) }.toSet()
            )
        }
    }
}
