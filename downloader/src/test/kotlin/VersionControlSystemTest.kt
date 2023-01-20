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
import java.io.IOException
import java.lang.UnsupportedOperationException

import org.ossreviewtoolkit.downloader.vcs.Git
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.common.CommandLineTool

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
                "v1.6.0"
            )
        }

        "add 'main' as a candidate for Git if otherwise 'master' is the only one" {
            val pkg = Package.EMPTY.copy(
                id = Identifier("NuGet::Microsoft.NETFramework.ReferenceAssemblies.net40:1.0.0-preview.2"),
                vcsProcessed = VcsInfo(
                    type = VcsType.GIT,
                    url = "https://github.com/Microsoft/dotnet.git",
                    revision = "master",
                    path = "releases/reference-assemblies"
                )
            )

            val workingTree = mockk<WorkingTree>()

            every {
                workingTree.guessRevisionName(any(), any())
            } throws IOException("No matching revision name found.")

            every { workingTree.listRemoteBranches() } returns listOf("main")
            every { workingTree.listRemoteTags() } returns emptyList()

            Git().getRevisionCandidates(workingTree, pkg, allowMovingRevisions = true) shouldBeSuccess listOf(
                "master",
                "main"
            )
        }
    }

    "isAvailable" should {
        "return true for implementations not based on command line tools" {
            val vcs = VersionControlSystemTestImpl(null)

            vcs.isAvailable() shouldBe true
        }

        "return true for command line tools available on the path" {
            val tool = mockk<CommandLineTool> {
                every { isInPath() } returns true
            }

            val vcs = VersionControlSystemTestImpl(tool)

            vcs.isAvailable() shouldBe true
        }

        "return false for command line tools not available on the path" {
            val tool = mockk<CommandLineTool> {
                every { isInPath() } returns false
            }

            val vcs = VersionControlSystemTestImpl(tool)

            vcs.isAvailable() shouldBe false
        }
    }
})

/**
 * A dummy implementation of [VersionControlSystem] used to test certain functionality.
 */
private class VersionControlSystemTestImpl(
    tool: CommandLineTool?,
    override val type: VcsType = VcsType.UNKNOWN,
    override val latestRevisionNames: List<String> = emptyList()
) : VersionControlSystem(tool) {
    override fun getVersion(): String = "0"

    override fun getDefaultBranchName(url: String): String? = null

    override fun getWorkingTree(vcsDirectory: File): WorkingTree = mockk()

    override fun isApplicableUrlInternal(vcsUrl: String): Boolean = false

    override fun initWorkingTree(targetDir: File, vcs: VcsInfo): WorkingTree = mockk()

    override fun updateWorkingTree(
        workingTree: WorkingTree,
        revision: String,
        path: String,
        recursive: Boolean
    ): Result<String> = Result.failure(UnsupportedOperationException("Unexpected invocation."))
}
