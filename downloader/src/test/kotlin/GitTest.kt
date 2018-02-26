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

package com.here.ort.downloader.vcs

import com.here.ort.utils.unpack
import com.here.ort.utils.getUserConfigDirectory
import com.here.ort.utils.safeDeleteRecursively

import io.kotlintest.Spec
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.specs.StringSpec

import java.io.File

class GitTest : StringSpec() {
    private lateinit var zipContentDir: File

    // Required to make lateinit of outputDir work.
    override val oneInstancePerTest = false

    override fun interceptSpec(context: Spec, spec: () -> Unit) {
        val zipFile = File("src/test/assets/pipdeptree-2018-01-03-git.zip")

        zipContentDir = createTempDir()

        println("Extracting '$zipFile' to '$zipContentDir'...")
        zipFile.unpack(zipContentDir)

        try {
            super.interceptSpec(context, spec)
        } finally {
            zipContentDir.safeDeleteRecursively()
        }
    }

    init {
        "Detected Git version is not empty" {
            val version = Git.getVersion()
            println("Git version $version detected.")
            version shouldNotBe ""
        }

        "Git detects non-working-trees" {
            Git.getWorkingTree(getUserConfigDirectory()).isValid() shouldBe false
        }

        "Git correctly detects URLs to remote repositories" {
            // Bitbucket forwards to ".git" URLs for Git repositories, so we can omit the suffix.
            Git.isApplicableUrl("https://bitbucket.org/yevster/spdxtraxample") shouldBe true

            Git.isApplicableUrl("https://bitbucket.org/paniq/masagin") shouldBe false
        }

        "Detected Git working tree information is correct" {
            val workingTree = Git.getWorkingTree(zipContentDir)

            workingTree.getType() shouldBe "Git"
            workingTree.isValid() shouldBe true
            workingTree.getRemoteUrl() shouldBe "https://github.com/naiquevin/pipdeptree.git"
            workingTree.getRevision() shouldBe "6f70dd5508331b6cfcfe3c1b626d57d9836cfd7c"
            workingTree.getRootPath() shouldBe zipContentDir.path.replace(File.separatorChar, '/')
            workingTree.getPathToRoot(File(zipContentDir, "tests")) shouldBe "tests"
        }

        "Git correctly lists remote tags" {
            val expectedTags = listOf(
                    "0.10.0",
                    "0.10.1",
                    "0.11.0",
                    "0.5.0",
                    "0.6.0",
                    "0.7.0",
                    "0.8.0",
                    "0.9.0"
            )

            val workingTree = Git.getWorkingTree(zipContentDir)
            workingTree.listRemoteTags().joinToString("\n") shouldBe expectedTags.joinToString("\n")
        }
    }
}
