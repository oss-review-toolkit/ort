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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.downloader.DownloadException
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.test.ExpensiveTag
import org.ossreviewtoolkit.utils.test.createTestTempDir

private const val PKG_VERSION = "0.4.1"

private const val REPO_URL = "https://github.com/jriecken/dependency-graph"
private const val REPO_REV = "8964880d9bac33f0a7f030a74c7c9299a8f117c8"
private const val REPO_PATH = "lib"

private const val REPO_REV_FOR_VERSION = "371b23f37da064687518bace268d607a92ecbe8f"
private const val REPO_PATH_FOR_VERSION = "specs"

class GitDownloadFunTest : StringSpec() {
    private val git = Git()
    private lateinit var outputDir: File

    override suspend fun beforeTest(testCase: TestCase) {
        outputDir = createTestTempDir()
    }

    init {
        "Git does not prompt for credentials for non-existing repositories" {
            val url = "https://github.com/oss-review-toolkit/foobar.git"
            val pkg = Package.EMPTY.copy(vcsProcessed = VcsInfo(VcsType.GIT, url, "master"))

            val exception = shouldThrow<DownloadException> {
                git.download(pkg, outputDir, allowMovingRevisions = true)
            }
            exception.message shouldBe "Git failed to download from URL '$url'."
        }

        "Git can download a given revision".config(tags = setOf(ExpensiveTag)) {
            val pkg = Package.EMPTY.copy(vcsProcessed = VcsInfo(VcsType.GIT, REPO_URL, REPO_REV))
            val expectedFiles = listOf(
                ".git",
                ".gitignore",
                "CHANGELOG.md",
                "LICENSE",
                "README.md",
                "lib",
                "package.json",
                "specs"
            )

            val workingTree = git.download(pkg, outputDir)
            val actualFiles = workingTree.workingDir.list().sorted()

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }

        "Git can download only a single path".config(tags = setOf(ExpensiveTag)) {
            val pkg = Package.EMPTY.copy(vcsProcessed = VcsInfo(VcsType.GIT, REPO_URL, REPO_REV, path = REPO_PATH))
            val expectedFiles = listOf(
                File("LICENSE"),
                File("README.md"),
                File(REPO_PATH, "dep_graph.js"),
                File(REPO_PATH, "index.d.ts")
            )

            val workingTree = git.download(pkg, outputDir)
            val actualFiles = workingTree.workingDir.walkBottomUp()
                .onEnter { it.name != ".git" }
                .filter { it.isFile }
                .map { it.relativeTo(outputDir) }
                .sortedBy { it.path }
                .toList()

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }

        "Git can download based on a version".config(tags = setOf(ExpensiveTag)) {
            val pkg = Package.EMPTY.copy(
                id = Identifier("Test:::$PKG_VERSION"),

                // Use a non-blank dummy revision to enforce multiple revision candidates being tried.
                vcsProcessed = VcsInfo(VcsType.GIT, REPO_URL, "dummy")
            )

            val workingTree = git.download(pkg, outputDir)

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV_FOR_VERSION
        }

        "Git can download only a single path based on a version".config(tags = setOf(ExpensiveTag)) {
            val pkg = Package.EMPTY.copy(
                id = Identifier("Test:::$PKG_VERSION"),

                // Use a non-blank dummy revision to enforce multiple revision candidates being tried.
                vcsProcessed = VcsInfo(VcsType.GIT, REPO_URL, "dummy", path = REPO_PATH_FOR_VERSION)
            )
            val expectedFiles = listOf(
                File("LICENSE"),
                File("README.md"),
                File(REPO_PATH_FOR_VERSION, "dep_graph_spec.js")
            )

            val workingTree = git.download(pkg, outputDir)
            val actualFiles = workingTree.workingDir.walkBottomUp()
                .onEnter { it.name != ".git" }
                .filter { it.isFile }
                .map { it.relativeTo(outputDir) }
                .sortedBy { it.path }
                .toList()

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV_FOR_VERSION
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }
    }
}
