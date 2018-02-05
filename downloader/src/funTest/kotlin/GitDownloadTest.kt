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

import com.here.ort.model.VcsInfo
import com.here.ort.utils.ExpensiveTag
import com.here.ort.utils.safeDeleteRecursively

import io.kotlintest.TestCaseContext
import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

private const val REPO_URL = "https://github.com/jriecken/dependency-graph"
private const val REPO_REV = "8964880d9bac33f0a7f030a74c7c9299a8f117c8"
private const val REPO_PATH = "lib"
private const val REPO_VERSION = "0.4.1"
private const val REPO_REV_FOR_VERSION = "371b23f37da064687518bace268d607a92ecbe8f"
private const val REPO_PATH_FOR_VERSION = "specs"

class GitDownloadTest : StringSpec() {
    private lateinit var outputDir: File

    // Required to make lateinit of outputDir work.
    override val oneInstancePerTest = false

    override fun interceptTestCase(context: TestCaseContext, test: () -> Unit) {
        outputDir = createTempDir()
        try {
            super.interceptTestCase(context, test)
        } finally {
            outputDir.safeDeleteRecursively()
        }
    }

    init {
        "Git can download a given revision" {
            val vcs = VcsInfo("Git", REPO_URL, REPO_REV, "")
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

            val workingTree = Git.download(vcs, "", outputDir)
            val actualFiles = workingTree.workingDir.list().sorted()

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }.config(tags = setOf(ExpensiveTag))

        "Git can download only a single path" {
            val vcs = VcsInfo("Git", REPO_URL, REPO_REV, REPO_PATH)
            val expectedFiles = listOf(
                    File(REPO_PATH, "dep_graph.js"),
                    File(REPO_PATH, "index.d.ts")
            )

            val workingTree = Git.download(vcs, "", outputDir)
            val actualFiles = workingTree.workingDir.walkBottomUp()
                    .onEnter { it.name != ".git" }
                    .filter { it.isFile }
                    .map { it.relativeTo(outputDir) }
                    .sortedBy { it.path }

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }.config(tags = setOf(ExpensiveTag))

        "Git can download based on a version" {
            val vcs = VcsInfo("Git", REPO_URL, "", "")

            val workingTree = Git.download(vcs, REPO_VERSION, outputDir)

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV_FOR_VERSION
        }.config(tags = setOf(ExpensiveTag))

        "Git can download only a single path based on a version" {
            val vcs = VcsInfo("Git", REPO_URL, "", REPO_PATH_FOR_VERSION)
            val expectedFiles = listOf(
                    File(REPO_PATH_FOR_VERSION, "dep_graph_spec.js")
            )

            val workingTree = Git.download(vcs, REPO_VERSION, outputDir)
            val actualFiles = workingTree.workingDir.walkBottomUp()
                    .onEnter { it.name != ".git" }
                    .filter { it.isFile }
                    .map { it.relativeTo(outputDir) }
                    .sortedBy { it.path }

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV_FOR_VERSION
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }.config(tags = setOf(ExpensiveTag))
    }
}
