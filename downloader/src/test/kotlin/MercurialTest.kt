/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

import com.here.ort.downloader.vcs.Mercurial
import com.here.ort.utils.getUserConfigDirectory
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.searchUpwardsForSubdirectory
import com.here.ort.utils.unpack

import io.kotlintest.Description
import io.kotlintest.Spec
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec

import java.io.File

class MercurialTest : StringSpec() {
    private lateinit var zipContentDir: File

    override fun beforeSpec(description: Description, spec: Spec) {
        val rootDir = File(".").searchUpwardsForSubdirectory(".git")!!
        val zipFile = File(rootDir, "downloader/src/test/assets/lz4revlog-2018-01-03-hg.zip")

        zipContentDir = createTempDir()

        println("Extracting '$zipFile' to '$zipContentDir'...")
        zipFile.unpack(zipContentDir)
    }

    override fun afterSpec(description: Description, spec: Spec) {
        zipContentDir.safeDeleteRecursively()
    }

    init {
        "Detected Mercurial version is not empty".config(enabled = Mercurial.isInPath()) {
            val version = Mercurial.getVersion()
            println("Mercurial version $version detected.")
            version shouldNotBe ""
        }

        "Mercurial detects non-working-trees".config(enabled = Mercurial.isInPath()) {
            Mercurial.getWorkingTree(getUserConfigDirectory()).isValid() shouldBe false
        }

        "Mercurial correctly detects URLs to remote repositories".config(enabled = Mercurial.isInPath()) {
            Mercurial.isApplicableUrl("https://bitbucket.org/paniq/masagin") shouldBe true

            // Bitbucket forwards to ".git" URLs for Git repositories, so we can omit the suffix.
            Mercurial.isApplicableUrl("https://bitbucket.org/yevster/spdxtraxample") shouldBe false
        }

        "Detected Mercurial working tree information is correct".config(enabled = Mercurial.isInPath()) {
            val workingTree = Mercurial.getWorkingTree(zipContentDir)

            workingTree.getType() shouldBe "Mercurial"
            workingTree.isValid() shouldBe true
            workingTree.getRemoteUrl() shouldBe "https://bitbucket.org/facebook/lz4revlog"
            workingTree.getRevision() shouldBe "422ca71c35132f1f55d20a13355708aec7669b50"
            workingTree.getRootPath() shouldBe zipContentDir
            workingTree.getPathToRoot(File(zipContentDir, "tests")) shouldBe "tests"
        }

        "Mercurial correctly lists remote branches".config(enabled = Mercurial.isInPath()) {
            val expectedBranches = listOf(
                    "default"
            )

            val workingTree = Mercurial.getWorkingTree(zipContentDir)
            workingTree.listRemoteBranches().joinToString("\n") shouldBe expectedBranches.joinToString("\n")
        }

        "Mercurial correctly lists remote tags".config(enabled = Mercurial.isInPath()) {
            val expectedTags = listOf(
                    "1.0",
                    "1.0.1",
                    "1.0.2"
            )

            val workingTree = Mercurial.getWorkingTree(zipContentDir)
            workingTree.listRemoteTags().joinToString("\n") shouldBe expectedTags.joinToString("\n")
        }
    }
}
