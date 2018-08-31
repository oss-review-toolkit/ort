/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

package com.here.ort.downloader

import ch.frankel.slf4k.*

import com.here.ort.model.Package
import com.here.ort.model.VcsInfo
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.filterVersionNames
import com.here.ort.utils.getPathFromEnvironment
import com.here.ort.utils.log

import com.vdurmont.semver4j.Semver

import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Paths
import java.util.ServiceLoader

abstract class VersionControlSystem {
    companion object {
        private val LOADER = ServiceLoader.load(VersionControlSystem::class.java)!!

        /**
         * The (prioritized) list of all available Version Control Systems in the classpath.
         */
        val ALL by lazy { LOADER.iterator().asSequence().toList() }

        /**
         * Return the applicable VCS for the given [vcsType], or null if none is applicable.
         */
        fun forType(vcsType: String) = ALL.find { it.isApplicableType(vcsType) }

        /**
         * A map to cache the [VersionControlSystem], if any, for previously queried URLs. This helps to speed up
         * subsequent queries for the same URLs as identifying the [VersionControlSystem] for arbitrary URLs might
         * require network access.
         */
        private val urlToVcsMap = mutableMapOf<String, VersionControlSystem?>()

        /**
         * Return the applicable VCS for the given [vcsUrl], or null if none is applicable.
         */
        fun forUrl(vcsUrl: String) = if (vcsUrl in urlToVcsMap) {
            urlToVcsMap[vcsUrl]
        } else {
            ALL.find { it.isInPath() && it.isApplicableUrl(vcsUrl) }.also { urlToVcsMap[vcsUrl] = it }
        }

        /**
         * A map to cache the [WorkingTree], if any, for previously queried directories. This helps to speed up
         * subsequent queries for the same directories and to reduce log output from running external VCS tools.
         */
        private val dirToVcsMap = mutableMapOf<File, WorkingTree?>()

        /**
         * Return the applicable VCS working tree for the given [vcsDirectory], or null if none is applicable.
         */
        fun forDirectory(vcsDirectory: File): WorkingTree? {
            val absoluteVcsDirectory = vcsDirectory.absoluteFile

            return if (absoluteVcsDirectory in dirToVcsMap) {
                dirToVcsMap[absoluteVcsDirectory]
            } else {
                ALL.asSequence().mapNotNull {
                    if (it.isInPath()) it.getWorkingTree(absoluteVcsDirectory) else null
                }.find {
                    try {
                        it.isValid()
                    } catch (e: IOException) {
                        log.debug {
                            "Exception while validating ${it.getType()} working tree, treating it as non-applicable: " +
                                    e.message
                        }

                        false
                    }
                }.also {
                    dirToVcsMap[absoluteVcsDirectory] = it
                }
            }
        }

        /**
         * Return all VCS information about a [workingDir]. This is a convenience wrapper around [WorkingTree.getInfo].
         */
        fun getCloneInfo(workingDir: File) = VersionControlSystem.forDirectory(workingDir)?.getInfo() ?: VcsInfo.EMPTY

        /**
         * Return all VCS information about a specific [path]. If [path] points to a nested VCS (like an individual Git
         * working tree of GitRepo), information for the nested VCS is returned.
         */
        fun getPathInfo(path: File): VcsInfo {
            val dir = path.takeIf { it.isDirectory } ?: path.parentFile
            return VersionControlSystem.forDirectory(dir)?.let { workingTree ->
                // Always return the relative path to the (nested) VCS root.
                workingTree.getInfo().copy(path = workingTree.getPathToRoot(path))
            } ?: VcsInfo.EMPTY
        }

        /**
         * Decompose a [vcsUrl] into any contained VCS information.
         */
        fun splitUrl(vcsUrl: String): VcsInfo {
            // A hierarchical URI looks like
            //     [scheme:][//authority][path][?query][#fragment]
            // where a server-based "authority" has the syntax
            //     [user-info@]host[:port]
            val uri = try {
                URI(vcsUrl)
            } catch (e: URISyntaxException) {
                // Fall back to returning just the original URL.
                return VcsInfo("", vcsUrl, "")
            }

            return when {
                uri.host == null -> VcsInfo("", vcsUrl, "")

                uri.host.endsWith("bitbucket.org") -> {
                    var url = uri.scheme + "://" + uri.authority

                    // Append the first two path components that denote the user and project to the base URL.
                    val pathIterator = Paths.get(uri.path).iterator()
                    if (pathIterator.hasNext()) {
                        url += "/${pathIterator.next()}"
                    }
                    if (pathIterator.hasNext()) {
                        url += "/${pathIterator.next()}"
                    }

                    var revision = ""
                    var path = ""

                    if (pathIterator.hasNext() && pathIterator.next().toString() == "src") {
                        if (pathIterator.hasNext()) {
                            revision = pathIterator.next().toString()
                            path = uri.path.substringAfter(revision).trimStart('/').removeSuffix(".git")
                        }
                    }

                    val type = forUrl(url)?.toString() ?: ""
                    if (type == "Git") {
                        url += ".git"
                    }

                    VcsInfo(type, url, revision, path = path)
                }

                uri.host.endsWith("gitlab.com") || uri.host.endsWith("github.com") -> {
                    var url = uri.scheme + "://" + uri.authority

                    // Append the first two path components that denote the user and project to the base URL.
                    val pathIterator = Paths.get(uri.path).iterator()
                    if (pathIterator.hasNext()) {
                        url += "/${pathIterator.next()}"
                    }
                    if (pathIterator.hasNext()) {
                        url += "/${pathIterator.next()}"

                        // GitLab and GitHub only host Git repositories.
                        if (!url.endsWith(".git")) {
                            url += ".git"
                        }
                    }

                    var revision = ""
                    var path = ""

                    if (pathIterator.hasNext()) {
                        val extra = pathIterator.next().toString()
                        if (extra in listOf("blob", "tree") && pathIterator.hasNext()) {
                            revision = pathIterator.next().toString()
                            path = uri.path.substringAfter(revision).trimStart('/').removeSuffix(".git")
                        } else {
                            // Just treat all the extra components as a path.
                            path = (sequenceOf(extra) + pathIterator.asSequence()).joinToString("/")
                        }
                    }

                    VcsInfo("git", url, revision, path = path)
                }

                else -> VcsInfo("", vcsUrl, "")
            }
        }
    }

