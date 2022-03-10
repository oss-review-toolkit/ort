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

import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.test.ExpensiveTag
import org.ossreviewtoolkit.utils.test.createTestTempDir

private const val REPO_URL = "https://github.com/oss-review-toolkit/ort-test-data-git-repo?manifest=manifest.xml"
private const val REPO_REV = "31588aa8f8555474e1c3c66a359ec99e4cd4b1fa"

class GitRepoDownloadFunTest : StringSpec() {
    private lateinit var outputDir: File

    override suspend fun beforeTest(testCase: TestCase) {
        outputDir = createTestTempDir()
    }

    init {
        "GitRepo can download a given revision".config(tags = setOf(ExpensiveTag)) {
            val vcs = VcsInfo(VcsType.GIT_REPO, REPO_URL, REPO_REV)
            val pkg = Package.EMPTY.copy(vcsProcessed = vcs)
            val workingTree = GitRepo().download(pkg, outputDir)

            val spdxDir = outputDir.resolve("spdx-tools")
            val expectedSpdxFiles = listOf(
                ".git",
                "Examples",
                "Test",
                "TestFiles",
                "doc",
                "resources",
                "src"
            )

            val actualSpdxFiles = spdxDir.walk().maxDepth(1).filter {
                it.isDirectory && it != spdxDir
            }.map {
                it.name
            }.sorted()

            val submodulesDir = outputDir.resolve("submodules")
            val expectedSubmodulesFiles = listOf(
                ".git",
                "commons-text",
                "test-data-npm"
            )

            val actualSubmodulesFiles = submodulesDir.walk().maxDepth(1).filter {
                it.isDirectory && it != submodulesDir
            }.map {
                it.name
            }.sorted()

            workingTree.isValid() shouldBe true
            workingTree.getInfo() shouldBe vcs

            workingTree.getPathToRoot(outputDir.resolve("grpc/README.md")) shouldBe "grpc/README.md"
            workingTree.getPathToRoot(outputDir.resolve("spdx-tools/TODO")) shouldBe "spdx-tools/TODO"

            actualSpdxFiles.joinToString("\n") shouldBe expectedSpdxFiles.joinToString("\n")
            actualSubmodulesFiles.joinToString("\n") shouldBe expectedSubmodulesFiles.joinToString("\n")
        }
    }
}
