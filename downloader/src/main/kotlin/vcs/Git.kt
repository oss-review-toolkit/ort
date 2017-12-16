/*
 * Copyright (c) 2017 HERE Europe B.V.
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
import com.here.ort.utils.log
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.getCommandVersion
import com.here.ort.utils.safeMkdirs

import java.io.File
import java.io.IOException

abstract class GitBase : VersionControlSystem() {
    override fun getVersion(): String {
        val gitVersionRegex = Regex("git version (?<version>[\\d.a-z]+)")

        return getCommandVersion("git") {
            gitVersionRegex.matchEntire(it.lineSequence().first())?.groups?.get("version")?.value ?: ""
        }
    }

    override fun getWorkingDirectory(vcsDirectory: File) =
            object : WorkingDirectory(vcsDirectory) {
                override fun isValid(): Boolean {
                    if (!workingDir.isDirectory) {
                        return false
                    }

                    val isInsideWorkTree = ProcessCapture(workingDir, "git", "rev-parse", "--is-inside-work-tree")
                    return isInsideWorkTree.exitValue() == 0 && isInsideWorkTree.stdout().trimEnd().toBoolean()
                }

                override fun getRemoteUrl() =
                    runGitCommand(workingDir, "remote", "get-url", "origin").stdout().trimEnd()

                override fun getRevision() =
                    runGitCommand(workingDir, "rev-parse", "HEAD").stdout().trimEnd()

                override fun getRootPath(path: File) =
                    runGitCommand(workingDir, "rev-parse", "--show-toplevel").stdout().trimEnd('\n', '/')

                override fun getPathToRoot(path: File): String {
                    val absolutePath = if (path.isAbsolute || path == workingDir) {
                        path
                    } else {
                        workingDir.resolve(path)
                    }

                    return runGitCommand(absolutePath, "rev-parse", "--show-prefix").stdout().trimEnd('\n', '/')
                }
            }

    protected fun runGitCommand(workingDir: File, vararg args: String) =
        ProcessCapture(workingDir, "git", *args).requireSuccess()
}

object Git : GitBase() {
    override fun isApplicableProvider(vcsProvider: String) = vcsProvider.equals("git", true)

    override fun isApplicableUrl(vcsUrl: String) = ProcessCapture("git", "ls-remote", vcsUrl).exitValue() == 0

    /**
     * Clones the Git repository using the native Git command.
     *
     * @param vcsPath If this parameter is not null or empty, the working tree is deleted and the path is selectively
     *                checked out using 'git checkout HEAD -- vcsPath'.
     *
     * @throws DownloadException In case the download failed.
     */
    @Suppress("ComplexMethod")
    override fun download(vcsUrl: String, vcsRevision: String?, vcsPath: String?, version: String, targetDir: File)
            : String {
        log.info { "Using $this version ${getVersion()}." }

        try {
            // Do not use "git clone" to have more control over what is being fetched.
            runGitCommand(targetDir, "init")
            runGitCommand(targetDir, "remote", "add", "origin", vcsUrl)

            val workingDir = getWorkingDirectory(targetDir)

            if (vcsPath != null && vcsPath.isNotEmpty()) {
                log.info { "Configuring Git to do sparse checkout of path '$vcsPath'." }
                runGitCommand(targetDir, "config", "core.sparseCheckout", "true")
                val gitInfoDir = File(targetDir, ".git/info").apply { safeMkdirs() }
                File(gitInfoDir, "sparse-checkout").writeText(vcsPath)
            }

            val committish = getCommittish(vcsRevision, version, targetDir)

            // Do safe network bandwidth, first try to only fetch exactly the committish we want.
            try {
                runGitCommand(targetDir, "fetch", "origin", committish)
                runGitCommand(targetDir, "checkout", "FETCH_HEAD")
                return workingDir.getRevision()
            } catch (e: IOException) {
                if (Main.stacktrace) {
                    e.printStackTrace()
                }

                log.warn {
                    "Could not fetch only '$committish': ${e.message}\n" +
                            "Falling back to fetching everything."
                }
            }

            // Fall back to fetching everything.
            log.info { "Fetching origin and trying to checkout '$committish'." }
            runGitCommand(targetDir, "fetch", "origin")

            try {
                runGitCommand(targetDir, "checkout", committish)
                return workingDir.getRevision()
            } catch (e: IOException) {
                if (Main.stacktrace) {
                    e.printStackTrace()
                }

                log.warn { "Could not checkout '$committish': ${e.message}" }
            }

            throw IOException("Unable to determine a committish to checkout.")
        } catch (e: IOException) {
            if (Main.stacktrace) {
                e.printStackTrace()
            }

            log.error { "Could not clone $vcsUrl: ${e.message}" }
            throw DownloadException("Could not clone $vcsUrl.", e)
        }
    }

    private fun getCommittish(vcsRevision: String?, packageVersion: String, targetDir: File): String {
        if (vcsRevision != null && vcsRevision.isNotEmpty()) {
            return vcsRevision
        }
        // If we don't have a revision, see if the package version matches a tag
        if (packageVersion.isBlank()) {
            log.warn { "No source revision and no package version. Using HEAD." }
            return "HEAD"
        }

        log.info { "Trying to guess tag for version '$packageVersion'." }
        val tag = runGitCommand(targetDir, "ls-remote", "--tags", "origin")
                .stdout()
                .lineSequence()
                .map { it.split("\t").last() }
                .find { it.endsWith(packageVersion) || it.endsWith(packageVersion.replace('.', '_')) }
        if (tag == null) {
            log.warn { "No matching tag found for package version '$packageVersion'. Using HEAD." }
            return "HEAD"
        }
        log.info{ "Found matching tag for package version '$packageVersion'. Using $tag." }
        return tag
    }
}
