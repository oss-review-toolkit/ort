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

package org.ossreviewtoolkit.plugins.versioncontrolsystems.git

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe

import java.io.File

import kotlin.time.Duration.Companion.milliseconds

import kotlinx.coroutines.delay

import org.ossreviewtoolkit.downloader.WorkingTree
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.utils.common.Os

private val branches = mapOf(
    "main" to "6f09f276c4426c387c6663f54bbd45aea8d81dac",
    "branch1" to "0c58ea81d5c8112affab7a9cd6308deb4bc51589",
    "branch2" to "7a05ad3ad30b4ddbfac22e0b768fb91383f16d8d",
    "branch3" to "b798693a551e4d0e96d09409948327178a9abbce"
)

private val tags = mapOf(
    "tag1" to "0c58ea81d5c8112affab7a9cd6308deb4bc51589",
    "tag2" to "7a05ad3ad30b4ddbfac22e0b768fb91383f16d8d",
    "tag3" to "b798693a551e4d0e96d09409948327178a9abbce"
)

class GitFunTest : WordSpec({
    val git = GitFactory().create(PluginConfig.EMPTY)
    val vcsInfo = VcsInfo(
        type = VcsType.GIT,
        url = "https://github.com/oss-review-toolkit/ort-test-data-git.git",
        revision = "main"
    )

    lateinit var repoDir: File
    lateinit var workingTree: WorkingTree

    beforeEach {
        repoDir = tempdir()
        workingTree = git.initWorkingTree(repoDir, vcsInfo)
    }

    afterEach {
        // This delay is required to successfully let Kotest delete the temporary directories on Windows.
        if (Os.isWindows) delay(100.milliseconds)
    }

    "updateWorkingTree" should {
        "update the working tree to the correct revision" {
            branches.values.forEach { revision ->
                git.updateWorkingTree(workingTree, revision) shouldBeSuccess revision
                workingTree.getRevision() shouldBe revision
            }
        }

        "update the working tree to the correct tag" {
            tags.forEach { (tag, revision) ->
                git.updateWorkingTree(workingTree, tag) shouldBeSuccess tag
                workingTree.getRevision() shouldBe revision
            }
        }

        "update the working tree to the correct branch" {
            branches.forEach { (branch, revision) ->
                git.updateWorkingTree(workingTree, branch) shouldBeSuccess branch
                workingTree.getRevision() shouldBe revision
            }
        }

        "update an outdated local branch" {
            val branch = "branch1"
            val revision = branches.getValue(branch)

            git.updateWorkingTree(workingTree, branch)
            GitCommand.run("reset", "--hard", "HEAD~1", workingDir = repoDir).requireSuccess()

            git.updateWorkingTree(workingTree, branch) shouldBeSuccess branch
            workingTree.getRevision() shouldBe revision
        }
    }
})