    /**
     * Return the Java class name as a simple way to refer to the [VersionControlSystem].
     */
    override fun toString(): String = javaClass.simpleName

    /**
     * A class representing a local VCS working tree. The passed [workingDir] does not necessarily need to be the
     * root directory of the tree. The root directory can be determined by calling [getRootPath].
     */
    abstract inner class WorkingTree(val workingDir: File) {
        /**
         * Return a simple string representation for the VCS this working tree belongs to.
         */
        fun getType() = this@VersionControlSystem.toString()

        /**
         * Conveniently return all VCS information about how this working tree was created, so it could be easily
         * recreated from that information.
         */
        open fun getInfo() = VcsInfo(getType(), getRemoteUrl(), getRevision(), path = getPathToRoot(workingDir))

        /**
         * Return true if the [workingDir] is managed by this VCS, false otherwise.
         */
        abstract fun isValid(): Boolean

        /**
         * Return whether this is a shallow working tree with truncated history.
         */
        abstract fun isShallow(): Boolean

        /**
         * Return the clone URL of the associated remote repository.
         */
        abstract fun getRemoteUrl(): String

        /**
         * Return the VCS-specific working tree revision.
         */
        abstract fun getRevision(): String

        /**
         * Return the root directory of this working tree.
         */
        abstract fun getRootPath(): File

        /**
         * Return the list of branches available in the remote repository.
         */
        abstract fun listRemoteBranches(): List<String>

        /**
         * Return the list of tags available in the remote repository.
         */
        abstract fun listRemoteTags(): List<String>

        /**
         * Search (symbolic) names of VCS revisions for a match with the given [project] and [version].
         *
         * @return The matching VCS revision, never blank.
         * @throws IOException If no or multiple matching revisions are found.
         */
        fun guessRevisionName(project: String, version: String): String {
            val versionNames = filterVersionNames(version, listRemoteTags(), project)
            return when {
                versionNames.isEmpty() ->
                    throw IOException("No matching tag found for version '$version'.")
                versionNames.size > 1 ->
                    throw IOException("Multiple matching tags found for version '$version': $versionNames")
                else -> versionNames.first()
            }
        }

        /**
         * Return the relative path to [path] with respect to the VCS root.
         */
        fun getPathToRoot(path: File): String {
            val relativePath = path.absoluteFile.relativeTo(getRootPath())

            // Use Unix paths even on Windows for consistent output.
            return relativePath.invariantSeparatorsPath
        }
    }

