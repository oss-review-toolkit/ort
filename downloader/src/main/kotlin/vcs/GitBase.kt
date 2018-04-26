/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

import com.here.ort.downloader.VersionControlSystem
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.getCommandVersion

import java.io.File
import java.util.regex.Pattern

abstract class GitBase : VersionControlSystem() {
    override val commandName = "git"
    override val movingRevisionNames = listOf("HEAD", "master")

    override fun getVersion(): String {
        val versionRegex = Pattern.compile("[Gg]it [Vv]ersion (?<version>[\\d.a-z-]+)(\\s.+)?")

        return getCommandVersion("git") {
            versionRegex.matcher(it.lineSequence().first()).let {
                if (it.matches()) {
                    it.group("version")
                } else {
                    ""
                }
            }
        }
    }

    open inner class GitWorkingTree(workingDir: File) : WorkingTree(workingDir) {
        override fun isValid(): Boolean {
            if (!workingDir.isDirectory) {
                return false
            }

            // Do not use runGitCommand() here as we do not require the command to succeed.
            val isInsideWorkTree = ProcessCapture(workingDir, "git", "rev-parse", "--is-inside-work-tree")
            return isInsideWorkTree.isSuccess() && isInsideWorkTree.stdout().trimEnd().toBoolean()
        }

        override fun isShallow(): Boolean {
            val dotGitDir = run(workingDir, "rev-parse", "--absolute-git-dir").stdout().trimEnd()
            return File(dotGitDir, "shallow").isFile
        }

        override fun getRemoteUrl() = run(workingDir, "remote", "get-url", "origin").stdout().trimEnd()

        override fun getRevision() = run(workingDir, "rev-parse", "HEAD").stdout().trimEnd()

        override fun getRootPath() =
                File(run(workingDir, "rev-parse", "--show-toplevel").stdout().trimEnd('\n', '/'))

        private fun listRemoteRefs(namespace: String): List<String> {
            val tags = run(workingDir, "ls-remote", "--refs", "origin", "refs/$namespace/*").stdout().trimEnd()
            return tags.lines().map {
                it.split('\t').last().removePrefix("refs/$namespace/")
            }
        }

        override fun listRemoteBranches() = listRemoteRefs("heads")

        override fun listRemoteTags() = listRemoteRefs("tags")
    }

    override fun getWorkingTree(vcsDirectory: File): WorkingTree = GitWorkingTree(vcsDirectory)
}
