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

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder

class GitHubActionDependencyHandlerTest : WordSpec({
    val projectVcs = VcsInfo(
        type = VcsType.GIT,
        url = "https://github.com/oss-review-toolkit/ort.git",
        revision = "0123456789abcdef0123456789abcdef01234567"
    )

    val projectId = Identifier("GitHubAction:workflow:workflow:")

    fun resolvePackages(vararg actions: ABom.Action, vcs: VcsInfo = projectVcs) =
        DependencyGraphBuilder(GitHubActionDependencyHandler(vcs)).run {
            addDependencies(projectId, "job", actions.toList())
            packages()
        }

    "The dependency handler" should {
        "create distinct packages for actions in different subdirectories of the same repository" {
            val packages = resolvePackages(
                subdirectoryAction("github", "codeql-action", "init", "v4"),
                subdirectoryAction("github", "codeql-action", "analyze", "v4")
            )

            packages.map { it.id } should containExactlyInAnyOrder(
                Identifier("GitHubAction:github:codeql-action/init:v4"),
                Identifier("GitHubAction:github:codeql-action/analyze:v4")
            )

            packages.map { it.vcs.path } should containExactlyInAnyOrder("init", "analyze")
        }

        "create distinct packages for an action in the root and in a subdirectory of the same repository" {
            val packages = resolvePackages(
                standardAction("actions", "cache", "v4"),
                subdirectoryAction("actions", "cache", "restore", "v4")
            )

            packages.map { it.id } should containExactlyInAnyOrder(
                Identifier("GitHubAction:actions:cache:v4"),
                Identifier("GitHubAction:actions:cache/restore:v4")
            )
        }

        "create distinct packages for different local actions" {
            val packages = resolvePackages(
                localAction("./"),
                localAction("./.github/actions/free-disk-space"),
                localAction("./.github/actions/setup-java")
            )

            packages.map { it.id } should containExactlyInAnyOrder(
                Identifier("GitHubAction:oss-review-toolkit:ort:${projectVcs.revision}"),
                Identifier(
                    "GitHubAction:oss-review-toolkit:ort/.github/actions/free-disk-space:${projectVcs.revision}"
                ),
                Identifier("GitHubAction:oss-review-toolkit:ort/.github/actions/setup-java:${projectVcs.revision}")
            )

            packages.map { it.vcs.path } should containExactlyInAnyOrder(
                "",
                ".github/actions/free-disk-space",
                ".github/actions/setup-java"
            )
        }

        "create purls with the repository as the name and the path to the action as the subpath" {
            val packages = resolvePackages(
                standardAction("actions", "checkout", "v6"),
                subdirectoryAction("github", "codeql-action", "init", "v4"),
                localAction("./"),
                localAction("./.github/actions/free-disk-space")
            )

            packages.map { it.purl } should containExactlyInAnyOrder(
                "pkg:github/actions/checkout@v6",
                "pkg:github/github/codeql-action@v4#init",
                "pkg:github/oss-review-toolkit/ort@${projectVcs.revision}",
                "pkg:github/oss-review-toolkit/ort@${projectVcs.revision}#.github/actions/free-disk-space"
            )
        }

        "create purls with encoded versions" {
            val packages = resolvePackages(standardAction("actions", "checkout", "releases/v6"))

            packages.map { it.purl } should containExactlyInAnyOrder("pkg:github/actions/checkout@releases%2Fv6")
        }

        "create purls with a lowercase owner and repository" {
            val packages = resolvePackages(subdirectoryAction("SonarSource", "SonarQube-Scan-Action", "Install", "V5"))

            packages.map { it.purl } should containExactly("pkg:github/sonarsource/sonarqube-scan-action@V5#Install")
        }

        "normalize the paths to local actions" {
            val packages = resolvePackages(
                localAction("./.github/actions/../../shared/"),
                localAction("./shared"),
                localAction("./.github/actions/..")
            )

            packages.map { it.id } should containExactlyInAnyOrder(
                Identifier("GitHubAction", "oss-review-toolkit", "ort/shared", projectVcs.revision),
                Identifier("GitHubAction", "oss-review-toolkit", "ort/.github", projectVcs.revision)
            )

            packages.map { it.purl } should containExactlyInAnyOrder(
                "pkg:github/oss-review-toolkit/ort@${projectVcs.revision}#shared",
                "pkg:github/oss-review-toolkit/ort@${projectVcs.revision}#.github"
            )

            packages.map { it.vcs.path } should containExactlyInAnyOrder("shared", ".github")
        }

        "create no purl for local actions outside the repository" {
            val packages = resolvePackages(localAction("../other/action"))

            packages.map { it.purl } should containExactly("")
        }

        "create packages for local actions if the project has no VCS information" {
            val packages = resolvePackages(
                localAction("./"),
                localAction("./.github/actions/free-disk-space"),
                vcs = VcsInfo.EMPTY
            )

            packages.map { it.id } should containExactlyInAnyOrder(
                Identifier("GitHubAction", "", "", ""),
                Identifier("GitHubAction", "", ".github/actions/free-disk-space", "")
            )

            packages.map { it.purl } should containExactlyInAnyOrder("", "")
        }

        "create packages for local actions if the project is not hosted on GitHub" {
            val gitLabVcs = projectVcs.copy(url = "https://gitlab.com/oss-review-toolkit/ort.git")

            val packages = resolvePackages(localAction("./.github/actions/free-disk-space"), vcs = gitLabVcs)

            packages.map { it.id } should containExactlyInAnyOrder(
                Identifier("GitHubAction", "", ".github/actions/free-disk-space", projectVcs.revision)
            )

            packages.map { it.purl } should containExactlyInAnyOrder("")
        }

        "not treat projects as hosted on GitHub if only part of the URL matches" {
            listOf(
                "https://notgithub.com/oss-review-toolkit/ort.git",
                "https://gitlab.example/mirrors/github.com/oss-review-toolkit/ort.git"
            ).forAll { url ->
                val packages = resolvePackages(localAction("./"), vcs = projectVcs.copy(url = url))

                packages.map { it.id.namespace to it.purl } should containExactly("" to "")
            }
        }

        "get the repository of local actions from SCP-like and SSH URLs" {
            listOf(
                "git@github.com:oss-review-toolkit/ort.git",
                "ssh://git@github.com/oss-review-toolkit/ort.git"
            ).forAll { url ->
                val packages = resolvePackages(localAction("./"), vcs = projectVcs.copy(url = url))

                packages.map { it.id } should containExactly(
                    Identifier("GitHubAction", "oss-review-toolkit", "ort", projectVcs.revision)
                )
            }
        }
    }
})

private fun standardAction(owner: String, repo: String, ref: String) =
    ABom.Action(
        uses = "$owner/$repo@$ref",
        owner = owner,
        repo = repo,
        ref = ref,
        refType = ABom.RefType.TAG,
        type = ABom.ActionType.STANDARD,
        pinned = false
    )

private fun subdirectoryAction(owner: String, repo: String, path: String, ref: String) =
    ABom.Action(
        uses = "$owner/$repo/$path@$ref",
        owner = owner,
        repo = repo,
        path = path,
        ref = ref,
        refType = ABom.RefType.TAG,
        type = ABom.ActionType.SUBDIRECTORY,
        pinned = false
    )

private fun localAction(path: String) =
    ABom.Action(
        uses = path,
        path = path,
        type = ABom.ActionType.LOCAL,
        pinned = false
    )
