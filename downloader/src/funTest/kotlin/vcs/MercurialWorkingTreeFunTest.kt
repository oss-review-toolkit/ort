/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import java.io.File

import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.common.unpack
import org.ossreviewtoolkit.utils.ort.ortDataDirectory
import org.ossreviewtoolkit.utils.test.createSpecTempDir

class MercurialWorkingTreeFunTest : StringSpec({
    val hg = Mercurial()
    val zipContentDir = createSpecTempDir()

    beforeSpec {
        val zipFile = File("src/funTest/assets/lz4revlog-2018-01-03-hg.zip")
        println("Extracting '$zipFile' to '$zipContentDir'...")
        zipFile.unpack(zipContentDir)
    }

    "Detected Mercurial version is not empty" {
        val version = hg.getVersion()
        println("Mercurial version $version detected.")
        version shouldNotBe ""
    }

    "Mercurial detects non-working-trees" {
        hg.getWorkingTree(ortDataDirectory).isValid() shouldBe false
    }

    "Mercurial correctly detects URLs to remote repositories" {
        hg.isApplicableUrl("https://hg.sr.ht/~duangle/paniq_legacy") shouldBe true
        hg.isApplicableUrl("https://bitbucket.org/yevster/spdxtraxample.git") shouldBe false
    }

    "Detected Mercurial working tree information is correct" {
        val workingTree = hg.getWorkingTree(zipContentDir)

        workingTree.isValid() shouldBe true
        workingTree.getInfo() shouldBe VcsInfo(
            type = VcsType.MERCURIAL,
            url = "https://bitbucket.org/facebook/lz4revlog",
            revision = "422ca71c35132f1f55d20a13355708aec7669b50",
            path = ""
        )
        workingTree.getNested() should beEmpty()
        workingTree.getRootPath() shouldBe zipContentDir
        workingTree.getPathToRoot(zipContentDir.resolve("tests")) shouldBe "tests"
    }

    // TODO: Find an alternative to Bitbucket that hosts public Mercurial repositories.
    "Mercurial correctly lists remote branches".config(enabled = false) {
        val expectedBranches = listOf(
            "default"
        )

        val workingTree = hg.getWorkingTree(zipContentDir)
        workingTree.listRemoteBranches().joinToString("\n") shouldBe expectedBranches.joinToString("\n")
    }

    // TODO: Find an alternative to Bitbucket that hosts public Mercurial repositories.
    "Mercurial correctly lists remote tags".config(enabled = false) {
        val expectedTags = listOf(
            "1.0",
            "1.0.1",
            "1.0.2"
        )

        val workingTree = hg.getWorkingTree(zipContentDir)
        workingTree.listRemoteTags().joinToString("\n") shouldBe expectedTags.joinToString("\n")
    }
})
