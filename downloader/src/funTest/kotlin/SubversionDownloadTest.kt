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

package com.here.ort.downloader.vcs

import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.VcsInfo
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.test.ExpensiveTag

import io.kotlintest.TestCase
import io.kotlintest.TestResult
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

private const val REPO_URL = "https://svn.code.sf.net/p/sendmessage/code"
private const val REPO_REV = "115"
private const val REPO_PATH = "trunk"
private const val REPO_TAG = "tags/SendMessage-1.0.2"
private const val REPO_REV_FOR_TAG = "37"
private const val REPO_VERSION = "1.0.1"
private const val REPO_REV_FOR_VERSION = "30"
private const val REPO_PATH_FOR_VERSION = "src/resources"

class SubversionDownloadTest : StringSpec() {
    private val svn = Subversion()
    private lateinit var outputDir: File

    override fun beforeTest(testCase: TestCase) {
        outputDir = createTempDir()
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        outputDir.safeDeleteRecursively(force = true)
    }

    init {
        "Subversion can download a given revision".config(enabled = svn.isInPath(), tags = setOf(ExpensiveTag)) {
            val pkg = Package.EMPTY.copy(vcsProcessed = VcsInfo("Subversion", REPO_URL, REPO_REV))
            val expectedFiles = listOf(
                    ".svn",
                    "branches",
                    "tags",
                    "trunk",
                    "wiki"
            )

            val workingTree = svn.download(pkg, outputDir)
            val actualFiles = workingTree.workingDir.list().sorted()

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }

        "Subversion can download only a single path"
                .config(enabled = svn.isInPath(), tags = setOf(ExpensiveTag)) {
            val pkg = Package.EMPTY.copy(vcsProcessed = VcsInfo("Subversion", REPO_URL, REPO_REV, path = REPO_PATH))
            val expectedFiles = listOf(
                    "SendMessage.sln",
                    "default.build",
                    "default.build.user.tmpl",
                    "sktoolslib", // This is an external.
                    "src",
                    "tools",
                    "version.build.in",
                    "versioninfo.build"
            )

            val workingTree = svn.download(pkg, outputDir)
            val actualFiles = workingTree.workingDir.list().sorted()

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }

        "Subversion can download a given tag".config(enabled = svn.isInPath(), tags = setOf(ExpensiveTag)) {
            val pkg = Package.EMPTY.copy(vcsProcessed = VcsInfo("Subversion", REPO_URL, "", path = REPO_TAG))
            val expectedFiles = listOf(
                    "SendMessage.proj",
                    "SendMessage.sln",
                    "src",
                    "version.proj",
                    "web"
            )

            val workingTree = svn.download(pkg, outputDir)
            val actualFiles = workingTree.workingDir.list().sorted()

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV_FOR_TAG
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }

        "Subversion can download based on a version"
                .config(enabled = svn.isInPath(), tags = setOf(ExpensiveTag)) {
            val pkg = Package.EMPTY.copy(
                    id = Identifier.EMPTY.copy(version = REPO_VERSION),
                    vcsProcessed = VcsInfo("Subversion", REPO_URL, "")
            )

            val workingTree = svn.download(pkg, outputDir)

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV_FOR_VERSION
        }

        "Subversion can download only a single path based on a version"
                .config(enabled = svn.isInPath(), tags = setOf(ExpensiveTag)) {
            val pkg = Package.EMPTY.copy(
                    id = Identifier.EMPTY.copy(version = REPO_VERSION),
                    vcsProcessed = VcsInfo("Subversion", REPO_URL, "", path = REPO_PATH_FOR_VERSION)
            )
            val expectedFiles = listOf(
                    "SendMessage.ico",
                    "searchw.cur",
                    "searchw.ico",
                    "windowmessages.xml"
            )

            val workingTree = svn.download(pkg, outputDir)
            val actualFiles = File(workingTree.workingDir, REPO_PATH_FOR_VERSION).list().sorted()

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV_FOR_VERSION
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }
    }
}
