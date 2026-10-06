/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.versioncontrolsystems.mercurial

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.maps.beEmpty as beEmptyMap
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beEmpty

import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.extractResource
import org.ossreviewtoolkit.utils.common.unpackZip

@Tags("RequiresExternalTool")
class MercurialWorkingTreeFunTest : StringSpec({
    val hg = Mercurial()
    val zipContentDir by lazy {
        tempdir().also {
            extractResource("/hgflow-2026-10-06.zip", tempfile(suffix = ".zip")).unpackZip(it)
        } / "hgflow"
    }

    "Detected Mercurial version is not empty" {
        val version = hg.getVersion()

        version shouldNot beEmpty()
    }

    "Mercurial detects non-working-trees" {
        hg.getWorkingTree(tempdir()).isValid() shouldBe false
    }

    "Mercurial correctly detects URLs to remote repositories" {
        hg.isApplicableUrl("https://hg.sr.ht/~duangle/paniq_legacy") shouldBe true
        hg.isApplicableUrl("https://bitbucket.org/yevster/spdxtraxample.git") shouldBe false
    }

    "Detected Mercurial working tree information is correct" {
        val workingTree = hg.getWorkingTree(zipContentDir)

        workingTree.isValid() shouldBe true
        workingTree.getInfo() shouldBe VcsInfo(
            type = VcsType.MERCURIAL,
            url = "https://hg.sr.ht/~wu/hgflow",
            revision = "4f95ea47701fea6ff8d54a5c42ef87f3bb21fc71",
            path = ""
        )
        workingTree.getNested() should beEmptyMap()
        workingTree.getRootPath() shouldBe zipContentDir
        workingTree.getPathToRoot(zipContentDir / "tests") shouldBe "tests"
    }

    "Mercurial correctly lists remote branches" {
        val workingTree = hg.getWorkingTree(zipContentDir)

        workingTree.listRemoteBranches() should containExactlyInAnyOrder(
            "default",
            "develop",
            "hotfix/0.9.8.7"
        )
    }

    "Mercurial correctly lists remote tags" {
        val workingTree = hg.getWorkingTree(zipContentDir)

        workingTree.listRemoteTags() should containExactlyInAnyOrder(
            "tag_support",
            "v0.1",
            "v0.2",
            "v0.3",
            "v0.7",
            "v0.7.1",
            "v0.7.2",
            "v0.8.0",
            "v0.8.1",
            "v0.8.2",
            "v0.8.3",
            "v0.9.0",
            "v0.9.1",
            "v0.9.2",
            "v0.9.3",
            "v0.9.4",
            "v0.9.5",
            "v0.9.6",
            "v0.9.7",
            "v0.9.8",
            "v0.9.8.1",
            "v0.9.8.2",
            "v0.9.8.3",
            "v0.9.8.4",
            "v0.9.8.5",
            "v0.9.8.6",
            "vmaster_push_pull"
        )
    }
})
