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

import java.io.File

import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.SourceCodeOrigin
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.utils.DependencyHandler
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.utils.common.ensurePrefix
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl

internal class GitHubActionDependencyHandler(private val projectVcs: VcsInfo) : DependencyHandler<ABom.Action> {
    override fun identifierFor(dependency: ABom.Action) = dependency.toIdentifier()

    override fun dependenciesFor(dependency: ABom.Action) = dependency.dependencies

    override fun linkageFor(dependency: ABom.Action) = PackageLinkage.DYNAMIC

    override fun createPackage(dependency: ABom.Action, issues: MutableCollection<Issue>) =
        with(dependency) {
            val normalizedPath = path?.let { normalizeActionPath(it) }.orEmpty()

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

            val purl = when {
                // Without a repository on GitHub, there is no "github" purl for local actions.
                type == ABom.ActionType.LOCAL -> projectRepository?.let { (owner, repo) ->
                    createGitHubPurl(owner, repo, projectVcs.revision, vcsInfo.path)
                }.orEmpty()

                owner != null && repo != null -> createGitHubPurl(owner, repo, ref.orEmpty(), vcsInfo.path)

                // Actions without an owner and a repository, like Docker actions, are not hosted on GitHub.
                else -> "pkg:github/$uses"
            }

            Package(
                id = dependency.toIdentifier(),
                purl = purl,
                declaredLicenses = emptySet(),
                description = "",
                homepageUrl = vcsToHomepageUrl(vcsInfo.url),
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact.EMPTY,
                vcs = vcsInfo,
                sourceCodeOrigins = listOf(SourceCodeOrigin.VCS)
            )
        }

    /**
     * The owner and the name of the project's repository, which contains local actions, or null if the project is not
     * hosted on GitHub.
     */
    private val projectRepository: Pair<String, String>? by lazy {
        val url = normalizeVcsUrl(projectVcs.url)
        val owner = VcsHost.GITHUB.getUserOrOrganization(url)
        val repo = VcsHost.GITHUB.getProject(url)

        if (owner != null && repo != null) owner to repo else null
    }

    private fun ABom.Action.toIdentifier(): Identifier {
        // Include the path to the action in the name, as there can be multiple actions in the same repository. For
        // Docker actions, the path is the image reference instead.
        val normalizedPath = path?.takeUnless { type == ABom.ActionType.DOCKER }
            ?.let { normalizeActionPath(it) }.orEmpty()
        val pathSuffix = normalizedPath.ensurePrefix("/")

        return if (type == ABom.ActionType.LOCAL) {
            Identifier(
                type = PACKAGE_TYPE,
                namespace = projectRepository?.first.orEmpty(),
                name = projectRepository?.second?.let { it + pathSuffix } ?: normalizedPath,
                version = projectVcs.revision
            )
        } else {
            Identifier(
                type = PACKAGE_TYPE,
                namespace = owner.orEmpty(),
                name = repo.orEmpty() + pathSuffix,
                version = ref.orEmpty()
            )
        }
    }
}

internal fun vcsToHomepageUrl(vcsUrl: String) =
    normalizeVcsUrl(vcsUrl).removeSuffix(".git").replace("ssh://git@", "https://")

/**
 * Create a purl of type "github" for the action in the [subpath] of the repository [owner]/[repo] in the given
 * [version], or an empty string if the [subpath] is outside the repository.
 */
private fun createGitHubPurl(owner: String, repo: String, version: String, subpath: String) =
    if (File(subpath).startsWith("..")) "" else Identifier("github", owner, repo, version).toPurl(subpath = subpath)

/**
 * Normalize the [path] to an action within a repository, as purl subpaths must not contain "." or ".." segments. The
 * result is empty for the root of the repository, and starts with ".." if the path is outside the repository.
 */
internal fun normalizeActionPath(path: String) = File(path).normalize().invariantSeparatorsPath
