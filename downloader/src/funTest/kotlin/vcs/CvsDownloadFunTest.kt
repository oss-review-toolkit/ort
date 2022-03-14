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
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.contain

import java.io.File

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.test.ExpensiveTag
import org.ossreviewtoolkit.utils.test.createTestTempDir

private const val REPO_URL = ":pserver:anonymous@a.cvs.sourceforge.net:/cvsroot/xmlenc"
private const val REPO_REV = "RELEASE_0_52"
private const val REPO_PATH = "xmlenc/src/history"
private const val REPO_VERSION = "0.52"
private const val REPO_PATH_FOR_VERSION = "xmlenc/build.xml"

class CvsDownloadFunTest : StringSpec() {
    private val cvs = Cvs()
    private lateinit var outputDir: File

    override suspend fun beforeTest(testCase: TestCase) {
        outputDir = createTestTempDir()
    }

    init {
        "CVS can download a given revision".config(tags = setOf(ExpensiveTag), enabled = false) {
            val pkg = Package.EMPTY.copy(vcsProcessed = VcsInfo(VcsType.CVS, REPO_URL, REPO_REV))
            val expectedFiles = listOf(
                "CVS",
                "xmlenc"
            )

            val workingTree = cvs.download(pkg, outputDir)
            val actualFiles = workingTree.workingDir.list().sorted()

            // Use forward slashes also on Windows as the CVS client comes from MSYS2.
            val buildXmlFile = "xmlenc/build.xml"
            val buildXmlStatus = cvs.run(outputDir, "status", buildXmlFile)

            workingTree.isValid() shouldBe true
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")

            // Only tag "RELEASE_0_52" has revision 1.159 of "xmlenc/build.xml".
            buildXmlStatus.stdout should contain("Working revision:\t1.159")
        }

        "CVS can download only a single path".config(tags = setOf(ExpensiveTag), enabled = false) {
            val pkg = Package.EMPTY.copy(vcsProcessed = VcsInfo(VcsType.CVS, REPO_URL, REPO_REV, path = REPO_PATH))
            val expectedFiles = listOf(
                File(REPO_PATH, "changes.xml")
            )

            val workingTree = cvs.download(pkg, outputDir)
            val actualFiles = workingTree.workingDir.walkBottomUp()
                .onEnter { it.name != "CVS" }
                .filter { it.isFile }
                .map { it.relativeTo(outputDir) }
                .sortedBy { it.path }

            workingTree.isValid() shouldBe true
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }

        "CVS can download based on a version".config(tags = setOf(ExpensiveTag), enabled = false) {
            val pkg = Package.EMPTY.copy(
                id = Identifier("Test:::$REPO_VERSION"),
                vcsProcessed = VcsInfo(VcsType.CVS, REPO_URL, "")
            )

            val workingTree = cvs.download(pkg, outputDir)

            // Use forward slashes also on Windows as the CVS client comes from MSYS2.
            val buildXmlFile = "xmlenc/build.xml"
            val buildXmlStatus = cvs.run(outputDir, "status", buildXmlFile)

            workingTree.isValid() shouldBe true

            // Only tag "RELEASE_0_52" has revision 1.159 of "xmlenc/build.xml".
            buildXmlStatus.stdout should contain("Working revision:\t1.159")
        }

        "CVS can download only a single path based on a version".config(tags = setOf(ExpensiveTag), enabled = false) {
            val pkg = Package.EMPTY.copy(
                id = Identifier("Test:::$REPO_VERSION"),
                vcsProcessed = VcsInfo(VcsType.CVS, REPO_URL, "", path = REPO_PATH_FOR_VERSION)
            )
            val expectedFiles = listOf(
                File(REPO_PATH_FOR_VERSION)
            )

            val workingTree = cvs.download(pkg, outputDir)
            val actualFiles = workingTree.workingDir.walkBottomUp()
                .onEnter { it.name != "CVS" }
                .filter { it.isFile }
                .map { it.relativeTo(outputDir) }
                .sortedBy { it.path }

            workingTree.isValid() shouldBe true
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }
    }
}
