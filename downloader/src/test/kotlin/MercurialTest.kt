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
import com.here.ort.utils.unpack

import io.kotlintest.Spec
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.specs.StringSpec

import java.io.File

class MercurialTest : StringSpec() {
    private lateinit var zipContentDir: File

    // Required to make lateinit of outputDir work.
    override val oneInstancePerTest = false

    override fun interceptSpec(context: Spec, spec: () -> Unit) {
        val zipFile = File("src/test/assets/lz4revlog-2018-01-03-hg.zip")

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
        "Detected Mercurial version is not empty" {
            val version = Mercurial.getVersion()
            println("Mercurial version $version detected.")
            version shouldNotBe ""
        }.config(enabled = Mercurial.isInPath())

        "Mercurial detects non-working-trees" {
            Mercurial.getWorkingTree(getUserConfigDirectory()).isValid() shouldBe false
        }.config(enabled = Mercurial.isInPath())

        "Mercurial correctly detects URLs to remote repositories" {
            Mercurial.isApplicableUrl("https://bitbucket.org/paniq/masagin") shouldBe true

            // Bitbucket forwards to ".git" URLs for Git repositories, so we can omit the suffix.
            Mercurial.isApplicableUrl("https://bitbucket.org/yevster/spdxtraxample") shouldBe false
        }.config(enabled = Mercurial.isInPath())

        "Detected Mercurial working tree information is correct" {
            val workingTree = Mercurial.getWorkingTree(zipContentDir)

            workingTree.getType() shouldBe "Mercurial"
            workingTree.isValid() shouldBe true
            workingTree.getRemoteUrl() shouldBe "https://bitbucket.org/facebook/lz4revlog"
            workingTree.getRevision() shouldBe "422ca71c35132f1f55d20a13355708aec7669b50"
            workingTree.getRootPath() shouldBe zipContentDir.path.replace(File.separatorChar, '/')
            workingTree.getPathToRoot(File(zipContentDir, "tests")) shouldBe "tests"
        }.config(enabled = Mercurial.isInPath())

        "Mercurial correctly lists remote tags" {
            val expectedTags = listOf(
                    "1.0",
                    "1.0.1",
                    "1.0.2"
            )

            val workingTree = Mercurial.getWorkingTree(zipContentDir)
            workingTree.listRemoteTags().joinToString("\n") shouldBe expectedTags.joinToString("\n")
        }.config(enabled = Mercurial.isInPath())
    }
}
