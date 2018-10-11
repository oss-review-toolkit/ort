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
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.hasFragmentRevision
import com.here.ort.utils.log
import com.here.ort.utils.normalizeVcsUrl
import com.here.ort.utils.showStackTrace

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
        fun forUrl(vcsUrl: String) =
                if (vcsUrl in urlToVcsMap) {
                    urlToVcsMap[vcsUrl]
                } else {
                    ALL.find {
                        if (it is CommandLineTool) {
                            it.isInPath() && it.isApplicableUrl(vcsUrl)
                        } else {
                            it.isApplicableUrl(vcsUrl)
                        }
                    }.also { urlToVcsMap[vcsUrl] = it }
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
                    if (it is CommandLineTool) {
                        if (it.isInPath()) it.getWorkingTree(absoluteVcsDirectory) else null
                    } else {
                        it.getWorkingTree(absoluteVcsDirectory)
                    }
                }.find {
                    try {
                        it.isValid()
                    } catch (e: IOException) {
                        e.showStackTrace()

                        log.debug {
                            "Exception while validating ${it.vcsType} working tree, treating it as non-applicable: " +
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

                    val type = forUrl(url)?.type ?: ""
                    if (type == "Git") {
                        url += ".git"
                    }

                    VcsInfo(type, url, revision, path = path)
                }

                uri.host.endsWith("github.com") || uri.host.endsWith("gitlab.com") -> {
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
                    } else {
                        if (uri.hasFragmentRevision()) revision = uri.fragment
                    }

                    VcsInfo("git", url, revision, path = path)
                }

                else -> {
                    when {
                        vcsUrl.contains(".git/") -> {
                            val url = normalizeVcsUrl(vcsUrl.substringBefore(".git/"))
                            val path = vcsUrl.substringAfter(".git/")
                            VcsInfo("git", "$url.git", "", null, path)
                        }

                        vcsUrl.contains(".git#") || Regex("^git.+#[a-fA-F0-9]{7,}$").matches(vcsUrl) -> {
                            val url = normalizeVcsUrl(vcsUrl.substringBeforeLast('#'))
                            val revision = vcsUrl.substringAfterLast('#')
                            VcsInfo("git", url, revision, null, "")
                        }

                        else -> VcsInfo("", vcsUrl, "")
                    }
                }
            }
        }
    }

    /**
     * A string uniquely identifying the type of this [VersionControlSystem], e.g. 'Git'.
     */
    val type: String = javaClass.simpleName

    /**
     * A list of lowercase names that clearly identify the VCS. For example ["svn", "subversion"] for Subversion.
     */
    protected abstract val aliases: List<String>

    /**
     * A list of symbolic names that point to the latest revision.
     */
    protected abstract val latestRevisionNames: List<String>

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
