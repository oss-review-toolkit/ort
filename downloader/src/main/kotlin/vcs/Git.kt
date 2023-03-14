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
import java.io.IOException
import java.net.Authenticator
import java.net.InetSocketAddress
import java.security.PublicKey

import org.apache.logging.log4j.kotlin.Logging

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.LsRemoteCommand
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.errors.UnsupportedCredentialItem
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.SymbolicRef
import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.SshSessionFactory
import org.eclipse.jgit.transport.TagOpt
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.sshd.DefaultProxyDataFactory
import org.eclipse.jgit.transport.sshd.JGitKeyCache
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase
import org.eclipse.jgit.transport.sshd.SshdSessionFactory

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.downloader.WorkingTree
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.ort.requestPasswordAuthentication
import org.ossreviewtoolkit.utils.ort.showStackTrace

import org.semver4j.RangesList
import org.semver4j.RangesListFactory

// TODO: Make this configurable.
const val GIT_HISTORY_DEPTH = 50

// Replace prefixes of Git submodule repository URLs.
private val REPOSITORY_URL_PREFIX_REPLACEMENTS = mapOf(
    "git://" to "https://"
)

class Git : VersionControlSystem(), CommandLineTool {
    companion object : Logging {
        init {
            // Make sure that JGit uses the exact same authentication information as ORT itself. This addresses
            // discrepancies in the way .netrc files are interpreted between JGit's and ORT's implementation.
            CredentialsProvider.setDefault(AuthenticatorCredentialsProvider)

            // Create a dummy key database that accepts any key from any (unknown) host.
            val dummyKeyDatabase = object : ServerKeyDatabase {
                override fun lookup(
                    connectAddress: String,
                    remoteAddress: InetSocketAddress,
                    config: ServerKeyDatabase.Configuration
                ) = emptyList<PublicKey>()

                override fun accept(
                    connectAddress: String,
                    remoteAddress: InetSocketAddress,
                    serverKey: PublicKey,
                    config: ServerKeyDatabase.Configuration,
                    provider: CredentialsProvider?
                ) = true
            }

            val sessionFactory = object : SshdSessionFactory(JGitKeyCache(), DefaultProxyDataFactory()) {
                override fun getServerKeyDatabase(homeDir: File, sshDir: File) = dummyKeyDatabase
            }

            SshSessionFactory.setInstance(sessionFactory)
        }
    }

    private val versionRegex = Regex("[Gg]it [Vv]ersion (?<version>[\\d.a-z-]+)(\\s.+)?")

    override val type = VcsType.GIT
    override val priority = 100
    override val latestRevisionNames = listOf("HEAD", "@")

    override fun command(workingDir: File?) = "git"

    override fun getVersion() = getVersion(null)

    // Require at least Git 2.29 on the client side as it has protocol "v2" enabled by default.
    override fun getVersionRequirement(): RangesList = RangesListFactory.create(">=2.29")

    override fun getDefaultBranchName(url: String): String {
        val refs = Git.lsRemoteRepository().setRemote(url).callAsMap()
        return (refs["HEAD"] as? SymbolicRef)?.target?.name?.removePrefix("refs/heads/") ?: "master"
    }

    override fun transformVersion(output: String): String =
        versionRegex.matchEntire(output.lineSequence().first())?.let { match ->
            match.groups["version"]!!.value
        }.orEmpty()

    override fun getWorkingTree(vcsDirectory: File): WorkingTree = GitWorkingTree(
        workingDir = vcsDirectory,
        vcsType = type,
        repositoryUrlPrefixReplacements = REPOSITORY_URL_PREFIX_REPLACEMENTS
    )

    override fun isApplicableUrlInternal(vcsUrl: String): Boolean =
        runCatching {
            LsRemoteCommand(null).setRemote(vcsUrl).call().isNotEmpty()
        }.onFailure {
            logger.debug { "Failed to check whether $type is applicable for $vcsUrl: ${it.collectMessages()}" }
        }.isSuccess

    override fun initWorkingTree(targetDir: File, vcs: VcsInfo): WorkingTree {
        try {
            Git.init().setDirectory(targetDir).call().use { git ->
                git.remoteAdd().setName("origin").setUri(URIish(vcs.url)).call()

                if (Os.isWindows) {
                    git.repository.config.setBoolean("core", null, "longpaths", true)
                }

                if (vcs.path.isNotBlank()) {
                    logger.info { "Configuring Git to do sparse checkout of path '${vcs.path}'." }

                    git.repository.config.setBoolean("core", null, "sparseCheckout", true)

                    val gitInfoDir = targetDir.resolve(".git/info").safeMkdirs()
                    val path = vcs.path.let { if (it.startsWith("/")) it else "/$it" }
                    val globPatterns = getSparseCheckoutGlobPatterns() + path

                    gitInfoDir.resolve("sparse-checkout").writeText(globPatterns.joinToString("\n"))
                }

                git.repository.config.save()
            }
        } catch (e: GitAPIException) {
            throw IOException("Unable to initialize $type working tree at directory '$targetDir'.", e)
        }

        return getWorkingTree(targetDir)
    }

