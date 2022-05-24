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
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import java.io.File

import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.common.unpack
import org.ossreviewtoolkit.utils.ort.ortDataDirectory
import org.ossreviewtoolkit.utils.test.createSpecTempDir

class CvsWorkingTreeFunTest : StringSpec({
    val cvs = Cvs()
    val zipContentDir = createSpecTempDir()

    beforeSpec {
        val zipFile = File("src/funTest/assets/jhove-2019-12-11-cvs.zip")
        println("Extracting '$zipFile' to '$zipContentDir'...")
        zipFile.unpack(zipContentDir)
    }

    "Detected CVS version is not empty" {
        val version = cvs.getVersion()
        println("CVS version $version detected.")
        version shouldNotBe ""
    }

    "CVS detects non-working-trees" {
        cvs.getWorkingTree(ortDataDirectory).isValid() shouldBe false
    }

    "CVS correctly detects URLs to remote repositories" {
        cvs.isApplicableUrl(":pserver:anonymous@a.cvs.sourceforge.net:/cvsroot/tyrex") shouldBe true
        cvs.isApplicableUrl(":ext:jrandom@cvs.foobar.com:/usr/local/cvs") shouldBe true
        cvs.isApplicableUrl("https://svn.code.sf.net/p/grepwin/code/") shouldBe false
    }

    "Detected CVS working tree information is correct".config(enabled = false /* Failing due to SF issues. */) {
        val workingTree = cvs.getWorkingTree(zipContentDir)

        workingTree.isValid() shouldBe true
        workingTree.getInfo() shouldBe VcsInfo(
            type = VcsType.CVS,
            url = ":pserver:anonymous@a.cvs.sourceforge.net:/cvsroot/jhove",
            revision = "449addc0d9e0ee7be48bfaa06f99a6f23cd3bae0",
            path = ""
        )
        workingTree.getNested() should beEmpty()
        workingTree.getRootPath() shouldBe zipContentDir
        workingTree.getPathToRoot(zipContentDir.resolve("lib")) shouldBe "lib"
    }

    "CVS correctly lists remote branches".config(enabled = false /* Failing due to SF issues. */) {
        val expectedBranches = listOf(
            "JHOVE_1_7",
            "branch_lpeer",
            "junit_tests",
            "pdfprofile_noncompliance_analyzer"
        )

        val workingTree = cvs.getWorkingTree(zipContentDir)
        workingTree.listRemoteBranches().joinToString("\n") shouldBe expectedBranches.joinToString("\n")
    }

    "CVS correctly lists remote tags".config(enabled = false /* Failing due to SF issues. */) {
        val expectedTags = listOf(
            "JHOVE_1_1",
            "JHOVE_1_11",
            "JHOVE_1_8",
            "JHOVE_1_9",
            "Root_JHOVE_1_7",
            "lpeer_0"
        )

        val workingTree = cvs.getWorkingTree(zipContentDir)
        workingTree.listRemoteTags().joinToString("\n") shouldBe expectedTags.joinToString("\n")
    }
})
