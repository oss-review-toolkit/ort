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

package org.ossreviewtoolkit.downloader.vcs

import java.io.File

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.downloader.WorkingTree
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.common.CommandLineTool

typealias CvsFileRevisions = List<Pair<String, String>>

object CvsCommand : CommandLineTool {
    private val versionRegex = Regex("Concurrent Versions System \\(CVS\\) (?<version>[\\d.]+).+")

    override fun command(workingDir: File?) = "cvs"

    override fun transformVersion(output: String): String =
        versionRegex.matchEntire(output.lineSequence().first())?.let { match ->
            match.groups["version"]!!.value
        }.orEmpty()
}

class Cvs : VersionControlSystem(CvsCommand) {
    override val type = VcsType.CVS
    override val latestRevisionNames = emptyList<String>()

    override fun getVersion() = CvsCommand.getVersion(null)

    override fun getDefaultBranchName(url: String): String? = null

    override fun getWorkingTree(vcsDirectory: File) = CvsWorkingTree(vcsDirectory, type)

    override fun isApplicableUrlInternal(vcsUrl: String) = vcsUrl.matches(":(ext|pserver):[^@]+@.+".toRegex())

    override fun initWorkingTree(targetDir: File, vcs: VcsInfo): WorkingTree {
        // Create a "fake" checkout as described at https://stackoverflow.com/a/3448891/1127485.
        CvsCommand.run(targetDir, "-z3", "-d", vcs.url, "checkout", "-l", ".")

        return getWorkingTree(targetDir)
    }

    override fun updateWorkingTree(workingTree: WorkingTree, revision: String, path: String, recursive: Boolean) =
        runCatching {
            // Checkout the working tree of the desired revision.
            CvsCommand.run(workingTree.workingDir, "checkout", "-r", revision, path.takeUnless { it.isEmpty() } ?: ".")
        }.map {
            revision
        }
}
