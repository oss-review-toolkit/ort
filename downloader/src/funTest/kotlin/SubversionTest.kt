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

import com.here.ort.utils.Expensive

import io.kotlintest.TestCaseContext
import io.kotlintest.matchers.beGreaterThan
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

private const val REPO_URL = "https://svn.code.sf.net/p/pythonqt/code"
private const val REPO_REV = "460"
private const val REPO_SUBDIR = "extensions"
private const val REPO_SUBDIR_OMITTED = "examples"
private const val REPO_VERSION = "1.0"
private const val REPO_VERSION_REV = "33"

class SubversionTest : StringSpec() {
    private lateinit var outputDir: File

    // Required to make lateinit of outputDir work.
    override val oneInstancePerTest = false

    override fun interceptTestCase(context: TestCaseContext, test: () -> Unit) {
        outputDir = createTempDir()
        try {
            super.interceptTestCase(context, test)
        } finally {
            outputDir.deleteRecursively()
        }
    }

    init {
        "Subversion can download single revision" {
            val downloadedRev = Subversion.download(REPO_URL, REPO_REV, null, "", outputDir)
            downloadedRev shouldBe REPO_REV
        }.config(tags = setOf(Expensive))

        "Subversion can download sub path" {
            Subversion.download(REPO_URL, null, REPO_SUBDIR, "", outputDir)

            val outputDirList = Subversion.getWorkingTree(outputDir).workingDir.list()
            outputDirList.indexOf(REPO_SUBDIR) should beGreaterThan(-1)
            outputDirList.indexOf(REPO_SUBDIR_OMITTED) shouldBe -1
        }.config(tags = setOf(Expensive))

        "Subversion can download version" {
            val downloadedRev = Subversion.download(REPO_URL, null, null, REPO_VERSION, outputDir)
            downloadedRev shouldBe REPO_VERSION_REV
        }.config(tags = setOf(Expensive))

        "Subversion can download entire repo" {
            Subversion.download(REPO_URL, null, null, "", outputDir)
            val outputDirList = Subversion.getWorkingTree(outputDir).workingDir.list()
            outputDirList.indexOf("trunk") should beGreaterThan(-1)
            outputDirList.indexOf("tags") should beGreaterThan(-1)
            outputDirList.indexOf("branches") should beGreaterThan(-1)
        }.config(tags = setOf(Expensive))
    }
}
