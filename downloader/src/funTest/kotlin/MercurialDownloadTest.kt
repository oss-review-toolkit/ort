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

import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.VcsInfo
import com.here.ort.utils.ExpensiveTag
import com.here.ort.utils.safeDeleteRecursively

import io.kotlintest.Description
import io.kotlintest.TestResult
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

private const val REPO_URL = "https://bitbucket.org/creaceed/mercurial-xcode-plugin"
private const val REPO_REV = "02098fc8bdaca4739ec52cbcb8ed51654eacee25"
private const val REPO_PATH = "Classes"
private const val REPO_VERSION = "1.1"
private const val REPO_REV_FOR_VERSION = "562fed42b4f3dceaacf6f1051963c865c0241e28"
private const val REPO_PATH_FOR_VERSION = "Resources"

class MercurialDownloadTest : StringSpec() {
    private lateinit var outputDir: File

    override fun beforeTest(description: Description) {
        outputDir = createTempDir()
    }

    override fun afterTest(description: Description, result: TestResult) {
        outputDir.safeDeleteRecursively()
    }

    init {
        "Mercurial can download a given revision".config(enabled = Mercurial.isInPath(), tags = setOf(ExpensiveTag)) {
            val pkg = Package.EMPTY.copy(vcsProcessed = VcsInfo("Mercurial", REPO_URL, REPO_REV))
            val expectedFiles = listOf(
                    ".hg",
                    ".hgsub",
                    ".hgsubstate",
                    "Classes",
                    "LICENCE.md",
                    "MercurialPlugin.xcodeproj",
                    "README.md",
                    "Resources",
                    "Script"
            )

            val workingTree = Mercurial.download(pkg, outputDir)
            val actualFiles = workingTree.workingDir.list().sorted()

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }

        "Mercurial can download only a single path"
                .config(enabled = Mercurial.isInPath() && Mercurial.isAtLeastVersion("4.3"),
                        tags = setOf(ExpensiveTag)) {
            val pkg = Package.EMPTY.copy(vcsProcessed = VcsInfo("Mercurial", REPO_URL, REPO_REV, path = REPO_PATH))
            val expectedFiles = listOf(
                    File(".hgsub"), // We always get these configuration files, if present.
                    File(".hgsubstate"),
                    File(REPO_PATH, "MercurialPlugin.h"),
                    File(REPO_PATH, "MercurialPlugin.m"),
                    File("LICENCE.md"),
                    File("Script", "README"), // As a submodule, "Script" is always included.
                    File("Script", "git.py"),
                    File("Script", "gpl-2.0.txt"),
                    File("Script", "install_bridge.sh"),
                    File("Script", "sniff.py"),
                    File("Script", "uninstall_bridge.sh")
            )

            val workingTree = Mercurial.download(pkg, outputDir)
            val actualFiles = workingTree.workingDir.walkBottomUp()
                    .onEnter { it.name != ".hg" }
                    .filter { it.isFile }
                    .map { it.relativeTo(outputDir) }
                    .sortedBy { it.path }

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }

        "Mercurial can download based on a version".config(enabled = Mercurial.isInPath(), tags = setOf(ExpensiveTag)) {
            val pkg = Package.EMPTY.copy(
                    id = Identifier.EMPTY.copy(version = REPO_VERSION),
                    vcsProcessed = VcsInfo("Mercurial", REPO_URL, "")
            )

            val workingTree = Mercurial.download(pkg, outputDir)

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV_FOR_VERSION
        }

        "Mercurial can download only a single path based on a version"
                .config(enabled = Mercurial.isInPath() && Mercurial.isAtLeastVersion("4.3"),
                        tags = setOf(ExpensiveTag)) {
            val pkg = Package.EMPTY.copy(
                    id = Identifier.EMPTY.copy(version = REPO_VERSION),
                    vcsProcessed = VcsInfo("Mercurial", REPO_URL, "", path = REPO_PATH_FOR_VERSION)
            )
            val expectedFiles = listOf(
                    File(".hgsub"), // We always get these configuration files, if present.
                    File(".hgsubstate"),
                    File("LICENCE.md"),
                    File(REPO_PATH_FOR_VERSION, "Info.plist"),
                    File(REPO_PATH_FOR_VERSION, "icon.icns"),
                    File(REPO_PATH_FOR_VERSION, "icon_blank.icns"),
                    File("Script", "README"), // As a submodule, "Script" is always included.
                    File("Script", "git.py"),
                    File("Script", "gpl-2.0.txt"),
                    File("Script", "install_bridge.sh"),
                    File("Script", "sniff.py"),
                    File("Script", "uninstall_bridge.sh")
            )

            val workingTree = Mercurial.download(pkg, outputDir)
            val actualFiles = workingTree.workingDir.walkBottomUp()
                    .onEnter { it.name != ".hg" }
                    .filter { it.isFile }
                    .map { it.relativeTo(outputDir) }
                    .sortedBy { it.path }

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV_FOR_VERSION
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }
    }
}
