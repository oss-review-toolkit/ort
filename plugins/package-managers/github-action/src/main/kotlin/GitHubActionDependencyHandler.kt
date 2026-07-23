/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.githubaction

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.SourceCodeOrigin
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.utils.DependencyHandler
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl

internal class GitHubActionDependencyHandler(private val projectVcs: VcsInfo) : DependencyHandler<ABom.Action> {
    override fun identifierFor(dependency: ABom.Action) = dependency.toIdentifier()

    override fun dependenciesFor(dependency: ABom.Action) = dependency.dependencies

    override fun linkageFor(dependency: ABom.Action) = PackageLinkage.DYNAMIC

    override fun createPackage(dependency: ABom.Action, issues: MutableCollection<Issue>) =
        with(dependency) {
            val normalizedPath = path?.removePrefix("./").orEmpty()

            val vcsInfo = if (type == ABom.ActionType.LOCAL) {
                projectVcs.copy(path = normalizedPath)
            } else {
                val url = "https://github.com/$owner/$repo.git".takeIf { owner != null && repo != null }.orEmpty()

                VcsInfo(
                    type = if (url.isEmpty()) VcsType.UNKNOWN else VcsType.GIT,
                    url = url,
                    revision = ref.orEmpty(),
                    path = normalizedPath
                )
            }

            val pathQualifier = if (vcsInfo.path.isNotEmpty()) "#${vcsInfo.path}" else vcsInfo.path

            Package(
                id = dependency.toIdentifier(),
                purl = "pkg:github/$slug$pathQualifier",
                declaredLicenses = emptySet(),
                description = "",
                homepageUrl = vcsToHomepageUrl(vcsInfo.url),
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact.EMPTY,
                vcs = vcsInfo,
                sourceCodeOrigins = listOf(SourceCodeOrigin.VCS)
            )
        }

    private val ABom.Action.slug: String
        get() = if (type == ABom.ActionType.LOCAL) {
            projectVcs.url.substringAfter("github.com/").removeSuffix(".git")
        } else {
            uses
        }

    private fun ABom.Action.toIdentifier() =
        if (type == ABom.ActionType.LOCAL) {
            Identifier(
                type = PACKAGE_TYPE,
                namespace = slug.substringBefore("/"),
                name = slug.substringAfter("/"),
                version = projectVcs.revision
            )
        } else {
            Identifier(
                type = PACKAGE_TYPE,
                namespace = owner.orEmpty(),
                name = repo.orEmpty(),
                version = ref.orEmpty()
            )
        }
}

internal fun vcsToHomepageUrl(vcsUrl: String) =
    normalizeVcsUrl(vcsUrl).removeSuffix(".git").replace("ssh://git@", "https://")
