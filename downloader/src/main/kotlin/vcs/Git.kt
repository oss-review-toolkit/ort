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
import com.here.ort.model.VcsType
import com.here.ort.spdx.LicenseFileMatcher
import com.here.ort.utils.Os
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.log
import com.here.ort.utils.safeMkdirs
import com.here.ort.utils.showStackTrace

import com.vdurmont.semver4j.Semver

import java.io.File
import java.io.IOException

// TODO: Make this configurable.
const val GIT_HISTORY_DEPTH = 50

class Git : GitBase() {
    override val type = VcsType.GIT
    override val priority = 100

    override fun isApplicableUrlInternal(vcsUrl: String) =
        ProcessCapture("git", "-c", "credential.helper=", "-c", "core.askpass=echo", "ls-remote", vcsUrl).isSuccess

    override fun initWorkingTree(targetDir: File, vcs: VcsInfo): WorkingTree {
        // Do not use "git clone" to have more control over what is being fetched.
        run(targetDir, "init")
        run(targetDir, "remote", "add", "origin", vcs.url)

        // Enable the more efficient Git Wire Protocol version 2, if possible. See
        // https://github.com/git/git/blob/master/Documentation/technical/protocol-v2.txt
        if (Semver(getVersion()).isGreaterThanOrEqualTo("2.18.0")) {
            run(targetDir, "config", "protocol.version", "2")
        }

        if (Os.isWindows) {
            run(targetDir, "config", "core.longpaths", "true")
        }

        if (vcs.path.isNotBlank()) {
            log.info { "Configuring Git to do sparse checkout of path '${vcs.path}'." }
            run(targetDir, "config", "core.sparseCheckout", "true")
            val gitInfoDir = File(targetDir, ".git/info").apply { safeMkdirs() }
            val path = vcs.path.let { if (it.startsWith("/")) it else "/$it" }
            File(gitInfoDir, "sparse-checkout").writeText("$path\n" +
                    LicenseFileMatcher.DEFAULT_MATCHER.licenseFileNames.joinToString("\n") { "/$it" })
        }

        return getWorkingTree(targetDir)
    }

    override fun updateWorkingTree(workingTree: WorkingTree, revision: String, recursive: Boolean) =
        updateWorkingTreeWithoutSubmodules(workingTree, revision) && (!recursive || initSubmodules(workingTree))

    private fun updateWorkingTreeWithoutSubmodules(workingTree: WorkingTree, revision: String): Boolean {
        // To safe network bandwidth, first try to only fetch exactly the revision we want. Skip this optimization for
        // SSH URLs to GitHub as GitHub does not have "allowReachableSHA1InWant" (nor "allowAnySHA1InWant") enabled and
        // the SSH transport invokes "git-upload-pack" without the "--stateless-rpc" option, causing different
        // reachability rules to kick in. Over HTTPS, the ref advertisement and the want/have negotiation happen over
        // two separate connections so the later actually does a reachability check instead of relying on the advertised
        // refs.
        if (!workingTree.getRemoteUrl().startsWith("ssh://git@github.com/")) {
            try {
                log.info { "Trying to fetch only revision '$revision' with depth limited to $GIT_HISTORY_DEPTH." }
                run(workingTree.workingDir, "fetch", "--depth", GIT_HISTORY_DEPTH.toString(), "origin", revision)

                // The documentation for git-fetch states that "By default, any tag that points into the histories being
                // fetched is also fetched", but that is not true for shallow fetches of a tag; then the tag itself is
                // not fetched. So create it manually afterwards.
                if (revision in workingTree.listRemoteTags()) {
                    run(workingTree.workingDir, "tag", revision, "FETCH_HEAD")
                }

                run(workingTree.workingDir, "checkout", revision)
                return true
            } catch (e: IOException) {
                e.showStackTrace()

                log.warn {
                    "Could not fetch only revision '$revision': ${e.message}\n" +
                            "Falling back to fetching all refs."
                }
            }
        }

        // Fall back to fetching all refs with limited depth of history.
        try {
            log.info { "Trying to fetch all refs with depth limited to $GIT_HISTORY_DEPTH." }
            run(workingTree.workingDir, "fetch", "--depth", GIT_HISTORY_DEPTH.toString(), "--tags", "origin")
            run(workingTree.workingDir, "checkout", revision)
            return true
        } catch (e: IOException) {
            e.showStackTrace()

            log.warn {
                "Could not fetch with only a depth of $GIT_HISTORY_DEPTH: ${e.message}\n" +
                        "Falling back to fetching everything."
            }
        }

        // Fall back to fetching everything.
        return try {
            log.info { "Trying to fetch everything including tags." }

            if (workingTree.isShallow()) {
                run(workingTree.workingDir, "fetch", "--unshallow", "--tags", "origin")
            } else {
                run(workingTree.workingDir, "fetch", "--tags", "origin")
            }

            run(workingTree.workingDir, "checkout", revision)

            true
        } catch (e: IOException) {
            e.showStackTrace()

            log.warn { "Failed to fetch everything: ${e.message}" }

            false
        }
    }

    private fun initSubmodules(workingTree: WorkingTree) =
        try {
            if (File(workingTree.workingDir, ".gitmodules").isFile) {
                run(workingTree.workingDir, "submodule", "update", "--init", "--recursive")
            }

            true
        } catch (e: IOException) {
            e.showStackTrace()

            log.warn { "Failed to initialize submodules: ${e.message}" }

            false
        }
}
