/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.ortbom

import kotlin.collections.orEmpty

import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.model.utils.toPurl

internal object OrtBomFileMapper {
    private const val DEFAULT_PROJECT_NAME = "unknown"
    private const val EXCLUDED_SCOPE_NAME = "excluded"
    private const val MAIN_SCOPE_NAME = "main"
    private const val PROJECT_TYPE = "OrtBom"

    internal fun extractAndMapProject(projectDto: OrtBomProjectDto) =
        Project.EMPTY.copy(
            id = Identifier(
                name = projectDto.projectName?.takeUnless { it.isBlank() } ?: DEFAULT_PROJECT_NAME,
                type = PROJECT_TYPE,
                namespace = "",
                version = ""
            ),
            description = projectDto.description.orEmpty(),
            vcs = projectDto.vcs.toVcsInfo(),
            authors = projectDto.authors.orEmpty(),
            homepageUrl = projectDto.homepageUrl.orEmpty(),
            scopeDependencies = setOfNotNull(
                projectDto.dependencies.filterNot { it.scopes?.contains("main") == true }
                    .toScope(MAIN_SCOPE_NAME),
                projectDto.dependencies.filter { it.scopes?.contains("main") == false }
                    .toScope(EXCLUDED_SCOPE_NAME)
            )
        )

    internal fun extractAndMapPackages(project: OrtBomProjectDto): Pair<Set<Package>, List<Issue>> {
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
                source = "OrtBomFile"
            )
            return Pair(null, listOf(issue))
        }
    }

    private fun DependencyDto.getIdentifiers(): Pair<Identifier, String> {
        when {
            id.isNullOrEmpty() && purl.isNullOrEmpty() ->
                throw IllegalArgumentException("There is no id or purl defined for the package.")

            id?.isEmpty() == false && purl?.isEmpty() == false ->
                throw IllegalArgumentException(
                    "There can be only one id or purl for package id:'$id', curl:'$purl'."
                )

            id?.isNotEmpty() == true -> {
                val identifier = Identifier(id)
                return Pair(identifier, identifier.toPurl())
            }

            purl?.isNotEmpty() == true -> {
                return Pair(Identifier.fromPurl(purl), purl)
            }
        }
        error("This state is unhandled")
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

    private fun Collection<DependencyDto>.toScope(name: String): Scope =
        Scope(
            name = name,
            dependencies = mapTo(mutableSetOf()) { dependency ->
                PackageReference(
                    id = dependency.getIdentifiers().first,
                    linkage = PackageLinkage.STATIC
                )
            }
        )
}
