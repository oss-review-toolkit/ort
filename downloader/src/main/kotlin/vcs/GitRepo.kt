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

import ch.frankel.slf4k.*

import com.here.ort.downloader.DownloadException
import com.here.ort.downloader.WorkingTree
import com.here.ort.model.Package
import com.here.ort.model.VcsInfo
import com.here.ort.utils.Os
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.getPathFromEnvironment
import com.here.ort.utils.log
import com.here.ort.utils.realFile
import com.here.ort.utils.searchUpwardsForSubdirectory

import java.io.File
import java.io.IOException

/**
 * The branch of git-repo to use. This allows to override git-repo's default of using the "stable" branch.
 */
const val GIT_REPO_BRANCH = "master"

class GitRepo : GitBase() {
    override val aliases = listOf("git-repo", "repo")
    override val priority: Int = 50

    override fun getWorkingTree(vcsDirectory: File): WorkingTree {
        val repoRoot = vcsDirectory.searchUpwardsForSubdirectory(".repo")

        return if (repoRoot == null) {
            object : GitWorkingTree(vcsDirectory, this) {
                override fun isValid() = false
            }
        } else {
            // GitRepo is special in that the workingDir points to the Git working tree of the manifest files, yet
            // the root path is the directory containing the ".repo" directory. This way Git operations work on a valid
            // Git repository, but path operations work relative to the path GitRepo was initialized in.
            object : GitWorkingTree(File(repoRoot, ".repo/manifests"), this) {
                // Return the path to the manifest as part of the VCS information, as that is required to recreate the
                // working tree.
                override fun getInfo(): VcsInfo {
                    val manifestLink = File(getRootPath(), ".repo/manifest.xml")
                    val manifestFile = manifestLink.realFile()
                    return super.getInfo().copy(path = manifestFile.relativeTo(workingDir).invariantSeparatorsPath)
                }

                override fun getNested(): Map<String, VcsInfo> {
                    val paths = runRepoCommand(workingDir, "list", "-p").stdout.lines().filter { it.isNotBlank() }
                    val nested = mutableMapOf<String, VcsInfo>()

                    paths.forEach { path ->
                        // Add the nested Repo project.
                        val workingTree = Git().getWorkingTree(getRootPath().resolve(path))
                        nested[path] = workingTree.getInfo()

                        // Add the Git submodules of the nested Repo project.
                        workingTree.getNested().forEach { (submodulePath, vcsInfo) ->
                            nested["$path/$submodulePath"] = vcsInfo
                        }
                    }

                    return nested
                }

                // Return the directory in which "repo init" was run (that directory in not managed with Git).
                override fun getRootPath() = workingDir.parentFile.parentFile
            }
        }
    }

    override fun isApplicableUrlInternal(vcsUrl: String) = false

    /**
     * Clones the Git repositories defined in the manifest file using the Git Repo tool. The manifest file is checked
     * out from the repository defined in [pkg.vcsProcessed][Package.vcsProcessed], its location is defined by
     * [pkg.vcsProcessed.path][VcsInfo.path].
     *
     * @throws DownloadException In case the download failed.
     */
    override fun download(
        pkg: Package, targetDir: File, allowMovingRevisions: Boolean,
        recursive: Boolean
    ): WorkingTree {
        val revision = pkg.vcsProcessed.revision.takeUnless { it.isBlank() } ?: "master"
        val manifestPath = pkg.vcsProcessed.path.takeUnless { it.isBlank() } ?: "default.xml"

        try {
            log.info {
                "Initializing git-repo from ${pkg.vcsProcessed.url} with revision '$revision' " +
                        "and manifest '$manifestPath'."
            }

            // Clone all projects instead of only those in the "default" group until we support specifying groups.
            runRepoCommand(
                targetDir,
                "init", "--groups=all", "--no-repo-verify",
                "--no-clone-bundle", "--repo-branch=$GIT_REPO_BRANCH",
                "-b", revision,
                "-u", pkg.vcsProcessed.url,
                "-m", manifestPath
            )

            // Repo allows to checkout Git repositories to nested directories. If a manifest is badly configured, a
            // nested Git checkout overwrites files in a directory of the upper-level Git repository. However, we still
            // want to be able to download such projects, so specify "--force-sync" to work around that issue.
            val syncArgs = mutableListOf("sync", "-c", "--force-sync")

            if (recursive) {
                syncArgs += "--fetch-submodules"
            }

            runRepoCommand(targetDir, *syncArgs.toTypedArray())

            log.debug { runRepoCommand(targetDir, "info").stdout }

            return getWorkingTree(targetDir)
        } catch (e: IOException) {
            throw DownloadException("Could not clone from ${pkg.vcsProcessed.url} using manifest '$manifestPath'.", e)
        }
    }

    private fun runRepoCommand(targetDir: File, vararg args: String) =
        if (Os.isWindows) {
            val repo = getPathFromEnvironment("repo") ?: throw IOException("'repo' not found in PATH.")

            // On Windows, the script itself is not executable, so we need to wrap the call by "python".
            ProcessCapture(targetDir, "python", repo.absolutePath, *args).requireSuccess()
        } else {
            ProcessCapture(targetDir, "repo", *args).requireSuccess()
        }
}
