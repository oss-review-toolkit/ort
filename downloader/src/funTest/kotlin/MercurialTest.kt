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
import com.here.ort.utils.Expensive

import io.kotlintest.TestCaseContext
import io.kotlintest.matchers.beGreaterThan
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

private const val REPO_URL = "https://bitbucket.org/creaceed/mercurial-xcode-plugin"
private const val REPO_REV = "02098fc8bdac"
private const val REPO_SUBDIR = "Classes"
private const val REPO_SUBDIR_OMITTED = "Resources"
private const val REPO_VERSION = "1.1"
private const val REPO_VERSION_REV = "562fed42b4f3"

class MercurialTest : StringSpec() {
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
        "Mercurial can download entire repo" {
            Mercurial.download(VcsInfo("Mercurial", REPO_URL, "", ""), "", outputDir)
            Mercurial.getWorkingTree(outputDir).getProvider() shouldBe "Mercurial"
        }.config(tags = setOf(Expensive))

        "Mercurial can download single revision" {
            val workingTree = Mercurial.download(VcsInfo("Mercurial", REPO_URL, REPO_REV, ""), "", outputDir)
            workingTree.getRevision() shouldBe REPO_REV
        }.config(tags = setOf(Expensive))

        "Mercurial can download sub path" {
            Mercurial.download(VcsInfo("Mercurial", REPO_URL, "", REPO_SUBDIR), "", outputDir)

            val outputDirList = Mercurial.getWorkingTree(outputDir).workingDir.list()
            outputDirList.indexOf(REPO_SUBDIR) should beGreaterThan(-1)
            outputDirList.indexOf(REPO_SUBDIR_OMITTED) shouldBe -1
        }.config(tags = setOf(Expensive), enabled = Mercurial.isAtLeastVersion("4.3"))

        "Mercurial can download version" {
            val workingTree = Mercurial.download(VcsInfo("Mercurial", REPO_URL, "", ""), REPO_VERSION, outputDir)
            workingTree.getRevision() shouldBe REPO_VERSION_REV
        }.config(tags = setOf(Expensive))
    }
}
