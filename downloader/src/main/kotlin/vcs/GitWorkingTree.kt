/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import com.here.ort.downloader.WorkingTree
import com.here.ort.model.VcsInfo
import com.here.ort.utils.ProcessCapture

import java.io.File

open class GitWorkingTree(workingDir: File, private val gitBase: GitBase) : WorkingTree(workingDir, gitBase.type) {
    override fun isValid(): Boolean {
        if (!workingDir.isDirectory) {
            return false
        }

        // Do not use runGitCommand() here as we do not require the command to succeed.
        val isInsideWorkTree = ProcessCapture(workingDir, "git", "rev-parse", "--is-inside-work-tree")
        return isInsideWorkTree.isSuccess && isInsideWorkTree.stdout.trimEnd().toBoolean()
    }

    override fun isShallow(): Boolean {
        val dotGitDir = gitBase.run(workingDir, "rev-parse", "--absolute-git-dir").stdout.trimEnd()
        return File(dotGitDir, "shallow").isFile
    }

    override fun getNested(): Map<String, VcsInfo> {
        val root = getRootPath()
        val paths = gitBase.run(root, "submodule", "--quiet", "foreach", "--recursive", "echo \$displaypath").stdout
            .lines().filter { it.isNotBlank() }

        return paths.associateWith { GitWorkingTree(root.resolve(it), gitBase).getInfo() }
    }

    override fun getRemoteUrl() = gitBase.run(workingDir, "remote", "get-url", getFirstRemote()).stdout.trimEnd()

    override fun getRevision() = gitBase.run(workingDir, "rev-parse", "HEAD").stdout.trimEnd()

    override fun getRootPath() =
        File(gitBase.run(workingDir, "rev-parse", "--show-toplevel").stdout.trimEnd('\n', '/'))

    private fun listRemoteRefs(namespace: String): List<String> {
        val tags = gitBase.run(workingDir, "ls-remote", "--refs", getFirstRemote(), "refs/$namespace/*")
            .stdout.trimEnd()
        return tags.lines().map {
            it.split('\t').last().removePrefix("refs/$namespace/")
        }
    }

    override fun listRemoteBranches() = listRemoteRefs("heads")

    override fun listRemoteTags() = listRemoteRefs("tags")

    private fun getFirstRemote() = gitBase.run(workingDir, "remote", "show", "-n").stdout.lineSequence().first()
}
