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
import com.here.ort.spdx.LICENSE_FILE_NAMES
import com.here.ort.utils.OS
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
    override val aliases = listOf("git")

    override fun isApplicableUrlInternal(vcsUrl: String) =
            ProcessCapture("git", "ls-remote", vcsUrl).isSuccess

    override fun download(pkg: Package, targetDir: File, allowMovingRevisions: Boolean,
                          recursive: Boolean): WorkingTree {
        log.info { "Using $type version ${getVersion()}." }

        try {
            return createWorkingTree(pkg, targetDir, allowMovingRevisions).also { workingTree ->
                if (recursive && File(workingTree.workingDir, ".gitmodules").isFile) {
                    run(workingTree.workingDir, "submodule", "update", "--init", "--recursive")
                }

                pkg.vcsProcessed.path.let {
                    if (it.isNotEmpty() && !workingTree.workingDir.resolve(it).exists()) {
                        throw DownloadException("The $type working directory at '${workingTree.workingDir}' does not " +
                                "contain the requested path '$it'.")
                    }
                }
            }
        } catch (e: IOException) {
            throw DownloadException("$type failed to download from ${pkg.vcsProcessed.url}.", e)
        }
    }

    private fun createWorkingTree(pkg: Package, targetDir: File, allowMovingRevisions: Boolean): WorkingTree {
        // Do not use "git clone" to have more control over what is being fetched.
        run(targetDir, "init")
        run(targetDir, "remote", "add", "origin", pkg.vcsProcessed.url)

        // Enable the more efficient Git Wire Protocol version 2, if possible. See
        // https://github.com/git/git/blob/master/Documentation/technical/protocol-v2.txt
        if (Semver(getVersion()).isGreaterThanOrEqualTo("2.18.0")) {
            run(targetDir, "config", "protocol.version", "2")
        }

        if (OS.isWindows) {
            run(targetDir, "config", "core.longpaths", "true")
        }

        if (pkg.vcsProcessed.path.isNotBlank()) {
            log.info { "Configuring Git to do sparse checkout of path '${pkg.vcsProcessed.path}'." }
            run(targetDir, "config", "core.sparseCheckout", "true")
            val gitInfoDir = File(targetDir, ".git/info").apply { safeMkdirs() }
            val path = pkg.vcsProcessed.path.let { if (it.startsWith("/")) it else "/$it" }
            File(gitInfoDir, "sparse-checkout").writeText("$path\n" +
                    LICENSE_FILE_NAMES.joinToString("\n") { "/$it" })
        }

        val workingTree = getWorkingTree(targetDir)

        val revisionCandidates = mutableListOf<String>()

        if (allowMovingRevisions || isFixedRevision(workingTree, pkg.vcsProcessed.revision)) {
            revisionCandidates += pkg.vcsProcessed.revision
        } else {
            log.warn {
                "No valid revision specified. Other possible candidates might cause the downloaded source code " +
                        "to not match the package version."
            }
        }

        log.info { "Trying to guess a $type revision for version '${pkg.id.version}' to fall back to." }
        try {
            workingTree.guessRevisionName(pkg.id.name, pkg.id.version).also { revision ->
                revisionCandidates += revision
                log.info { "Found $type revision '$revision' for version '${pkg.id.version}'." }
            }
        } catch (e: IOException) {
            e.showStackTrace()

            log.info { "No $type revision for version '${pkg.id.version}' found: ${e.message}" }
        }

        val revision = revisionCandidates.firstOrNull()
                ?: throw IOException("Unable to determine a revision to checkout.")

        // To safe network bandwidth, first try to only fetch exactly the revision we want. Skip this optimization for
        // SSH URLs to GitHub as GitHub does not have "allowReachableSHA1InWant" (nor "allowAnySHA1InWant") enabled and
        // the SSH transport invokes "git-upload-pack" without the "--stateless-rpc" option, causing different
        // reachability rules to kick in. Over HTTPS, the ref advertisement and the want/have negotiation happen over
        // two separate connections so the later actually does a reachability check instead of relying on the advertised
        // refs.
        if (!pkg.vcsProcessed.url.startsWith("ssh://git@github.com/")) {
            try {
                log.info { "Trying to fetch only revision '$revision' with depth limited to $GIT_HISTORY_DEPTH." }
                run(targetDir, "fetch", "--depth", GIT_HISTORY_DEPTH.toString(), "origin", revision)

                // The documentation for git-fetch states that "By default, any tag that points into the histories being
                // fetched is also fetched", but that is not true for shallow fetches of a tag; then the tag itself is
                // not fetched. So create it manually afterwards.
                if (revision in workingTree.listRemoteTags()) {
                    run(targetDir, "tag", revision, "FETCH_HEAD")
                }

                run(targetDir, "checkout", revision)
                return workingTree
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
            run(targetDir, "fetch", "--depth", GIT_HISTORY_DEPTH.toString(), "--tags", "origin")
            run(targetDir, "checkout", revision)
            return workingTree
        } catch (e: IOException) {
            e.showStackTrace()

            log.warn {
                "Could not fetch with only a depth of $GIT_HISTORY_DEPTH: ${e.message}\n" +
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
                e.showStackTrace()

                log.info { "Failed to checkout revision '$candidate'. Trying next candidate, if any." }

                false
            }
        } ?: throw IOException("Unable to determine a revision to checkout.")

        return workingTree
    }
}
