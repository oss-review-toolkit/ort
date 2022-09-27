/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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

package org.ossreviewtoolkit.downloader.vcs

import java.io.File

import org.ossreviewtoolkit.downloader.WorkingTree
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.ort.showStackTrace

import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.wc.SVNClientManager
import org.tmatesoft.svn.core.wc.SVNRevision

class SubversionWorkingTree(
    workingDir: File,
    vcsType: VcsType,
    private val clientManager: SVNClientManager
) : WorkingTree(workingDir, vcsType) {
    private val directoryNamespaces = listOf("branches", "tags", "trunk", "wiki")

    override fun isValid(): Boolean {
        if (!workingDir.isDirectory) {
            return false
        }

        return doSvnInfo() != null
    }

    override fun isShallow() = false

    override fun getRemoteUrl() = doSvnInfo()?.repositoryRootURL?.toString().orEmpty()

    override fun getRevision() = doSvnInfo()?.committedRevision?.number?.toString().orEmpty()

    override fun getRootPath() = doSvnInfo()?.workingCopyRoot ?: workingDir

    override fun listRemoteBranches() = listRemoteRefs("branches")

    override fun listRemoteTags() = listRemoteRefs("tags")

    fun getFullUrl() = doSvnInfo()?.url?.toString().orEmpty()

    private fun listRemoteRefs(namespace: String): List<String> {
        val refs = mutableListOf<String>()
        val remoteUrl = getRemoteUrl()
        val fullUrl = getFullUrl()

        val projectRoot = if (directoryNamespaces.any { "/$it/" in fullUrl }) {
            remoteUrl
        } else {
            fullUrl
        }

        // We assume a single project directory layout.
        val svnUrl = SVNURL.parseURIEncoded("$projectRoot/$namespace")

        try {
            clientManager.logClient.doList(
                svnUrl,
                SVNRevision.HEAD,
                SVNRevision.HEAD,
                /* fetchLocks = */ false,
                /* recursive = */ false
            ) { dirEntry ->
                if (dirEntry.name.isNotEmpty()) refs += "$namespace/${dirEntry.relativePath}"
            }
        } catch (e: SVNException) {
            e.showStackTrace()

            Subversion.logger.info { "Unable to list remote refs for $vcsType repository at $remoteUrl." }
        }

        return refs
    }

    private fun doSvnInfo() =
        runCatching { clientManager.wcClient.doInfo(workingDir, SVNRevision.WORKING) }.getOrNull()
}
