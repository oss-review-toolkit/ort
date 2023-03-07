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

package org.ossreviewtoolkit.downloader.vcs

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.containAll
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.common.unpack
import org.ossreviewtoolkit.utils.ort.ortDataDirectory
import org.ossreviewtoolkit.utils.test.createSpecTempDir
import org.ossreviewtoolkit.utils.test.getAssetFile

class GitWorkingTreeFunTest : StringSpec({
    val git = Git()
    val zipContentDir = createSpecTempDir()

    beforeSpec {
        val zipFile = getAssetFile("pipdeptree-2018-01-03-git.zip")
        println("Extracting '$zipFile' to '$zipContentDir'...")
        zipFile.unpack(zipContentDir)
    }

    "Git detects non-working-trees" {
        git.getWorkingTree(ortDataDirectory).isValid() shouldBe false
    }

    "Detected Git working tree information is correct" {
        val workingTree = git.getWorkingTree(zipContentDir)

        workingTree.isValid() shouldBe true
        workingTree.getInfo() shouldBe VcsInfo(
            type = VcsType.GIT,
            url = "https://github.com/naiquevin/pipdeptree.git",
            revision = "6f70dd5508331b6cfcfe3c1b626d57d9836cfd7c",
            path = ""
        )
        workingTree.getNested() should beEmpty()
        workingTree.getRootPath() shouldBe zipContentDir
        workingTree.getPathToRoot(zipContentDir.resolve("tests")) shouldBe "tests"
    }

    "Git correctly lists remote branches" {
        val workingTree = git.getWorkingTree(zipContentDir)

        // Ignore auto-created branches by Dependabot to avoid regular updates to this list.
        workingTree.listRemoteBranches().filterNot { it.startsWith("dependabot/") } should containExactlyInAnyOrder(
            "all-repos_autofix_bump",
            "all-repos_autofix_bump-2023-02-05",
            "main",
            "pre-commit-ci-update-config"
        )
    }

    "Git correctly lists remote tags" {
        val workingTree = git.getWorkingTree(zipContentDir)

        workingTree.listRemoteTags() should containAll(expectedRemoteTags)
    }

    "Git correctly lists submodules" {
        val expectedSubmodules = listOf(
            "analyzer/src/funTest/assets/projects/external/quickcheck-state-machine",
            "analyzer/src/funTest/assets/projects/external/sbt-multi-project-example",
            "plugins/package-managers/pub/src/funTest/assets/projects/external/dart-http",
            "plugins/package-managers/python/src/funTest/assets/projects/external/example-python-flask",
            "plugins/package-managers/python/src/funTest/assets/projects/external/spdx-tools-python"
        ).associateWith { VersionControlSystem.getPathInfo(File("../$it")) }

        val workingTree = git.getWorkingTree(File(".."))
        workingTree.getNested() shouldBe expectedSubmodules
    }
})

private val expectedRemoteTags = listOf(
    "0.10.0",
    "0.10.1",
    "0.11.0",
    "0.12.0",
    "0.12.1",
    "0.13.0",
    "0.13.1",
    "0.13.2",
    "0.5.0",
    "0.6.0",
    "0.7.0",
    "0.8.0",
    "0.9.0",
    "1.0.0",
    "2.0.0",
    "2.0.0b1",
    "2.1.0",
    "2.2.0",
    "2.2.1",
    "2.3.0",
    "2.3.1",
    "2.3.2",
    "2.3.3",
    "2.4.0",
    "2.5.0",
    "2.5.1"
)
