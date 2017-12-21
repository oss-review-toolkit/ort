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

import com.here.ort.model.VcsInfo
import com.here.ort.utils.Expensive

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.specs.StringSpec
import io.kotlintest.TestCaseContext

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
        "Detected CVS version is not empty" {
            val version = Cvs.getVersion()
            println("CVS version $version detected.")
            version shouldNotBe ""
        }

        "CVS correctly validates repository urls" {
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

        "CVS can check out whole CVS repository" {
            Cvs.download(VcsInfo("Cvs", REPO_URL, "", ""), "", outputDir)
            Cvs.getWorkingTree(outputDir).isValid() shouldBe true
        }.config(tags = setOf(Expensive))

        "CVS can check out subpath from CVS repository" {
            Cvs.download(VcsInfo("Cvs", REPO_URL, "", REPO_SUBDIR), "", outputDir)
            Cvs.getWorkingTree(outputDir).isValid() shouldBe true
        }.config(tags = setOf(Expensive))

        "CVS can check out files with specified revision from CVS repository" {
            Cvs.download(VcsInfo("Cvs", REPO_URL, REPO_FILE_REVISION, REPO_SUBDIR), "", outputDir)
            val workingDirectory = Cvs.getWorkingTree(outputDir)
            workingDirectory.isValid() shouldBe true
            workingDirectory.workingDir.list().contains(REPO_SUBDIR_OMITTED) shouldBe false
            val f = File(outputDir, REPO_FILE)
            workingDirectory.getRevision(f) shouldBe REPO_FILE_REVISION
        }.config(tags = setOf(Expensive))

        "CVS can check out snapshot" {
            Cvs.download(VcsInfo("Cvs", REPO_URL, "", REPO_SUBDIR), REPO_SNAPSHOT, outputDir)
            Cvs.getWorkingTree(outputDir).isValid() shouldBe true
            Cvs.getWorkingTree(outputDir)
                    .getRevision(File(outputDir, REPO_FILE)) shouldBe REPO_FILE_REVISION_SNAPSHOT
        }.config(tags = setOf(Expensive))

        "CVS can check out snapshot" {
            Cvs.download(VcsInfo("Cvs", REPO_URL, REPO_FILE_REVISION, REPO_SUBDIR), "", outputDir)
            Cvs.getWorkingTree(outputDir)
                    .listRemoteTags() shouldBe listOf("ZLIB", "ZLIB-1-0-3", "ZLIB-1-0-4", "ZLIB-1-1-3", "ZLIB-1-1-4",
                    "ZLIB-1-2-1", "ZLIB-1-2-2", "ZLIB-1-2-3", "before-history-lock", "ccvs-autotest", "ccvs-autotest-2",
                    "config-HistoryLogPath", "config-HistoryLogPath-root", "cvs1-10", "cvs1-10-1", "cvs1-10-2",
                    "cvs1-10-3", "cvs1-10-4", "cvs1-10-5", "cvs1-10-6", "cvs1-10-7", "cvs1-10-8", "cvs1-11",
                    "cvs1-11-0-0-pre", "cvs1-11-0-2", "cvs1-11-0-3", "cvs1-11-0-5", "cvs1-11-1", "cvs1-11-10",
                    "cvs1-11-11", "cvs1-11-12", "cvs1-11-13", "cvs1-11-14", "cvs1-11-15", "cvs1-11-16", "cvs1-11-17",
                    "cvs1-11-18", "cvs1-11-19", "cvs1-11-1p1", "cvs1-11-2", "cvs1-11-2-branch", "cvs1-11-20",
                    "cvs1-11-21", "cvs1-11-22", "cvs1-11-23", "cvs1-11-3", "cvs1-11-4", "cvs1-11-5", "cvs1-11-5-branch",
                    "cvs1-11-6", "cvs1-11-7", "cvs1-11-8", "cvs1-11-9", "cvs1-11-x-branch",
                    "cvs1-11-x-branch-last-merge", "cvs1-11-x-branch-last-merge-tbd", "cvs1-12-1", "cvs1-12-10",
                    "cvs1-12-11", "cvs1-12-12", "cvs1-12-13", "cvs1-12-13-win-fix", "cvs1-12-13a", "cvs1-12-2",
                    "cvs1-12-3", "cvs1-12-4", "cvs1-12-5", "cvs1-12-6", "cvs1-12-7", "cvs1-12-8", "cvs1-12-9",
                    "cvs1-8-7", "cvs1-8-85", "cvs1-8-86", "cvs1-8-87", "cvs1-9", "cvs1-9-10", "cvs1-9-12",
                    "cvs1-9-14", "cvs1-9-16", "cvs1-9-18", "cvs1-9-2", "cvs1-9-20", "cvs1-9-22", "cvs1-9-24",
                    "cvs1-9-26", "cvs1-9-27", "cvs1-9-28", "cvs1-9-29", "cvs1-9-30", "cvs1-9-4", "cvs1-9-6",
                    "cvs1-9-8", "entries-cache", "entries-cache-root", "greg-1_8_86n-base", "greg-1_8_86n-br",
                    "greg-1_8_86n-merge", "multiroot_merged_980831", "multiroot_merged_980901", "nc_multiroot",
                    "nc_multiroot_bp", "new-recursion-root", "newtags", "newtags-root", "newtags2",
                    "post-pserver-empty-password-changes", "postautomakeconversion", "postautomakeconversion2",
                    "pre-pserver-empty-password-changes", "preautomakeconversion", "signed-commit2-root",
                    "signed-commits", "signed-commits-seg-1-complete", "signed-commits-seg-2-complete",
                    "signed-commits-seg-3-complete", "signed-commits2", "signed-commits3", "signed-commits4",
                    "tmpstart", "writeproxy", "writeproxy2")
        }.config(tags = setOf(Expensive))
    }
}