    override fun updateWorkingTree(
        workingTree: WorkingTree,
        revision: String,
        path: String,
        recursive: Boolean
    ): Result<String> =
        (workingTree as GitWorkingTree).useRepo {
            Git(this).use { git ->
                logger.info { "Updating working tree from '${workingTree.getRemoteUrl()}'." }

                updateWorkingTreeWithoutSubmodules(git, revision).mapCatching {
                    // In case this throws the exception gets encapsulated as a failure.
                    if (recursive) updateSubmodules(workingTree)

                    revision
                }
            }
        }

    private fun updateWorkingTreeWithoutSubmodules(git: Git, revision: String): Result<String> =
        runCatching {
            logger.info { "Trying to fetch only revision '$revision' with depth limited to $GIT_HISTORY_DEPTH." }

            val fetch = git.fetch().setDepth(GIT_HISTORY_DEPTH)

            // See https://git-scm.com/docs/gitrevisions#_specifying_revisions for how Git resolves ambiguous
            // names.
            runCatching { fetch.setRefSpecs(revision).call() }
                .recoverCatching {
                    // Note that in contrast to branches / heads, Git does not namespace tags per remote.
                    val tagRefSpec = "+${Constants.R_TAGS}$revision:${Constants.R_TAGS}$revision"
                    fetch.setRefSpecs(tagRefSpec).call()
                }
                .recoverCatching {
                    val branchRefSpec = "+${Constants.R_HEADS}$revision:${Constants.R_REMOTES}origin/$revision"
                    fetch.setRefSpecs(branchRefSpec).call()
                }
                .getOrThrow()
        }.recoverCatching {
            it.showStackTrace()

            logger.info { "Could not fetch only revision '$revision': ${it.collectMessages()}" }
            logger.info { "Falling back to fetching all refs with depth limited to $GIT_HISTORY_DEPTH." }

            git.fetch().setDepth(GIT_HISTORY_DEPTH).setTagOpt(TagOpt.FETCH_TAGS).call()
        }.recoverCatching {
            it.showStackTrace()

            logger.info { "Could not fetch with only a depth of $GIT_HISTORY_DEPTH: ${it.collectMessages()}" }
            logger.info { "Falling back to fetch everything including tags." }

            git.fetch().setUnshallow(true).setTagOpt(TagOpt.FETCH_TAGS).call()
        }.recoverCatching {
            it.showStackTrace()

            logger.info { "Could not fetch everything using JGit: ${it.collectMessages()}" }
            logger.info { "Falling back to Git CLI." }

            val workingTree = GitWorkingTree(git.repository.workTree, VcsType.GIT)
            if (workingTree.isShallow()) {
                workingTree.runGit("fetch", "--unshallow", "--tags", "origin")
            } else {
                workingTree.runGit("fetch", "--tags", "origin")
            }
        }.onFailure {
            it.showStackTrace()

            logger.warn { "Failed to fetch everything: ${it.collectMessages()}" }
        }.mapCatching {
            // TODO: Migrate this to JGit once https://bugs.eclipse.org/bugs/show_bug.cgi?id=383772 is implemented.
            run("checkout", revision, workingDir = git.repository.workTree)

            revision
        }

    private fun updateSubmodules(workingTree: WorkingTree) {
        if (!workingTree.getRootPath().resolve(".gitmodules").isFile) return

        val configOption = REPOSITORY_URL_PREFIX_REPLACEMENTS.flatMap { (prefix, replacement) ->
            listOf("-c", "url.$replacement.insteadOf=$prefix")
        }.toTypedArray()

        runCatching {
            // TODO: Migrate this to JGit once https://bugs.eclipse.org/bugs/show_bug.cgi?id=580731 is implemented.
            workingTree.runGit(
                *configOption, "submodule", "update", "--init", "--recursive", "--depth", "$GIT_HISTORY_DEPTH"
            )
        }.recover {
            // As Git's dumb HTTP transport does not support shallow capabilities, also try to not limit the depth.
            workingTree.runGit(*configOption, "submodule", "update", "--recursive")
        }
    }

    private fun WorkingTree.runGit(vararg args: String) = run(*args, workingDir = workingDir)
}

/**
 * A special JGit [CredentialsProvider] implementation that delegates requests for credentials to the current
 * [Authenticator]. An instance of this class is installed by [Git], making sure that JGit uses the exact same
 * authentication mechanism as ORT.
 */
internal object AuthenticatorCredentialsProvider : CredentialsProvider(), Logging {
    override fun isInteractive(): Boolean = false

    override fun supports(vararg items: CredentialItem): Boolean =
        items.all {
            it is CredentialItem.Username || it is CredentialItem.Password
        }

    override fun get(uri: URIish, vararg items: CredentialItem): Boolean {
        logger.debug { "JGit queries credentials ${items.map { it.javaClass.simpleName }} for '${uri.host}'." }

        val auth = requestPasswordAuthentication(uri.host, uri.port, uri.scheme) ?: return false

        logger.debug { "Passing credentials for '${uri.host}' to JGit." }

        items.forEach { item ->
            when (item) {
                is CredentialItem.Username -> item.value = auth.userName
                is CredentialItem.Password -> item.value = auth.password
                else -> throw UnsupportedCredentialItem(uri, "${item.javaClass.simpleName}: ${item.promptText}")
            }
        }

        return true
    }
}
