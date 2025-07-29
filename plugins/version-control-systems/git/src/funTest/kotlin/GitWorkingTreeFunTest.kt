/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.downloader.WorkingTree
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.utils.common.div

class GitWorkingTreeFunTest : StringSpec({
    val git = GitFactory().create(PluginConfig.EMPTY)
    val repoDir = tempdir()
    val vcsInfo = VcsInfo(
        type = VcsType.GIT,
        url = "https://github.com/oss-review-toolkit/ort-test-data-git.git",
        revision = "main"
    )

    lateinit var workingTree: WorkingTree

    beforeSpec {
        workingTree = git.initWorkingTree(repoDir, vcsInfo)
        git.updateWorkingTree(workingTree, "main")
    }

    "Git detects non-working-trees" {
        git.getWorkingTree(tempdir()).isValid() shouldBe false
    }

    "Detected Git working tree information is correct" {
        workingTree.isValid() shouldBe true
        workingTree.getInfo() shouldBe vcsInfo.copy(revision = "6f09f276c4426c387c6663f54bbd45aea8d81dac")
        workingTree.getNested() should beEmpty()
        workingTree.getRootPath() shouldBe repoDir
        workingTree.getPathToRoot(repoDir / "README.md") shouldBe "README.md"
    }

    "Git correctly lists remote branches" {
        workingTree.listRemoteBranches() should containExactlyInAnyOrder(
            "main",
            "branch1",
            "branch2",
            "branch3"
        )
    }

    "Git correctly lists remote tags" {
        workingTree.listRemoteTags() should containExactlyInAnyOrder(
            "tag1",
            "tag2",
            "tag3"
        )
    }

    "Git correctly lists submodules" {
        val expectedSubmodules = listOf(
            "plugins/package-managers/pub/src/funTest/assets/projects/external/dart-http",
            "plugins/package-managers/python/src/funTest/assets/projects/external/example-python-flask",
            "plugins/package-managers/python/src/funTest/assets/projects/external/spdx-tools-python",
            "plugins/package-managers/sbt/src/funTest/assets/projects/external/multi-project",
            "plugins/package-managers/stack/src/funTest/assets/projects/external/quickcheck-state-machine"
        ).associateWith { VersionControlSystem.getPathInfo(File("../../../$it")) }

        git.getWorkingTree(File("..")).getNested() shouldBe expectedSubmodules
    }
})
