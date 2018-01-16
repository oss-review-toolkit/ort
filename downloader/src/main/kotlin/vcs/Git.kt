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

import ch.frankel.slf4k.*

import com.here.ort.downloader.DownloadException
import com.here.ort.downloader.Main
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.VcsInfo
import com.here.ort.utils.OS
import com.here.ort.utils.log
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.getCommandVersion
import com.here.ort.utils.safeMkdirs

import java.io.File
import java.io.IOException

abstract class GitBase : VersionControlSystem() {
    override fun getVersion(): String {
        val gitVersionRegex = Regex("[Gg]it [Vv]ersion (?<version>[\\d.a-z-]+)(\\s.+)?")

        return getCommandVersion("git") {
            gitVersionRegex.matchEntire(it.lineSequence().first())?.groups?.get("version")?.value ?: ""
        }
    }

    override fun getWorkingTree(vcsDirectory: File) =
            object : WorkingTree(vcsDirectory) {
                override fun isValid(): Boolean {
                    if (!workingDir.isDirectory) {
                        return false
                    }

                    // Do not use runGitCommand() here as we do not require the command to succeed.
                    val isInsideWorkTree = ProcessCapture(workingDir, "git", "rev-parse", "--is-inside-work-tree")
                    return isInsideWorkTree.exitValue() == 0 && isInsideWorkTree.stdout().trimEnd().toBoolean()
                }

                override fun isShallow(): Boolean {
                    val dotGitDir = runGitCommand(workingDir, "rev-parse", "--absolute-git-dir").stdout().trimEnd()
                    return File(dotGitDir, "shallow").isFile
                }

                override fun getRemoteUrl() =
                        runGitCommand(workingDir, "remote", "get-url", "origin").stdout().trimEnd()

                override fun getRevision() =
                        runGitCommand(workingDir, "rev-parse", "HEAD").stdout().trimEnd()

                override fun getRootPath() =
                        runGitCommand(workingDir, "rev-parse", "--show-toplevel").stdout().trimEnd('\n', '/')

                override fun listRemoteTags(): List<String> {
                    val tags = runGitCommand(workingDir, "ls-remote", "--refs", "origin", "refs/tags/*")
                            .stdout().trimEnd()
                    return tags.lines().map {
                        it.split('\t').last().removePrefix("refs/tags/")
                    }
                }
            }

    protected fun runGitCommand(workingDir: File, vararg args: String) =
            ProcessCapture(workingDir, "git", *args).requireSuccess()
}

object Git : GitBase() {
    // TODO: Make this configurable.
    private const val HISTORY_DEPTH = 10

    override fun isApplicableProvider(vcsProvider: String) = vcsProvider.equals("git", true)

    override fun isApplicableUrl(vcsUrl: String) = ProcessCapture("git", "ls-remote", vcsUrl).exitValue() == 0

    override fun download(vcs: VcsInfo, version: String, targetDir: File): WorkingTree {
        log.info { "Using $this version ${getVersion()}." }

        try {
            // Do not use "git clone" to have more control over what is being fetched.
            runGitCommand(targetDir, "init")
            runGitCommand(targetDir, "remote", "add", "origin", vcs.url)

            if (OS.isWindows) {
                runGitCommand(targetDir, "config", "core.longpaths", "true")
            }

            if (vcs.path.isNotBlank()) {
                log.info { "Configuring Git to do sparse checkout of path '${vcs.path}'." }
                runGitCommand(targetDir, "config", "core.sparseCheckout", "true")
                val gitInfoDir = File(targetDir, ".git/info").apply { safeMkdirs() }
                File(gitInfoDir, "sparse-checkout").writeText(vcs.path)
            }

            val workingTree = getWorkingTree(targetDir)

            val revision = if (vcs.revision.isNotBlank()) {
                vcs.revision
            } else {
                log.info { "Trying to guess $this revision for version '$version'." }
                workingTree.guessRevisionNameForVersion(version).also { revision ->
                    if (revision.isBlank()) {
                        throw IOException("Unable to determine a revision to checkout.")
                    }

                    log.info { "Found $this revision '$revision' for version '$version'." }
                }
            }

            // To safe network bandwidth, first try to only fetch exactly the revision we want.
            try {
                log.info { "Trying to fetch only revision '$revision' with depth limited to $HISTORY_DEPTH." }
                runGitCommand(targetDir, "fetch", "--depth", HISTORY_DEPTH.toString(), "origin", revision)
                runGitCommand(targetDir, "checkout", "FETCH_HEAD")
                return workingTree
            } catch (e: IOException) {
                if (Main.stacktrace) {
                    e.printStackTrace()
                }

                log.warn {
                    "Could not fetch only revision '$revision': ${e.message}\n" +
                            "Falling back to fetching all refs."
                }
            }

            // Fall back to fetching all refs with limited depth of history.
            try {
                log.info { "Trying to fetch all refs with depth limited to $HISTORY_DEPTH." }
                runGitCommand(targetDir, "fetch", "--depth", HISTORY_DEPTH.toString(), "--tags", "origin")
                runGitCommand(targetDir, "checkout", revision)
                return workingTree
            } catch (e: IOException) {
                if (Main.stacktrace) {
                    e.printStackTrace()
                }

                log.warn {
                    "Could not fetch with only a depth of $HISTORY_DEPTH: ${e.message}\n" +
                            "Falling back to fetching everything."
                }
            }

            // Fall back to fetching everything.
            log.info { "Trying to fetch everything including tags." }
            runGitCommand(targetDir, "fetch", "--tags", "origin")
            runGitCommand(targetDir, "checkout", revision)
            return workingTree
        } catch (e: IOException) {
            if (Main.stacktrace) {
                e.printStackTrace()
            }

            throw DownloadException("$this failed to download from URL '${vcs.url}'.", e)
        }
    }
}
