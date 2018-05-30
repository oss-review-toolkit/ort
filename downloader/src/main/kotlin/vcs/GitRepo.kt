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
import com.here.ort.model.Package
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.log
import com.here.ort.utils.searchUpwardsForSubdirectory
import com.here.ort.utils.showStackTrace

import java.io.File
import java.io.IOException

object GitRepo : GitBase() {
    override val aliases = listOf("gitrepo", "git-repo", "repo")

    override fun getWorkingTree(vcsDirectory: File): WorkingTree {
        val repoRoot = vcsDirectory.searchUpwardsForSubdirectory(".repo")

        return if (repoRoot == null) {
            object : GitWorkingTree(vcsDirectory) {
                override fun isValid() = false
            }
        } else {
            object : GitWorkingTree(File(repoRoot, ".repo/manifests")) {
                // Return the directory in which "repo init" was run (that directory in not managed with Git).
                override fun getRootPath() = workingDir.parentFile.parentFile

                override fun getPathToRoot(path: File): String {
                    // GitRepo is special in that the path to the root is supposed to constantly return the path to the
                    // manifest file in use which is symlinked from ".repo/manifest.xml". So resolve that path to the
                    // underlying manifest file inside the "manifests" directory and ignore the actual path argument.
                    val manifestLink = File(getRootPath(), ".repo/manifest.xml")
                    val manifestDir = File(getRootPath(), ".repo/manifests")
                    return manifestLink.canonicalFile.toRelativeString(manifestDir)
                }
            }
        }
    }

    override fun isApplicableUrl(vcsUrl: String) = false

    /**
     * Clones the Git repositories defined in the manifest file using the Git Repo tool. The manifest file is checked
     * out from the repository defined in [pkg.vcsProcessed], its location is defined by [pkg.vcsProcessed.path].
     *
     * @throws DownloadException In case the download failed.
     */
    override fun download(pkg: Package, targetDir: File, allowMovingRevisions: Boolean,
                          recursive: Boolean): WorkingTree {
        val revision = pkg.vcsProcessed.revision.takeUnless { it.isBlank() } ?: "master"
        val manifestPath = pkg.vcsProcessed.path.takeUnless { it.isBlank() } ?: "manifest.xml"

        try {
            log.info {
                "Initializing git-repo from ${pkg.vcsProcessed.url} with revision '$revision' " +
                        "and manifest '$manifestPath'."
            }
            runRepoCommand(targetDir, "init", "-b", revision, "-u", pkg.vcsProcessed.url, "-m", manifestPath)

            log.info { "Starting git-repo sync." }
            runRepoCommand(targetDir, "sync", "-c")

            return getWorkingTree(targetDir)
        } catch (e: IOException) {
            e.showStackTrace()

            throw DownloadException("Could not clone from ${pkg.vcsProcessed.url} using manifest '$manifestPath'.", e)
        }
    }

    private fun runRepoCommand(targetDir: File, vararg args: String) {
        ProcessCapture(targetDir, "repo", *args).requireSuccess()
    }
}
