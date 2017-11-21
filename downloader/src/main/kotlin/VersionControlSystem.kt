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

import java.io.File

abstract class VersionControlSystem {
    companion object {
        /**
         * The prioritized list of all available version control systems. This needs to be initialized lazily to ensure
         * the referred objects, which derive from this class, exist.
         */
        val ALL by lazy {
            listOf(
                    Git,
                    GitRepo
            )
        }

        /**
         * Return the list of all applicable VCS for the given [vcsProvider], or null if none are applicable.
         */
        fun fromProvider(vcsProvider: String) = ALL.find { it.isApplicableProvider(vcsProvider) }

        /**
         * Return the list of all applicable VCS for the given [vcsUrl], or null if none are applicable.
         */
        fun fromUrl(vcsUrl: String) = ALL.find { it.isApplicableUrl(vcsUrl) }

        /**
         * Return the list of all applicable VCS for the given [vcsDirectory], or null if none are applicable.
         */
        fun fromDirectory(vcsDirectory: File) = ALL.find { it.isApplicableDirectory(vcsDirectory) }
    }

    /**
     * Return a simple string representation for this VCS.
     */
    override fun toString(): String = javaClass.simpleName

    /**
     * Use this VCS to download the source code from the specified URL.
     *
     * @return A String identifying the revision that was downloaded.
     *
     * @throws DownloadException In case the download failed.
     */
    abstract fun download(vcsUrl: String, vcsRevision: String?, vcsPath: String?, version: String, targetDir: File)
            : String

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
     * Return true if the specified local directory is managed by this VCS, false otherwise.
     */
    abstract fun isApplicableDirectory(vcsDirectory: File): Boolean

    /**
     * Return the relative path of [workingDir] with respect to the VCS root directory.
     */
    abstract fun getPathToRoot(workingDir: File): String

    /**
     * Return the VCS-specific revision for the given [workingDir].
     */
    abstract fun getWorkingRevision(workingDir: File): String

    /**
     * Return the URL of the (remote) repository the [workingDir] was cloned from.
     */
    abstract fun getRemoteUrl(workingDir: File): String
}
