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
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.Package
import com.here.ort.utils.OS
import com.here.ort.utils.log
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.getCommandVersion
import com.here.ort.utils.safeMkdirs

import java.io.File
import java.io.IOException
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
            return isInsideWorkTree.exitValue() == 0 && isInsideWorkTree.stdout().trimEnd().toBoolean()
        }

        override fun isShallow(): Boolean {
            val dotGitDir = run(workingDir, "rev-parse", "--absolute-git-dir").stdout().trimEnd()
            return File(dotGitDir, "shallow").isFile
        }

        override fun getRemoteUrl() =
                run(workingDir, "remote", "get-url", "origin").stdout().trimEnd()

        override fun getRevision() =
                run(workingDir, "rev-parse", "HEAD").stdout().trimEnd()

        override fun getRootPath() =
                run(workingDir, "rev-parse", "--show-toplevel").stdout().trimEnd('\n', '/')

        override fun listRemoteTags(): List<String> {
            val tags = run(workingDir, "ls-remote", "--refs", "origin", "refs/tags/*").stdout().trimEnd()
            return tags.lines().map {
                it.split('\t').last().removePrefix("refs/tags/")
            }
        }
    }

    override fun getWorkingTree(vcsDirectory: File) : WorkingTree = GitWorkingTree(vcsDirectory)
}

object Git : GitBase() {
    // TODO: Make this configurable.
    private const val HISTORY_DEPTH = 10

    override val aliases = listOf("git")

    override fun isApplicableUrl(vcsUrl: String) = ProcessCapture("git", "ls-remote", vcsUrl).exitValue() == 0

    override fun download(pkg: Package, targetDir: File, allowMovingRevisions: Boolean,
                          recursive: Boolean): WorkingTree {
        log.info { "Using $this version ${getVersion()}." }

        try {
            return createWorkingTree(pkg, targetDir, allowMovingRevisions).also {
                if (recursive && File(targetDir, ".gitmodules").isFile) {
                    run(targetDir, "submodule", "update", "--init", "--recursive")
                }
            }
        } catch (e: IOException) {
            if (com.here.ort.utils.printStackTrace) {
                e.printStackTrace()
            }

            throw DownloadException("$this failed to download from URL '${pkg.vcsProcessed.url}'.", e)
        }
    }

    private fun createWorkingTree(pkg: Package, targetDir: File, allowMovingRevisions: Boolean): WorkingTree {
        // Do not use "git clone" to have more control over what is being fetched.
        run(targetDir, "init")
        run(targetDir, "remote", "add", "origin", pkg.vcsProcessed.url)

        if (OS.isWindows) {
            run(targetDir, "config", "core.longpaths", "true")
        }

        if (pkg.vcsProcessed.path.isNotBlank()) {
            log.info { "Configuring Git to do sparse checkout of path '${pkg.vcsProcessed.path}'." }
            run(targetDir, "config", "core.sparseCheckout", "true")
            val gitInfoDir = File(targetDir, ".git/info").apply { safeMkdirs() }
            File(gitInfoDir, "sparse-checkout").writeText(pkg.vcsProcessed.path)
        }

        val workingTree = getWorkingTree(targetDir)

        val revisionCandidates = mutableListOf<String>()

        if (allowMovingRevisions || isFixedRevision(pkg.vcsProcessed.revision)) {
            revisionCandidates.add(pkg.vcsProcessed.revision)
        } else {
            log.warn {
                "No valid revision specified. Other possible candidates might cause the downloaded source code " +
                        "to not match the package version."
            }
        }

        log.info { "Trying to guess a $this revision for version '${pkg.id.version}' to fall back to." }
        workingTree.guessRevisionName(pkg.id.name, pkg.id.version).also { revision ->
            if (revision.isNotBlank()) {
                revisionCandidates.add(revision)
                log.info { "Found $this revision '$revision' for version '${pkg.id.version}'." }
            } else {
                log.info { "No $this revision for version '${pkg.id.version}' found." }
            }
        }

        val revision = revisionCandidates.firstOrNull()
                ?: throw IOException("Unable to determine a revision to checkout.")

        // To safe network bandwidth, first try to only fetch exactly the revision we want.
        try {
            log.info { "Trying to fetch only revision '$revision' with depth limited to $HISTORY_DEPTH." }
            run(targetDir, "fetch", "--depth", HISTORY_DEPTH.toString(), "origin", revision)
            run(targetDir, "checkout", "FETCH_HEAD")
            return workingTree
        } catch (e: IOException) {
            if (com.here.ort.utils.printStackTrace) {
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
            run(targetDir, "fetch", "--depth", HISTORY_DEPTH.toString(), "--tags", "origin")
            run(targetDir, "checkout", revision)
            return workingTree
        } catch (e: IOException) {
            if (com.here.ort.utils.printStackTrace) {
                e.printStackTrace()
            }

            log.warn {
                "Could not fetch with only a depth of $HISTORY_DEPTH: ${e.message}\n" +
                        "Falling back to fetching everything."
            }
        }

        // Fall back to fetching everything.
        log.info { "Trying to fetch everything including tags." }

        if (workingTree.isShallow()) {
            run(targetDir, "fetch", "--unshallow", "--tags", "origin")
        } else {
            run(targetDir, "fetch", "--tags", "origin")
        }

        revisionCandidates.find { candidate ->
            try {
                run(targetDir, "checkout", candidate)
                true
            } catch (e: IOException) {
                if (com.here.ort.utils.printStackTrace) {
                    e.printStackTrace()
                }

                log.info { "Failed to checkout revision '$candidate'. Trying next candidate, if any." }

                false
            }
        } ?: throw IOException("Unable to determine a revision to checkout.")

        return workingTree
    }
}
