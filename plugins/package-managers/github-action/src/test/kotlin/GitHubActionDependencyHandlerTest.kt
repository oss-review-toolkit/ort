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

    fun resolvePackages(vararg actions: ABom.Action) =
        DependencyGraphBuilder(GitHubActionDependencyHandler(projectVcs)).run {
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
