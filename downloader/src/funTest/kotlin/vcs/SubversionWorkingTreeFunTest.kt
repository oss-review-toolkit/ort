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
import org.ossreviewtoolkit.utils.core.ortDataDirectory
import org.ossreviewtoolkit.utils.test.createSpecTempDir

class SubversionWorkingTreeFunTest : StringSpec({
    val svn = Subversion()
    val zipContentDir = createSpecTempDir()

    beforeSpec {
        val zipFile = File("src/funTest/assets/docutils-2018-01-03-svn-trunk.zip")
        print("Extracting '$zipFile' to '$zipContentDir'... ")
        zipFile.unpack(zipContentDir)
        println("done.")
    }

    "Detected Subversion version is not empty" {
        val version = svn.getVersion()
        println("Subversion version $version detected.")
        version shouldNotBe ""
    }

    "Subversion detects non-working-trees" {
        svn.getWorkingTree(ortDataDirectory).isValid() shouldBe false
    }

    "Subversion correctly detects URLs to remote repositories" {
        svn.isApplicableUrl("https://svn.code.sf.net/p/grepwin/code/") shouldBe true
        svn.isApplicableUrl("https://bitbucket.org/facebook/lz4revlog") shouldBe false
    }

    "Detected Subversion working tree information is correct" {
        val workingTree = svn.getWorkingTree(zipContentDir)

        workingTree.isValid() shouldBe true
        workingTree.getInfo() shouldBe VcsInfo(
            type = VcsType.SUBVERSION,
            url = "https://svn.code.sf.net/p/docutils/code/trunk/docutils",
            revision = "8207",
            path = ""
        )
        workingTree.getNested() should beEmpty()
        workingTree.getRootPath() shouldBe zipContentDir
        workingTree.getPathToRoot(zipContentDir.resolve("docutils")) shouldBe "docutils"
    }

    "Subversion correctly lists remote branches" {
        val expectedBranches = listOf(
            "address-rendering",
            "index-bug",
            "lossless-rst-writer",
            "nesting",
            "plugins",
            "rel-0.15",
            "subdocs"
        ).map { "branches/$it" }

        val workingTree = svn.getWorkingTree(zipContentDir)
        workingTree.listRemoteBranches().joinToString("\n") shouldBe expectedBranches.joinToString("\n")
    }

    "Subversion correctly lists remote tags" {
        val expectedTags = listOf(
            "docutils-0.10",
            "docutils-0.11",
            "docutils-0.12",
            "docutils-0.13.1",
            "docutils-0.14",
            "docutils-0.14.0a",
            "docutils-0.14a0",
            "docutils-0.14rc1",
            "docutils-0.14rc2",
            "docutils-0.15",
            "docutils-0.16",
            "docutils-0.17",
            "docutils-0.17.1",
            "docutils-0.18",
            "docutils-0.18.1",
            "docutils-0.3.7",
            "docutils-0.3.9",
            "docutils-0.4",
            "docutils-0.5",
            "docutils-0.6",
            "docutils-0.7",
            "docutils-0.8",
            "docutils-0.8.1",
            "docutils-0.9",
            "docutils-0.9.1",
            "initial",
            "merged_to_nesting",
            "prest-0.3.10",
            "prest-0.3.11",
            "start"
        ).map { "tags/$it" }

        val workingTree = svn.getWorkingTree(zipContentDir)
        workingTree.listRemoteTags().joinToString("\n") shouldBe expectedTags.joinToString("\n")
    }
})
