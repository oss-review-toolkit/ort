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

package org.ossreviewtoolkit.plugins.versioncontrolsystems.subversion

import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Paths

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.downloader.VersionControlSystemFactory
import org.ossreviewtoolkit.downloader.WorkingTree
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.div
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

@OrtPlugin(
    displayName = "Subversion",
    description = "A VCS implementation to interact with Subversion repositories.",
    factory = VersionControlSystemFactory::class
)
class Subversion(override val descriptor: PluginDescriptor = SubversionFactory.descriptor) : VersionControlSystem() {
    private val ortAuthManager = OrtSVNAuthenticationManager()
    private val clientManager = SVNClientManager.newInstance().apply {
        setAuthenticationManager(ortAuthManager)
    }

    override val type = VcsType.SUBVERSION
    override val priority = 10
    override val latestRevisionNames = listOf("HEAD")

    override fun getVersion(): String = Version.getVersionString()

    override fun getDefaultBranchName(url: String) = "trunk"

    override fun getWorkingTree(vcsDirectory: File): WorkingTree =
        SubversionWorkingTree(vcsDirectory, type, clientManager)

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

            throw IOException("Unable to initialize a $type working tree in '$targetDir' from ${vcs.url}.", e)
        }

        return getWorkingTree(targetDir)
    }

    private fun deepenWorkingTreePath(workingTree: WorkingTree, path: String, revision: SVNRevision): Long {
        val finalPath = workingTree.workingDir / path
        var currentPath = workingTree.workingDir

        // Avoid the "None of the targets are working copies" error by deepening one path at a time.
        val pathIterator = Paths.get(path).iterator()
        val pathRevisions = mutableSetOf<Long>()

        while (pathIterator.hasNext()) {
            currentPath = currentPath.resolve(pathIterator.next().toFile())

            pathRevisions += clientManager.updateClient.doUpdate(
                currentPath,
                revision,
                if (currentPath != finalPath) SVNDepth.EMPTY else SVNDepth.INFINITY,
                /* allowUnversionedObstructions = */ false,
                /* depthIsSticky = */ true
            )
        }

        return pathRevisions.single()
    }

    override fun updateWorkingTree(workingTree: WorkingTree, revision: String, path: String, recursive: Boolean) =
        runCatching {
            // Note that the path should never be part of the URL as that would root the working tree at that path, but
            // the path should be available in the working tree.
            val (svnUrl, svnRevision) = revision.toLongOrNull()?.let { numericRevision ->
                val url = workingTree.getRemoteUrl()

                SVNURL.parseURIEncoded(url) to SVNRevision.create(numericRevision)
            } ?: run {
                val url = listOf(workingTree.getRemoteUrl(), revision).joinToString("/")

                SVNURL.parseURIEncoded(url) to SVNRevision.HEAD
            }

            clientManager.updateClient.isIgnoreExternals = !recursive

            logger.info {
                val printableRevision = svnRevision.name ?: svnRevision.number
                "Switching $type '${workingTree.workingDir}' to $svnUrl at revision $printableRevision."
            }

            // For "peg revision" vs. "revision" see https://svnbook.red-bean.com/en/1.7/svn.advanced.pegrevs.html.
            val workingTreeRevision = clientManager.updateClient.doSwitch(
                workingTree.workingDir,
                svnUrl,
                /* pegRevision = */ SVNRevision.HEAD,
                /* revision = */ svnRevision,
                if (path.isEmpty()) SVNDepth.INFINITY else SVNDepth.EMPTY,
                /* allowUnversionedObstructions = */ false,
                /* depthIsSticky = */ true
            )

            logger.info { "$type working tree '${workingTree.workingDir}' is at revision $workingTreeRevision." }

            if (path.isNotEmpty()) {
                logger.info { "Deepening path '$path' in $type working tree '${workingTree.workingDir}'." }
                val pathRevision = deepenWorkingTreePath(workingTree, path, svnRevision)
                check(pathRevision == workingTreeRevision)
            }

            workingTreeRevision.toString()
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
