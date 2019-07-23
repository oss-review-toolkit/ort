/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.downloader

import com.here.ort.downloader.vcs.Subversion
import com.here.ort.model.VcsType
import com.here.ort.utils.getUserOrtDirectory
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.unpack

import io.kotlintest.Spec
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec

import java.io.File

class SubversionTest : StringSpec() {
    private val svn = Subversion()
    private lateinit var zipContentDir: File

    override fun beforeSpec(spec: Spec) {
        val zipFile = File("src/test/assets/docutils-2018-01-03-svn-trunk.zip")

        zipContentDir = createTempDir()

        println("Extracting '$zipFile' to '$zipContentDir'...")
        zipFile.unpack(zipContentDir)
    }

    override fun afterSpec(spec: Spec) {
        zipContentDir.safeDeleteRecursively(force = true)
    }

    init {
        "Detected Subversion version is not empty" {
            val version = svn.getVersion()
            println("Subversion version $version detected.")
            version shouldNotBe ""
        }

        "Subversion detects non-working-trees" {
            svn.getWorkingTree(getUserOrtDirectory()).isValid() shouldBe false
        }

        "Subversion correctly detects URLs to remote repositories" {
            svn.isApplicableUrl("http://svn.code.sf.net/p/grepwin/code/") shouldBe true
            svn.isApplicableUrl("https://bitbucket.org/facebook/lz4revlog") shouldBe false
        }

        "Detected Subversion working tree information is correct" {
            val workingTree = svn.getWorkingTree(zipContentDir)

            workingTree.vcsType shouldBe VcsType.SUBVERSION
            workingTree.isValid() shouldBe true
            workingTree.getRemoteUrl() shouldBe "https://svn.code.sf.net/p/docutils/code/trunk/docutils"
            workingTree.getRevision() shouldBe "8207"
            workingTree.getRootPath() shouldBe zipContentDir
            workingTree.getPathToRoot(File(zipContentDir, "docutils")) shouldBe "docutils"
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
            )

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
            )

            val workingTree = svn.getWorkingTree(zipContentDir)
            workingTree.listRemoteTags().joinToString("\n") shouldBe expectedTags.joinToString("\n")
        }
    }
}
