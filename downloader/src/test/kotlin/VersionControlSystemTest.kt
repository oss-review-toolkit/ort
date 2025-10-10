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

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk

import java.io.IOException

import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

class VersionControlSystemTest : WordSpec({
    "getRevisionCandidates()" should {
        "prefer a matching tag name over a branch name from metadata" {
            val pkg = Package.EMPTY.copy(
                vcsProcessed = VcsInfo(
                    type = VcsType.GIT,
                    url = "",
                    revision = "master"
                )
            )

            val workingTree = mockk<WorkingTree>()
            val vcs = spyk<VersionControlSystem>()

            every { workingTree.guessRevisionName(any(), any()) } returns "v1.6.0"

            vcs.getRevisionCandidates(workingTree, pkg, allowMovingRevisions = true) shouldBeSuccess listOf(
                "v1.6.0"
            )
        }

        "add 'main' as a candidate for Git if otherwise 'master' is the only one" {
            val pkg = Package.EMPTY.copy(
                vcsProcessed = VcsInfo(
                    type = VcsType.GIT,
                    url = "",
                    revision = "master"
                )
            )

            val workingTree = mockk<WorkingTree>()
            val vcs = spyk<VersionControlSystem> {
                every { type } returns VcsType.GIT
                every { isFixedRevision(any(), "master") } returns Result.success(false)
                every { isFixedRevision(any(), "main") } returns Result.success(false)
            }

            every {
                workingTree.guessRevisionName(any(), any())
            } throws IOException("No matching revision name found.")

            every { workingTree.listRemoteBranches() } returns listOf("main")
            every { workingTree.listRemoteTags() } returns emptyList()

            vcs.getRevisionCandidates(workingTree, pkg, allowMovingRevisions = true) shouldBeSuccess listOf(
                "master",
                "main"
            )
        }
    }
})
