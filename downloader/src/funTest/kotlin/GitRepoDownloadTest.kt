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

import com.here.ort.model.Package
import com.here.ort.model.VcsInfo
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.test.ExpensiveTag

import io.kotlintest.TestCase
import io.kotlintest.TestResult
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File
import java.io.FileFilter

private const val REPO_URL = "https://github.com/heremaps/oss-review-toolkit-test-data-git-repo"
private const val REPO_REV = "f00ec4cbb670b49a156fd95d29e8fd148d931ba9"
private const val REPO_MANIFEST = "manifest.xml"

class GitRepoDownloadTest : StringSpec() {
    private lateinit var outputDir: File

    override fun beforeTest(testCase: TestCase) {
        outputDir = createTempDir()
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        outputDir.safeDeleteRecursively(force = true)
    }

    init {
        "GitRepo can download a given revision".config(tags = setOf(ExpensiveTag)) {
            val vcs = VcsInfo("GitRepo", REPO_URL, REPO_REV, path = REPO_MANIFEST)
            val pkg = Package.EMPTY.copy(vcsProcessed = vcs)
            val workingTree = GitRepo().download(pkg, outputDir)

            val spdxDir = File(outputDir, "spdx-tools")
            val expectedSpdxFiles = listOf(
                    ".git",
                    "Examples",
                    "Test",
                    "TestFiles",
                    "doc",
                    "resources",
                    "src"
            )

            val actualSpdxFiles = spdxDir.listFiles(FileFilter { it.isDirectory }).map { it.name }.sorted()

            val submodulesDir = File(outputDir, "submodules")
            val expectedSubmodulesFiles = listOf(
                    ".git",
                    "commons-text",
                    "test-data-npm"
            )

            val actualSubmodulesFiles = submodulesDir.listFiles(FileFilter { it.isDirectory }).map { it.name }.sorted()

            workingTree.isValid() shouldBe true
            workingTree.getInfo() shouldBe vcs

            workingTree.getPathToRoot(File(outputDir, "grpc/README.md")) shouldBe "grpc/README.md"
            workingTree.getPathToRoot(File(outputDir, "spdx-tools/TODO")) shouldBe "spdx-tools/TODO"

            actualSpdxFiles.joinToString("\n") shouldBe expectedSpdxFiles.joinToString("\n")
            actualSubmodulesFiles.joinToString("\n") shouldBe expectedSubmodulesFiles.joinToString("\n")
        }
    }
}
