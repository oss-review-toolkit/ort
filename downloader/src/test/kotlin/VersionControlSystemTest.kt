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

package org.ossreviewtoolkit.downloader

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk

import java.io.File

import org.ossreviewtoolkit.downloader.vcs.Git
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

class VersionControlSystemTest : WordSpec({
    val vcsRoot = File("..").absoluteFile.normalize()
    val relProjDir = File("src/test")
    val absProjDir = relProjDir.absoluteFile

    "For an absolute working directory, getPathToRoot()" should {
        val absVcsDir = VersionControlSystem.forDirectory(absProjDir)!!

        "work if given absolute paths" {
            absVcsDir.getPathToRoot(vcsRoot) shouldBe ""
            absVcsDir.getPathToRoot(vcsRoot.resolve("downloader/src")) shouldBe "downloader/src"
            absVcsDir.getPathToRoot(absProjDir.resolve("kotlin")) shouldBe "downloader/src/test/kotlin"
        }

        "work if given relative paths" {
            absVcsDir.getPathToRoot(File(".")) shouldBe "downloader"
            absVcsDir.getPathToRoot(File("..")) shouldBe ""
            absVcsDir.getPathToRoot(File("src/test/kotlin")) shouldBe "downloader/src/test/kotlin"
        }
    }

    "For a relative working directory, getPathToRoot()" should {
        val relVcsDir = VersionControlSystem.forDirectory(relProjDir)!!

        "work if given absolute paths" {
            relVcsDir.getPathToRoot(vcsRoot) shouldBe ""
            relVcsDir.getPathToRoot(vcsRoot.resolve("downloader/src")) shouldBe "downloader/src"
            relVcsDir.getPathToRoot(absProjDir.resolve("kotlin")) shouldBe "downloader/src/test/kotlin"
        }

        "work if given relative paths" {
            relVcsDir.getPathToRoot(relProjDir) shouldBe "downloader/src/test"
            relVcsDir.getPathToRoot(File("..")) shouldBe ""
            relVcsDir.getPathToRoot(File("src/test/kotlin")) shouldBe "downloader/src/test/kotlin"
        }
    }

    "getRevisionCandidates()" should {
        "prefer a matching tag name over a branch name from metadata" {
            val pkg = Package.EMPTY.copy(
                id = Identifier("Gem::google-cloud-core:1.6.0"),
                vcsProcessed = VcsInfo(
                    type = VcsType.GIT,
                    url = "https://github.com/googleapis/google-cloud-ruby.git",
                    revision = "master"
                )
            )

            val workingTree = mockk<WorkingTree>()

            every { workingTree.guessRevisionName(any(), any()) } returns "v1.6.0"

            Git().getRevisionCandidates(workingTree, pkg, allowMovingRevisions = true) shouldBeSuccess listOf(
                "v1.6.0",
                "master"
            )
        }
    }
})
