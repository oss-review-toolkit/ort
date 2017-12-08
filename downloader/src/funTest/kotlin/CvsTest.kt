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
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.specs.StringSpec

import java.io.File

private const val REPO_URL = ":pserver:anonymous@cvs.savannah.nongnu.org:/sources/cvs"
private const val REPO_SUBDIR = "ccvs/zlib"
private const val REPO_FILE = "$REPO_SUBDIR/zconf.h"
private const val REPO_SUBDIR_OMITTED = "diffutils"
private const val REPO_FILE_REVISION = "1.2"
private const val REPO_SNAPSHOT = "cvs1-12-13"
private const val REPO_FILE_REVISION_SNAPSHOT = "1.4"

class CvsTest : StringSpec() {
    private lateinit var outputDir: File

    // Required to make lateinit of outputDir work.
    override val oneInstancePerTest = false

    override fun interceptTestCase(context: TestCaseContext, test: () -> Unit) {
        outputDir = createTempDir()
        super.interceptTestCase(context, test)
        outputDir.deleteRecursively()
    }

    init {
        "Detected Cvs version is not empty" {
            val version = Cvs.getVersion()
            println("CVS version $version detected.")
            version shouldNotBe ""
        }

        "Cvs correctly validates repository urls" {
            val validRepo1 = ":pserver:anonymous@cvs.savannah.nongnu.org:/sources/cvs"
            val validRepo2 = ":ext:anonymous@cvs.savannah.nongnu.org:/sources/cvs"
            val validRepo3 = ":ext:some_user123@123.com:/path/to/repo"

            val invalidRepo1 = ":ppppp:anonymous@cvs.savannah.nongnu.org:/sources/cvs"
            val invalidRepo2 = "pserver:anonymous@cvs.savannah.nongnu.org:/sources/cvs"
            val invalidRepo3 = ":ext:some_user123@123.com:"

            Cvs.isApplicableUrl(validRepo1) shouldBe true
            Cvs.isApplicableUrl(validRepo2) shouldBe true
            Cvs.isApplicableUrl(validRepo3) shouldBe true

            Cvs.isApplicableUrl(invalidRepo1) shouldBe false
            Cvs.isApplicableUrl(invalidRepo2) shouldBe false
            Cvs.isApplicableUrl(invalidRepo3) shouldBe false
        }

        "Cvs checkouts whole CVS repository" {
            Cvs.download(REPO_URL, null, null, "", outputDir)
            Cvs.getWorkingDirectory(outputDir).isValid() shouldBe true
        }.config(tags = setOf(Expensive))

        "Cvs checkouts subpath from CVS repository" {
            Cvs.download(REPO_URL, null, REPO_SUBDIR, "", outputDir)
            Cvs.getWorkingDirectory(outputDir).isValid() shouldBe true
        }.config(tags = setOf(Expensive))

        "Cvs checkouts files with specified revision from CVS repository" {
            Cvs.download(REPO_URL, REPO_FILE_REVISION, REPO_SUBDIR, "", outputDir)
            val workingDirectory = Cvs.getWorkingDirectory(outputDir)
            workingDirectory.isValid() shouldBe true
            workingDirectory.workingDir.list().contains(REPO_SUBDIR_OMITTED) shouldBe false
            val f = File(outputDir, REPO_FILE)
            workingDirectory.getRevision(f) shouldBe REPO_FILE_REVISION
        }.config(tags = setOf(Expensive))

        "Cvs checkouts snapshot" {
            Cvs.download(REPO_URL, null, REPO_SUBDIR, REPO_SNAPSHOT, outputDir)
            Cvs.getWorkingDirectory(outputDir).isValid() shouldBe true
            Cvs.getWorkingDirectory(outputDir)
                    .getRevision(File(outputDir, REPO_FILE)) shouldBe REPO_FILE_REVISION_SNAPSHOT
        }.config(tags = setOf(Expensive))
    }
}
