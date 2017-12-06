/*
 * Copyright (c) 2017 HERE Europe B.V.
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

import com.here.ort.downloader.Expensive
import io.kotlintest.TestCaseContext
import io.kotlintest.matchers.beGreaterThan
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe

import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.specs.StringSpec
import java.io.File

private const val REPO_URL = "https://svn.code.sf.net/p/pythonqt/code"

class SubversionTest : StringSpec() {
    private lateinit var outputDir: File

    // Required to make lateinit of outputDir work.
    override val oneInstancePerTest = false

    override fun interceptTestCase(context: TestCaseContext, test: () -> Unit) {
        outputDir = createTempDir()
        super.interceptTestCase(context, test)
        outputDir.deleteRecursively()
    }

    init {
        "Detected Subversion version is not empty" {
            val version = Subversion.getVersion()
            version shouldNotBe ""
        }

        "Subversion can download single revision" {
            val revision = "460"
            val downloadedRev = Subversion.download(REPO_URL, revision, null, "", outputDir)
            downloadedRev shouldBe revision
        }.config(tags = setOf(Expensive))

        "Subversion can download sub path" {
            val subdir = "extensions"
            val notCheckoutSubDir = "examples"
            Subversion.download(REPO_URL, null, subdir, "", outputDir)

            val outputDirList = Subversion.getWorkingDirectory(outputDir).workingDir.list()
            outputDirList.indexOf(subdir) should beGreaterThan(-1)
            outputDirList.indexOf(notCheckoutSubDir) shouldBe -1
        }.config(tags = setOf(Expensive))

        "Subversion can download version" {
            val version = "1.0"
            val revisionForVersion = "33"
            val downloadedRev = Subversion.download(REPO_URL, null, null, version, outputDir)
            downloadedRev shouldBe revisionForVersion
        }.config(tags = setOf(Expensive))

        "Subversion can download entire repo" {
            Subversion.download(REPO_URL, null, null, "", outputDir)
            val outputDirList = Subversion.getWorkingDirectory(outputDir).workingDir.list()
            outputDirList.indexOf("trunk") should beGreaterThan(-1)
            outputDirList.indexOf("tags") should beGreaterThan(-1)
            outputDirList.indexOf("branches") should beGreaterThan(-1)
        }.config(tags = setOf(Expensive))
    }
}
