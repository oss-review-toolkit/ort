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

import io.kotlintest.TestCaseContext
import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

private const val REPO_URL = ":pserver:anonymous@xmlenc.cvs.sourceforge.net:/cvsroot/xmlenc"
private const val REPO_REV = "RELEASE_0_52"
private const val REPO_PATH = "xmlenc/src/history"
private const val REPO_VERSION = "0.52"
private const val REPO_PATH_FOR_VERSION = "xmlenc/build.xml"

class CvsDownloadTest : StringSpec() {
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
        "CVS can download a given revision" {
            val pkg = Package.EMPTY.copy(vcsProcessed = VcsInfo("CVS", REPO_URL, REPO_REV, ""))
            val expectedFiles = listOf(
                    "CVS",
                    "xmlenc"
            )

            val workingTree = Cvs.download(pkg, outputDir)
            val actualFiles = workingTree.workingDir.list().sorted()

            // Use forward slashes also on Windows as the CVS client comes from MSYS2.
            val buildXmlFile = "xmlenc/build.xml"
            val buildXmlStatus = Cvs.run(outputDir, "status", buildXmlFile)

            workingTree.isValid() shouldBe true
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")

            // Only tag "RELEASE_0_52" has revision 1.159 of "xmlenc/build.xml".
            buildXmlStatus.stdout().contains("Working revision:\t1.159") shouldBe true
        }.config(tags = setOf(ExpensiveTag), enabled = false)

        "CVS can download only a single path" {
            val pkg = Package.EMPTY.copy(vcsProcessed = VcsInfo("CVS", REPO_URL, REPO_REV, REPO_PATH))
            val expectedFiles = listOf(
                    File(REPO_PATH, "changes.xml")
            )

            val workingTree = Cvs.download(pkg, outputDir)
            val actualFiles = workingTree.workingDir.walkBottomUp()
                    .onEnter { it.name != "CVS" }
                    .filter { it.isFile }
                    .map { it.relativeTo(outputDir) }
                    .sortedBy { it.path }

            workingTree.isValid() shouldBe true
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }.config(tags = setOf(ExpensiveTag), enabled = false)

        "CVS can download based on a version" {
            val pkg = Package.EMPTY.copy(
                    id = Identifier.EMPTY.copy(version = REPO_VERSION),
                    vcsProcessed = VcsInfo("CVS", REPO_URL, "", "")
            )

            val workingTree = Cvs.download(pkg, outputDir)

            // Use forward slashes also on Windows as the CVS client comes from MSYS2.
            val buildXmlFile = "xmlenc/build.xml"
            val buildXmlStatus = Cvs.run(outputDir, "status", buildXmlFile)

            workingTree.isValid() shouldBe true

            // Only tag "RELEASE_0_52" has revision 1.159 of "xmlenc/build.xml".
            buildXmlStatus.stdout().contains("Working revision:\t1.159") shouldBe true
        }.config(tags = setOf(ExpensiveTag), enabled = false)

        "CVS can download only a single path based on a version" {
            val pkg = Package.EMPTY.copy(
                    id = Identifier.EMPTY.copy(version = REPO_VERSION),
                    vcsProcessed = VcsInfo("CVS", REPO_URL, "", REPO_PATH_FOR_VERSION)
            )
            val expectedFiles = listOf(
                    File(REPO_PATH_FOR_VERSION)
            )

            val workingTree = Cvs.download(pkg, outputDir)
            val actualFiles = workingTree.workingDir.walkBottomUp()
                    .onEnter { it.name != "CVS" }
                    .filter { it.isFile }
                    .map { it.relativeTo(outputDir) }
                    .sortedBy { it.path }

            workingTree.isValid() shouldBe true
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }.config(tags = setOf(ExpensiveTag), enabled = false)
    }
}
