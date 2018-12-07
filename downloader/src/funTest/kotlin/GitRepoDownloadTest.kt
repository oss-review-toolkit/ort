/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

import io.kotlintest.Description
import io.kotlintest.TestResult
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File
import java.io.FileFilter

private const val REPO_URL = "https://github.com/heremaps/oss-review-toolkit-test-data"
private const val REPO_REV = "1c0b86e578349f38acd43de354f815370112a213"
private const val REPO_MANIFEST = "git-repo/manifest.xml"

class GitRepoDownloadTest : StringSpec() {
    private lateinit var outputDir: File

    override fun beforeTest(description: Description) {
        outputDir = createTempDir()
    }

    override fun afterTest(description: Description, result: TestResult) {
        outputDir.safeDeleteRecursively(true)
    }

    init {
        "GitRepo can download a given revision".config(tags = setOf(ExpensiveTag)) {
            val vcs = VcsInfo("GitRepo", REPO_URL, REPO_REV, path = REPO_MANIFEST)
            val pkg = Package.EMPTY.copy(vcsProcessed = vcs)
            val grpcDir = File(outputDir, "grpc")
            val expectedFiles = listOf(
                    ".git",
                    ".github",
                    ".vscode",
                    "bazel",
                    "cmake",
                    "doc",
                    "etc",
                    "examples",
                    "include",
                    "src",
                    "summerofcode",
                    "templates",
                    "test",
                    "third_party",
                    "tools",
                    "vsprojects"
            ).map { File(grpcDir, it) }

            val workingTree = GitRepo().download(pkg, outputDir)
            val actualFiles = grpcDir.listFiles(FileFilter {
                it.isDirectory
            }).sorted()

            workingTree.isValid() shouldBe true
            workingTree.getInfo() shouldBe vcs
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")

            workingTree.getPathToRoot(File(outputDir, "docker/Dockerfile")) shouldBe "docker/Dockerfile"
            workingTree.getPathToRoot(File(outputDir, "grpc/README.md")) shouldBe "grpc/README.md"
        }
    }
}