    /**
     * A list of lowercase names that clearly identify the VCS. For example ["svn", "subversion"] for Subversion.
     */
    protected abstract val aliases: List<String>

    /**
     * The name of the command line program to run for this VCS implementation.
     */
    protected abstract val commandName: String

    /**
     * A list of symbolic names that point to the latest revision.
     */
    protected abstract val latestRevisionNames: List<String>

    /**
     * Return whether the command for this VCS implementation is available in the system PATH.
     */
    fun isInPath() = getPathFromEnvironment(commandName) != null

    /**
     * A convenience function to run the command line program for this VCS implementation.
     */
    fun run(workingDir: File, vararg args: String) =
            ProcessCapture(workingDir, commandName, *args).requireSuccess()

    /**
     * Return the VCS command's version string, or an empty string if the version cannot be determined.
     */
    abstract fun getVersion(): String

    /**
     * Return a working tree instance for this VCS.
     */
    abstract fun getWorkingTree(vcsDirectory: File): WorkingTree

    /**
     * Return true if any of the [aliases] matches [vcsType]. Comparison is done case-insensitively.
     */
    fun isApplicableType(vcsType: String) = vcsType.toLowerCase() in aliases

    /**
     * Return true if this VCS can download from the provided URL. Should only return true when it's almost unambiguous,
     * for example when the URL ends on ".git" for Git or contains "/svn/" for SVN, but not when it contains the string
     * "git" as this could also be part of the host or project names.
     */
    fun isApplicableUrl(vcsUrl: String) =
            vcsUrl.isNotBlank() && !vcsUrl.endsWith(".html") && isApplicableUrlInternal(vcsUrl)

    protected abstract fun isApplicableUrlInternal(vcsUrl: String): Boolean

    /**
     * Download the source code as specified by the [pkg] information to [targetDir]. [allowMovingRevisions] toggles
     * whether symbolic names for which the revision they point might change are accepted or not. If [recursive] is
     * true, any nested repositories (like Git submodules or Mercurial subrepositories) are downloaded, too.
     *
     * @return An object describing the downloaded working tree.
     *
     * @throws DownloadException In case the download failed.
     */
    abstract fun download(pkg: Package, targetDir: File, allowMovingRevisions: Boolean = false,
                          recursive: Boolean = true): WorkingTree

    /**
     * Check whether the given [revision] is likely to name a fixed revision that does not move.
     */
    fun isFixedRevision(workingTree: WorkingTree, revision: String) =
            revision.isNotBlank() && revision !in latestRevisionNames && revision !in workingTree.listRemoteBranches()

    /**
     * Check whether the VCS tool is at least of the specified [expectedVersion], e.g. to check for features.
     */
    fun isAtLeastVersion(expectedVersion: String): Boolean {
        val actualVersion = Semver(getVersion(), Semver.SemverType.LOOSE)
        return actualVersion.isGreaterThanOrEqualTo(expectedVersion)
    }
}
