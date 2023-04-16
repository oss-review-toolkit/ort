/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Paths

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.downloader.DownloadException
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.downloader.WorkingTree
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.ort.OrtAuthenticator
import org.ossreviewtoolkit.utils.ort.OrtProxySelector
import org.ossreviewtoolkit.utils.ort.requestPasswordAuthentication
import org.ossreviewtoolkit.utils.ort.showStackTrace

import org.tmatesoft.svn.core.SVNDepth
import org.tmatesoft.svn.core.SVNErrorMessage
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNNodeKind
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider
import org.tmatesoft.svn.core.auth.ISVNProxyManager
import org.tmatesoft.svn.core.auth.SVNAuthentication
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication
import org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager
import org.tmatesoft.svn.core.io.SVNRepositoryFactory
import org.tmatesoft.svn.core.wc.SVNClientManager
import org.tmatesoft.svn.core.wc.SVNRevision
import org.tmatesoft.svn.util.Version

class Subversion : VersionControlSystem() {
    internal companion object : Logging

    private val ortAuthManager = OrtSVNAuthenticationManager()
    private val clientManager = SVNClientManager.newInstance().apply {
        setAuthenticationManager(ortAuthManager)
    }

    override val type = VcsType.SUBVERSION
    override val priority = 10
    override val latestRevisionNames = listOf("HEAD")

    override fun getVersion(): String = Version.getVersionString()

    override fun getDefaultBranchName(url: String) = "trunk"

    override fun getWorkingTree(vcsDirectory: File) = SubversionWorkingTree(vcsDirectory, type, clientManager)

    override fun isApplicableUrlInternal(vcsUrl: String) =
        try {
            SVNRepositoryFactory.create(SVNURL.parseURIEncoded(vcsUrl))
                .apply { authenticationManager = ortAuthManager }
                .checkPath("", -1) != SVNNodeKind.NONE
        } catch (e: SVNException) {
            e.showStackTrace()

            logger.debug {
                "An exception was thrown when checking $vcsUrl for a $type repository: ${e.collectMessages()}"
            }

            false
        }

    override fun initWorkingTree(targetDir: File, vcs: VcsInfo): WorkingTree {
        try {
            clientManager.updateClient.doCheckout(
                SVNURL.parseURIEncoded(vcs.url),
                targetDir,
                SVNRevision.HEAD,
                SVNRevision.HEAD,
                SVNDepth.EMPTY,
                /* allowUnversionedObstructions = */ false
            )
        } catch (e: SVNException) {
            e.showStackTrace()

            throw DownloadException("Unable to initialize a $type working tree in '$targetDir' from ${vcs.url}.", e)
        }

        return getWorkingTree(targetDir)
    }

    private fun updateEmptyPath(workingTree: WorkingTree, revision: SVNRevision, path: String) {
        val pathIterator = Paths.get(path).iterator()
        var currentPath = workingTree.workingDir

        while (pathIterator.hasNext()) {
            clientManager.updateClient.doUpdate(
                currentPath,
                revision,
                SVNDepth.EMPTY,
                /* allowUnversionedObstructions = */ false,
                /* depthIsSticky = */ true
            )

            currentPath = currentPath.resolve(pathIterator.next().toFile())
        }
    }

    override fun updateWorkingTree(workingTree: WorkingTree, revision: String, path: String, recursive: Boolean) =
        runCatching {
            revision.toLongOrNull()?.let { numericRevision ->
                // This code path updates the working tree to a numeric revision.
                val svnRevision = SVNRevision.create(numericRevision)

                // First update the (empty) working tree to the desired revision along the requested path.
                updateEmptyPath(workingTree, svnRevision, path)

                // Then deepen only the requested path in the desired revision.
                clientManager.updateClient.apply { isIgnoreExternals = !recursive }.doUpdate(
                    workingTree.workingDir.resolve(path),
                    svnRevision,
                    SVNDepth.INFINITY,
                    /* allowUnversionedObstructions = */ false,
                    /* depthIsSticky = */ true
                )
            } ?: run {
                // This code path updates the working tree to a symbolic revision.
                val svnUrl = SVNURL.parseURIEncoded(
                    "${workingTree.getRemoteUrl()}/$revision"
                )

                // First switch the (empty) working tree to the requested branch / tag.
                clientManager.updateClient.doSwitch(
                    workingTree.workingDir,
                    svnUrl,
                    SVNRevision.HEAD,
                    SVNRevision.HEAD,
                    SVNDepth.EMPTY,
                    /* allowUnversionedObstructions = */ false,
                    /* depthIsSticky = */ true,
                    /* ignoreAncestry = */ true
                )

                // Then update the working tree in the current revision along the requested path, and ...
                updateEmptyPath(workingTree, SVNRevision.HEAD, path)

                // Finally, deepen only the requested path in the current revision.
                clientManager.updateClient.apply { isIgnoreExternals = !recursive }.doUpdate(
                    workingTree.workingDir.resolve(path),
                    SVNRevision.HEAD,
                    SVNDepth.INFINITY,
                    /* allowUnversionedObstructions = */ false,
                    /* depthIsSticky = */ true
                )
            }

            true
        }.onFailure {
            it.showStackTrace()

            logger.warn {
                "Failed to update the $type working tree at '${workingTree.workingDir}' to revision '$revision':\n" +
                        it.collectMessages()
            }
        }.map {
            revision
        }
}

private class OrtSVNAuthenticationManager : DefaultSVNAuthenticationManager(
    /* configDirectory = */ null,
    /* storeAuth = */ false,
    /* userName = */ null,
    /* password = */ null,
    /* privateKey = */ null,
    /* passphrase = */ charArrayOf()
) {
    private val ortProxySelector = OrtProxySelector.install().also { OrtAuthenticator.install() }

    init {
        authenticationProvider = object : ISVNAuthenticationProvider {
            override fun requestClientAuthentication(
                kind: String,
                svnurl: SVNURL,
                realm: String,
                errorMessage: SVNErrorMessage?,
                previousAuth: SVNAuthentication?,
                authMayBeStored: Boolean
            ): SVNAuthentication? {
                val auth = requestPasswordAuthentication(svnurl.host, svnurl.port, svnurl.protocol) ?: return null

                return SVNPasswordAuthentication.newInstance(
                    auth.userName, auth.password, authMayBeStored, svnurl, /* isPartial = */ false
                )
            }

            override fun acceptServerAuthentication(
                url: SVNURL,
                realm: String,
                certificate: Any,
                resultMayBeStored: Boolean
            ) = ISVNAuthenticationProvider.ACCEPTED
        }
    }

    override fun getProxyManager(url: SVNURL): ISVNProxyManager? {
        val proxy = ortProxySelector.select(URI(url.toString())).firstOrNull() ?: return null
        val proxyAddress = (proxy.address() as? InetSocketAddress) ?: return null
        val authentication = ortProxySelector.getProxyAuthentication(proxy)

        return object : ISVNProxyManager {
            override fun getProxyUserName() = authentication?.userName

            override fun getProxyPassword() = authentication?.password?.let { String(it) }

            override fun getProxyPort() = proxyAddress.port

            override fun getProxyHost() = proxyAddress.hostName

            override fun acknowledgeProxyContext(accepted: Boolean, errorMessage: SVNErrorMessage?) = Unit
        }
    }
}
