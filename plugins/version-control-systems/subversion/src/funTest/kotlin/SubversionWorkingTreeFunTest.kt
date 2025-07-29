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
import io.kotest.engine.spec.tempdir
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.maps.beEmpty as beEmptyMap
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beEmpty

import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.extractResource
import org.ossreviewtoolkit.utils.common.unpack
import org.ossreviewtoolkit.utils.ort.ortDataDirectory

class SubversionWorkingTreeFunTest : StringSpec({
    val svn = Subversion()
    val zipContentDir = tempdir()

    beforeSpec {
        val zipFile = extractResource("/docutils-2018-01-03-svn-trunk.zip", tempfile(suffix = ".zip"))
        zipFile.unpack(zipContentDir)
    }

    "Detected Subversion version is not empty" {
        val version = svn.getVersion()

        version shouldNot beEmpty()
    }

    "Subversion detects non-working-trees" {
        svn.getWorkingTree(ortDataDirectory).isValid() shouldBe false
    }

    "Subversion correctly detects URLs to remote repositories" {
        svn.isApplicableUrl("https://svn.code.sf.net/p/grepwin/code/") shouldBe true
        svn.isApplicableUrl("https://bitbucket.org/facebook/lz4revlog") shouldBe false
    }

    "Detected Subversion working tree information is correct" {
        val workingTree = svn.getWorkingTree(zipContentDir)

        workingTree.isValid() shouldBe true
        workingTree.getInfo() shouldBe VcsInfo(
            type = VcsType.SUBVERSION,
            url = "https://svn.code.sf.net/p/docutils/code/trunk/docutils",
            revision = "8207",
            path = ""
        )
        workingTree.getNested() should beEmptyMap()
        workingTree.getRootPath() shouldBe zipContentDir
        workingTree.getPathToRoot(zipContentDir / "docutils") shouldBe "docutils"
    }

    "Subversion correctly lists remote branches" {
        val remoteBranches = svn.getWorkingTree(zipContentDir).listRemoteBranches()

        remoteBranches shouldContainAll expectedRemoteBranches
    }

    "Subversion correctly lists remote tags" {
        val remoteTags = svn.getWorkingTree(zipContentDir).listRemoteTags()

        remoteTags shouldContainAll expectedRemoteTags
    }
})

private val expectedRemoteBranches = listOf(
    "address-rendering",
    "index-bug",
    "lossless-rst-writer",
    "nesting",
    "plugins",
    "rel-0.15",
    "subdocs"
).map { "branches/$it" }

private val expectedRemoteTags = listOf(
    "docutils-0.10",
    "docutils-0.11",
    "docutils-0.12",
    "docutils-0.13.1",
    "docutils-0.14",
    "docutils-0.14.0a",
    "docutils-0.14a0",
    "docutils-0.14rc1",
    "docutils-0.14rc2",
    "docutils-0.15",
    "docutils-0.16",
    "docutils-0.17",
    "docutils-0.17.1",
    "docutils-0.18",
    "docutils-0.18.1",
    "docutils-0.19",
    "docutils-0.20",
    "docutils-0.3.7",
    "docutils-0.3.9",
    "docutils-0.4",
    "docutils-0.5",
    "docutils-0.6",
    "docutils-0.7",
    "docutils-0.8",
    "docutils-0.8.1",
    "docutils-0.9",
    "docutils-0.9.1",
    "initial",
    "merged_to_nesting",
    "prest-0.3.10",
    "prest-0.3.11",
    "start"
).map { "tags/$it" }
