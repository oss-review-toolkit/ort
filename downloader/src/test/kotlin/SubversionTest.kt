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

import com.here.ort.downloader.vcs.Subversion
import com.here.ort.utils.getUserConfigDirectory
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.unpack

import io.kotlintest.Spec
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.specs.StringSpec

import java.io.File

class SubversionTest : StringSpec() {
    private lateinit var zipContentDir: File

    // Required to make lateinit of outputDir work.
    override val oneInstancePerTest = false

    override fun interceptSpec(context: Spec, spec: () -> Unit) {
        val zipFile = File("src/test/assets/docutils-2018-01-03-svn-trunk.zip")

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
        "Detected Subversion version is not empty" {
            val version = Subversion.getVersion()
            println("Subversion version $version detected.")
            version shouldNotBe ""
        }.config(enabled = Subversion.isInPath())

        "Subversion detects non-working-trees" {
            Subversion.getWorkingTree(getUserConfigDirectory()).isValid() shouldBe false
        }.config(enabled = Subversion.isInPath())

        "Subversion correctly detects URLs to remote repositories" {
            Subversion.isApplicableUrl("http://svn.code.sf.net/p/grepwin/code/") shouldBe true
            Subversion.isApplicableUrl("https://bitbucket.org/facebook/lz4revlog") shouldBe false
        }.config(enabled = false)

        "Detected Subversion working tree information is correct" {
            val workingTree = Subversion.getWorkingTree(zipContentDir)

            workingTree.getType() shouldBe "Subversion"
            workingTree.isValid() shouldBe true
            workingTree.getRemoteUrl() shouldBe "https://svn.code.sf.net/p/docutils/code/trunk/docutils"
            workingTree.getRevision() shouldBe "8207"
            workingTree.getRootPath() shouldBe zipContentDir.path.replace(File.separatorChar, '/')
            workingTree.getPathToRoot(File(zipContentDir, "docutils")) shouldBe "docutils"
        }.config(enabled = Subversion.isInPath())

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

            val workingTree = Subversion.getWorkingTree(zipContentDir)
            workingTree.listRemoteTags().joinToString("\n") shouldBe expectedTags.joinToString("\n")
        }.config(enabled = false)
    }
}
