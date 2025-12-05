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

import kotlin.collections.orEmpty

import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.model.utils.toPurl

internal object OrtProjectFileMapper {
    private const val DEFAULT_PROJECT_NAME = "Unknown"
    private const val PROJECT_TYPE = "OrtProjectFile"

    internal fun extractAndMapProject(projectDto: OrtProjectFileDto, vcsInfo: VcsInfo) =
        Project.EMPTY.copy(
            id = Identifier(
                name = projectDto.projectName?.takeUnless { it.isBlank() } ?: DEFAULT_PROJECT_NAME,
                type = PROJECT_TYPE,
                namespace = "",
                version = ""
            ),
            vcs = vcsInfo,
            description = projectDto.description.orEmpty(),
            authors = projectDto.authors.orEmpty(),
            homepageUrl = projectDto.homepageUrl.orEmpty(),
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
                sourceArtifact = sourceArtifact?.let { artifact ->
                    RemoteArtifact(
                        url = artifact.url.orEmpty(),
                        hash = if (artifact.hash != null) Hash.create(artifact.hash) else Hash.NONE
                    )
                }.orEmpty(),
                vcs = vcs.toVcsInfo(),
                declaredLicenses = declaredLicenses,
                description = description.orEmpty(),
                homepageUrl = homepageUrl.orEmpty(),
                binaryArtifact = RemoteArtifact.EMPTY,
                authors = authors.orEmpty(),
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

    private fun DependencyDto.getIdentifiers(): Pair<Identifier, String> {
        when {
            id.isNullOrEmpty() && purl.isNullOrEmpty() ->
                throw IllegalArgumentException("There is no id or purl defined for the package.")

            id?.isEmpty() == false && purl?.isEmpty() == false ->
                return Pair(Identifier(id), purl)

            id?.isNotEmpty() == true -> {
                val identifier = Identifier(id)
                return Pair(identifier, identifier.toPurl())
            }

            purl?.isNotEmpty() == true ->
                return Pair(Identifier.fromPurl(purl), purl)

            else ->
                error("There is something wrong in dependency definition identifiers")
        }
    }

    private fun VcsDto?.toVcsInfo(): VcsInfo =
        if (this == null) {
            VcsInfo.EMPTY
        } else {
            VcsInfo(
                type = type?.let { VcsType.forName(it) } ?: VcsType.UNKNOWN,
                url = url.orEmpty(),
                revision = revision.orEmpty(),
                path = path.orEmpty()
            )
        }

    private fun Collection<DependencyDto>.toScopes(): Set<Scope> {
        val scopes = mutableSetOf<Scope>()

//        this.forEach { dependency ->
//            if (dependency.scopes?.isNotEmpty() == true) {
//                dependency.scopes.forEach { scopeName ->
//                    scopes.find { scope -> scope.name == scopeName }.let {
//                        scps -> scps.dependencies =
//                    }
//                }
//            }
//        }

        return scopes

//        Scope(
//            name = name,
//            dependencies = mapTo(mutableSetOf()) { dependency ->
//                PackageReference(
//                    id = dependency.getIdentifiers().first,
//                    linkage = PackageLinkage.STATIC
//                )
//            }
//        )
//    }
    }
}
