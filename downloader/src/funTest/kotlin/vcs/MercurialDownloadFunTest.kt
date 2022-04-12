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

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.test.ExpensiveTag
import org.ossreviewtoolkit.utils.test.createTestTempDir

private const val PKG_VERSION = "v1.0.0"

private const val REPO_URL = "https://hg.sr.ht/~breakfastquay/bqfft"
private const val REPO_REV = "e1c392f85e973225ece81cf8d74287b3a4992dea"
private const val REPO_PATH = "test"

private const val REPO_REV_FOR_VERSION = "a766fe47501b185bc46cffc210735304e28f2189"
private const val REPO_PATH_FOR_VERSION = "build"

class MercurialDownloadFunTest : StringSpec() {
    private val hg = Mercurial()
    private lateinit var outputDir: File

    override suspend fun beforeTest(testCase: TestCase) {
        outputDir = createTestTempDir()
    }

    init {
        "Mercurial can download a given revision".config(tags = setOf(ExpensiveTag)) {
            val pkg = Package.EMPTY.copy(vcsProcessed = VcsInfo(VcsType.MERCURIAL, REPO_URL, REPO_REV))
            val expectedFiles = listOf(
                ".hg",
                ".hgignore",
                ".hgtags",
                ".travis.yml",
                "COPYING",
                "Makefile",
                "README.md",
                "bqfft",
                "build",
                "src",
                "test"
            )

            val workingTree = hg.download(pkg, outputDir)
            val actualFiles = workingTree.workingDir.list().sorted()

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }

        "Mercurial can download only a single path"
            .config(enabled = hg.isAtLeastVersion("4.3"), tags = setOf(ExpensiveTag)) {
                val pkg = Package.EMPTY.copy(
                    vcsProcessed = VcsInfo(VcsType.MERCURIAL, REPO_URL, REPO_REV, path = REPO_PATH)
                )
                val expectedFiles = listOf(
                    ".hgignore",
                    ".hgtags",
                    "COPYING",
                    "README.md",
                    "$REPO_PATH/TestFFT.cpp",
                    "$REPO_PATH/timings.cpp"
                )

                val workingTree = hg.download(pkg, outputDir)
                val actualFiles = workingTree.workingDir.walkBottomUp()
                    .onEnter { it.name != ".hg" }
                    .filter { it.isFile }
                    .map { it.relativeTo(outputDir) }
                    .sortedBy { it.path }
                    .toList()

                workingTree.isValid() shouldBe true
                workingTree.getRevision() shouldBe REPO_REV
                actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
            }

        "Mercurial can download based on a version".config(tags = setOf(ExpensiveTag)) {
            val pkg = Package.EMPTY.copy(
                id = Identifier("Test:::$PKG_VERSION"),

                // Use a non-blank dummy revision to enforce multiple revision candidates being tried.
                vcsProcessed = VcsInfo(VcsType.MERCURIAL, REPO_URL, "dummy")
            )

            val workingTree = hg.download(pkg, outputDir)

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV_FOR_VERSION
        }

        "Mercurial can download only a single path based on a version"
            .config(enabled = hg.isAtLeastVersion("4.3"), tags = setOf(ExpensiveTag)) {
                val pkg = Package.EMPTY.copy(
                    id = Identifier("Test:::$PKG_VERSION"),

                    // Use a non-blank dummy revision to enforce multiple revision candidates being tried.
                    vcsProcessed = VcsInfo(VcsType.MERCURIAL, REPO_URL, "dummy", path = REPO_PATH_FOR_VERSION)
                )
                val expectedFiles = listOf(
                    ".hgignore",
                    "COPYING",
                    "README.md",
                    "build/Makefile.inc",
                    "build/Makefile.linux.fftw",
                    "build/Makefile.linux.ipp",
                    "build/Makefile.osx",
                    "build/run-platform-tests.sh"
                )

                val workingTree = hg.download(pkg, outputDir)
                val actualFiles = workingTree.workingDir.walkBottomUp()
                    .onEnter { it.name != ".hg" }
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
