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

package com.here.ort.downloader

import com.here.ort.downloader.vcs.*
import com.here.ort.model.VcsInfo

import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Paths

abstract class VersionControlSystem<WD : WorkingDirectory> {
    companion object {
        /**
         * The prioritized list of all available version control systems. This needs to be initialized lazily to ensure
         * the referred objects, which derive from this class, exist.
         */
        val ALL by lazy {
            listOf(
                    Git,
                    GitRepo,
                    Mercurial,
                    Subversion,
                    Cvs
            )
        }

        /**
         * Return the applicable VCS for the given [vcsProvider], or null if none is applicable.
         */
        fun forProvider(vcsProvider: String) = ALL.find { it.isApplicableProvider(vcsProvider) }

        /**
         * Return the applicable VCS for the given [vcsUrl], or null if none is applicable.
         */
        fun forUrl(vcsUrl: String) = ALL.find { it.isApplicableUrl(vcsUrl) }

        /**
         * Return the applicable VCS for the given [vcsDirectory], or null if none is applicable.
         */
        fun forDirectory(vcsDirectory: File) =
            ALL.asSequence().map {
                it.getWorkingDirectory(vcsDirectory)
            }.find {
                it.isValid()
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
                return VcsInfo("", vcsUrl, "", "")
            }

            return when {
                uri.host.endsWith("github.com") -> {
                    var url = uri.scheme + "://" + uri.authority

                    // Append the first two path components that denote the user and project to the base URL.
                    val pathIterator = Paths.get(uri.path).iterator()
                    if (pathIterator.hasNext()) {
                        url += "/${pathIterator.next()}"
                    }
                    if (pathIterator.hasNext()) {
                        url += "/${pathIterator.next()}"

                        // GitHub only hosts Git repositories.
                        if (!url.endsWith(".git")) {
                            url += ".git"
                        }
                    }

                    var revision = ""
                    var path = ""

                    if (pathIterator.hasNext() && pathIterator.next().toString() in listOf("blob", "tree")) {
                        if (pathIterator.hasNext()) {
                            revision = pathIterator.next().toString()
                            path = uri.path.substringAfter(revision).trimStart('/').removeSuffix(".git")
                        }
                    }

                    VcsInfo("Git", url, revision, path)
                }
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

                    val provider = forUrl(url)?.toString() ?: ""
                    if (provider == "Git") {
                        url += ".git"
                    }

                    VcsInfo(provider, url, revision, path)
                }
                else -> VcsInfo("", vcsUrl, "", "")
            }
        }
    }

    /**
     * Return a simple string representation for this VCS.
     */
    override fun toString(): String = javaClass.simpleName

    /**
     * Return the VCS command's version string, or an empty string if the version cannot be determined.
     */
    abstract fun getVersion(): String

    /**
     * Return a working directory instance for this VCS.
     */
    abstract fun getWorkingDirectory(vcsDirectory: File): WD

    /**
     * Return true if the provider name matches this VCS. For example for SVN it should return true on "svn",
     * "subversion", or any other spelling that clearly identifies SVN.
     */
    abstract fun isApplicableProvider(vcsProvider: String): Boolean

    /**
     * Return true if this VCS can download from the provided URL. Should only return true when it's almost unambiguous,
     * for example when the URL ends on ".git" for Git or contains "/svn/" for SVN, but not when it contains the string
     * "git" as this could also be part of the host or project names.
     */
    abstract fun isApplicableUrl(vcsUrl: String): Boolean

    /**
     * Use this VCS to download the source code from the specified URL.
     *
     * @return A String identifying the revision that was downloaded.
     *
     * @throws DownloadException In case the download failed.
     */
    abstract fun download(vcsUrl: String, vcsRevision: String?, vcsPath: String?, version: String, targetDir: File)
            : String?
}

/**
 * A class representing a local VCS working directory.
 */
abstract class WorkingDirectory(val workingDir: File, val provider: String) {

    /**
     * Conveniently return all VCS information for a given [path].
     */
    abstract fun getInfo(path: File): VcsInfo

    /**
     * Return true if the [workingDir] is managed by this VCS, false otherwise.
     */
    abstract fun isValid(): Boolean

    /**
     * Return the clone URL of the associated remote repository.
     */
    abstract fun getRemoteUrl(): String


    /**
     * Return the VCS root for the given [path].
     */
    abstract fun getRootPath(path: File): String

    /**
     * Return the relative path to [path] with respect to the VCS root.
     */
    abstract fun getPathToRoot(path: File): String
}

abstract class WorkingDirectoryWithRevision(workingFir: File, provider: String)
    : WorkingDirectory(workingFir, provider) {

    /**
     * Return the VCS-specific working directory revision.
     */
    abstract fun getRevision(): String

    override fun getInfo(path: File): VcsInfo = VcsInfo(provider, getRemoteUrl(), getRevision(), getPathToRoot(path))
}

abstract class WorkingDirectoryWithFileRevisions(workingFir: File, provider: String)
    : WorkingDirectory(workingFir, provider) {

    /**
     * Return the VCS-specific file revision.
     */
    abstract fun getRevision(file:  File): String

    override fun getInfo(path: File): VcsInfo = VcsInfo(provider, getRemoteUrl(), "", getPathToRoot(path))
}
