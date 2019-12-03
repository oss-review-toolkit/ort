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

import com.here.ort.downloader.DownloadException
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.downloader.WorkingTree
import com.here.ort.model.Package
import com.here.ort.model.VcsType
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.log

import java.io.File
import java.io.IOException
import java.util.regex.Pattern

import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.wc.SVNClientManager
import org.tmatesoft.svn.core.wc.SVNRevision

class Subversion : VersionControlSystem(), CommandLineTool {
    private val versionRegex = Pattern.compile("svn, [Vv]ersion (?<version>[\\d.]+) \\(r\\d+\\)")

    override val type = VcsType.SUBVERSION
    override val priority = 10
    override val latestRevisionNames = listOf("HEAD")

    override fun command(workingDir: File?) = "svn"

    override fun getVersion() =
        getVersion { output ->
            versionRegex.matcher(output.lineSequence().first()).let {
                if (it.matches()) {
                    it.group("version")
                } else {
                    ""
                }
            }
        }

    override fun getWorkingTree(vcsDirectory: File) =
        object : WorkingTree(vcsDirectory, type) {
            private val clientManager = SVNClientManager.newInstance()
            private val directoryNamespaces = listOf("branches", "tags", "trunk", "wiki")

            override fun isValid(): Boolean {
                if (!workingDir.isDirectory) {
                    return false
                }

                return doSvnInfo() != null
            }

            override fun isShallow() = false

            override fun getRemoteUrl() = doSvnInfo()?.url?.toDecodedString().orEmpty()

            override fun getRevision() = doSvnInfo()?.committedRevision?.number?.toString().orEmpty()

            override fun getRootPath() = doSvnInfo()?.workingCopyRoot ?: workingDir

            private fun listRemoteRefs(namespace: String): List<String> {
                val refs = mutableListOf<String>()
                val remoteUrl = getRemoteUrl()

                val projectRoot = if (directoryNamespaces.any { "/$it/" in remoteUrl }) {
                    doSvnInfo()?.repositoryRootURL?.toDecodedString().orEmpty()
                } else {
                    remoteUrl
                }

                // We assume a single project directory layout.
                val svnUrl = SVNURL.parseURIEncoded("$projectRoot/$namespace")

                try {
                    clientManager.logClient.doList(
                        svnUrl,
                        SVNRevision.HEAD,
                        SVNRevision.HEAD,
                        /*fetchLocks =*/ false,
                        /*recursive =*/ false
                    ) { dirEntry ->
                        if (dirEntry.name.isNotEmpty()) refs += dirEntry.relativePath
                    }
                } catch (e: SVNException) {
                    throw DownloadException("Unable to list remote refs for $type repository at $remoteUrl.")
                }

                return refs
            }

            override fun listRemoteBranches() = listRemoteRefs("branches")

            override fun listRemoteTags() = listRemoteRefs("tags")

            private fun doSvnInfo() =
                try {
                    clientManager.wcClient.doInfo(workingDir, SVNRevision.WORKING)
                } catch (e: SVNException) {
                    null
                }
        }

    override fun isApplicableUrlInternal(vcsUrl: String) =
        (vcsUrl.startsWith("svn+") || ProcessCapture("svn", "list", vcsUrl).isSuccess)

    private fun guessTagAndPath(targetDir: File, pkg: Package): Pair<String, String> {
        // The path to the tag (including the tag name), relative to the repository root.
        val tagPath: String

        // The path within the tag to limit the working directory to, relative to the repository root.
        val path: String

        val tagsMarker = "tags/"
        val tagsIndex = pkg.vcsProcessed.path.indexOf(tagsMarker)
        val isTagsPath = tagsIndex == 0 || (tagsIndex > 0 && pkg.vcsProcessed.path[tagsIndex - 1] == '/')
        if (isTagsPath) {
            log.info {
                "Ignoring the $type revision '${pkg.vcsProcessed.revision}' as the path points to a tag."
            }

            val tagsPathIndex = pkg.vcsProcessed.path.indexOf('/', tagsIndex + tagsMarker.length)

            tagPath = pkg.vcsProcessed.path.let {
                if (tagsPathIndex < 0) it else it.substring(0, tagsPathIndex)
            }
            path = pkg.vcsProcessed.path
        } else {
            log.info { "Trying to guess a $type revision for version '${pkg.id.version}'." }

            val revision = try {
                getWorkingTree(targetDir).guessRevisionName(pkg.id.name, pkg.id.version)
            } catch (e: IOException) {
                throw IOException("Unable to determine a revision to checkout.", e)
            }

            log.warn {
                "Using guessed $type revision '$revision' for version '${pkg.id.version}'. This might cause " +
                        "the downloaded source code to not match the package version."
            }

            tagPath = "tags/$revision"
            path = "$tagPath/${pkg.vcsProcessed.path}"
        }

        return Pair(tagPath, path)
    }

    override fun download(
        pkg: Package, targetDir: File, allowMovingRevisions: Boolean,
        recursive: Boolean
    ): WorkingTree {
        log.info { "Using $type version ${getVersion()}." }

        return try {
            // Create an empty working tree of the latest revision to allow sparse checkouts.
            run(targetDir, "checkout", pkg.vcsProcessed.url, "--depth", "empty", ".")

            var revision = pkg.vcsProcessed.revision.takeIf { it.isNotBlank() } ?: "HEAD"

            val workingTree = getWorkingTree(targetDir)
            if (allowMovingRevisions || isFixedRevision(workingTree, revision)) {
                if (pkg.vcsProcessed.path.isBlank()) {
                    // Deepen everything as we do not know whether the revision is contained in branches, tags or trunk.
                    run(targetDir, "update", "-r", revision, "--set-depth", "infinity")
                    workingTree
                } else {
                    // Deepen only the given path.
                    run(
                        targetDir, "update", "-r", revision, "--depth", "infinity", "--parents",
                        pkg.vcsProcessed.path
                    )

                    // Only return that part of the working tree that has the right revision.
                    getWorkingTree(File(targetDir, pkg.vcsProcessed.path))
                }
            } else {
                val (tagPath, path) = guessTagAndPath(targetDir, pkg)

                // In Subversion, tags are not symbolic names for revisions but names of directories containing
                // snapshots. Checking out a tag just is a sparse checkout of that path.
                run(targetDir, "update", "--depth", "infinity", "--parents", path)

                // Only return that part of the working tree that has the right revision.
                getWorkingTree(File(targetDir, tagPath))
            }
        } catch (e: IOException) {
            throw DownloadException("$type failed to download from ${pkg.vcsProcessed.url}.", e)
        }
    }
}
