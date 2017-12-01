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
import io.kotlintest.specs.StringSpec

import java.io.File

private const val REPO_URL = "https://bitbucket.org/creaceed/mercurial-xcode-plugin"

class MercurialTest : StringSpec() {
    private lateinit var outputDir: File

    override val oneInstancePerTest: Boolean
        get() = false

    override fun interceptTestCase(context: TestCaseContext, test: () -> Unit) {
        outputDir = createTempDir()
        super.interceptTestCase(context, test)
        outputDir.deleteRecursively()
    }

    init {
        "Mercurial can download entire repo" {
            Mercurial.download(REPO_URL, null, null, "", outputDir)
            Mercurial.getWorkingDirectory(outputDir).getProvider() shouldBe "Mercurial"
        }.config(tags = setOf(Expensive))

        "Mercurial can download single revision" {
            val revision = "02098fc8bdac"
            val downloadedRev = Mercurial.download(REPO_URL, revision, null, "", outputDir)
            downloadedRev shouldBe revision
        }.config(tags = setOf(Expensive))

        "Mercurial can download sub path" {
            val subdir = "Classes"
            val notCheckoutSubDir = "Resources"
            Mercurial.download(REPO_URL, null, subdir, "", outputDir)
            val outputDirList = Mercurial.getWorkingDirectory(outputDir).workingDir.list()
            outputDirList.indexOf(subdir) should beGreaterThan(-1)
            outputDirList.indexOf(notCheckoutSubDir) shouldBe -1
        }.config(tags = setOf(Expensive), enabled = Mercurial.isAtLeastVersion("4.3"))

        "Mercurial can download version" {
            val version = "1.1"
            val revisionForVersion = "562fed42b4f3"
            val downloadedRev = Mercurial.download(REPO_URL, null, null, version, outputDir)
            downloadedRev shouldBe revisionForVersion
        }.config(tags = setOf(Expensive))
    }
}
