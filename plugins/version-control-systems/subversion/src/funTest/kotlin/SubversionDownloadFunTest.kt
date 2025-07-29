/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.versioncontrolsystems.subversion

import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.common.VCS_DIRECTORIES
import org.ossreviewtoolkit.utils.common.div

private const val REPO_URL = "https://svn.code.sf.net/p/sendmessage/code"
private const val REPO_REV = "115"
private const val REPO_PATH = "trunk/tools"
private const val REPO_TAG = "tags/SendMessage-1.0.2"
private const val REPO_REV_FOR_TAG = "37"
private const val REPO_VERSION = "1.0.1"
private const val REPO_REV_FOR_VERSION = "30"
private const val REPO_PATH_FOR_VERSION = "src/resources"

class SubversionDownloadFunTest : StringSpec() {
    private val svn = Subversion()
    private lateinit var outputDir: File

    override suspend fun beforeTest(testCase: TestCase) {
        outputDir = tempdir()
    }

    init {
        "Subversion can download a given revision" {
            val pkg = Package.EMPTY.copy(vcsProcessed = VcsInfo(VcsType.SUBVERSION, REPO_URL, REPO_REV))
            val expectedFiles = listOf(
                ".svn",
                "branches",
                "tags",
                "trunk",
                "wiki"
            )

            val workingTree = svn.download(pkg, outputDir)
            val actualFiles = workingTree.getRootPath().walk().maxDepth(1).mapNotNullTo(mutableListOf()) {
                it.toRelativeString(workingTree.getRootPath()).ifEmpty { null }
            }.sorted()

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }

        "Subversion can download only a single path" {
            val pkg = Package.EMPTY.copy(
                vcsProcessed = VcsInfo(VcsType.SUBVERSION, REPO_URL, REPO_REV, path = REPO_PATH)
            )
            val expectedFiles = listOf(
                "$REPO_PATH/checkyear.js",
                "$REPO_PATH/coverity.bat"
            )

            val workingTree = svn.download(pkg, outputDir)
            val actualFiles = workingTree.getRootPath().walk()
                .onEnter { it.name !in VCS_DIRECTORIES }
                .filter { it.isFile }
                .mapTo(mutableListOf()) { it.toRelativeString(workingTree.getRootPath()) }
                .sorted()

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }

        "Subversion can download a given tag" {
            val pkg = Package.EMPTY.copy(vcsProcessed = VcsInfo(VcsType.SUBVERSION, REPO_URL, REPO_TAG))
            val expectedFiles = listOf(
                ".svn",
                "SendMessage.proj",
                "SendMessage.sln",
                "src",
                "version.proj",
                "web"
            )

            val workingTree = svn.download(pkg, outputDir)
            val actualFiles = workingTree.getRootPath().walk().maxDepth(1).mapNotNullTo(mutableListOf()) {
                it.toRelativeString(workingTree.getRootPath()).ifEmpty { null }
            }.sorted()

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV_FOR_TAG
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }

        "Subversion can download based on a version" {
            val pkg = Package.EMPTY.copy(
                id = Identifier("Test:::$REPO_VERSION"),
                vcsProcessed = VcsInfo(VcsType.SUBVERSION, REPO_URL, "")
            )

            val workingTree = svn.download(pkg, outputDir)

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV_FOR_VERSION
        }

        "Subversion can download only a single path based on a version" {
            val pkg = Package.EMPTY.copy(
                id = Identifier("Test:::$REPO_VERSION"),
                vcsProcessed = VcsInfo(VcsType.SUBVERSION, REPO_URL, "", path = REPO_PATH_FOR_VERSION)
            )
            val expectedFiles = listOf(
                "SendMessage.ico",
                "searchw.cur",
                "searchw.ico",
                "windowmessages.xml"
            )

            val workingTree = svn.download(pkg, outputDir)
            val pathForVersion = workingTree.getRootPath() / REPO_PATH_FOR_VERSION
            val actualFiles = pathForVersion.walk().maxDepth(1).mapNotNullTo(mutableListOf()) {
                it.toRelativeString(pathForVersion).ifEmpty { null }
            }.sorted()

            workingTree.isValid() shouldBe true
            workingTree.getRevision() shouldBe REPO_REV_FOR_VERSION
            actualFiles.joinToString("\n") shouldBe expectedFiles.joinToString("\n")
        }
    }
}
