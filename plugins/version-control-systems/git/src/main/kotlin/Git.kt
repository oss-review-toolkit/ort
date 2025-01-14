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

package org.ossreviewtoolkit.plugins.versioncontrolsystems.git

import java.io.File
import java.io.IOException
import java.net.Authenticator
import java.net.InetSocketAddress
import java.security.PublicKey

import org.apache.logging.log4j.kotlin.logger

import org.eclipse.jgit.api.Git as JGit
import org.eclipse.jgit.api.LsRemoteCommand
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.errors.UnsupportedCredentialItem
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectIdRef
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
import org.ossreviewtoolkit.downloader.VersionControlSystemFactory
import org.ossreviewtoolkit.downloader.WorkingTree
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Options
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.ort.requestPasswordAuthentication
import org.ossreviewtoolkit.utils.ort.showStackTrace

import org.semver4j.RangesList
import org.semver4j.RangesListFactory

const val DEFAULT_HISTORY_DEPTH = 50

// Replace prefixes of Git submodule repository URLs.
private val REPOSITORY_URL_PREFIX_REPLACEMENTS = listOf(
    "git://" to "https://"
)

object GitCommand : CommandLineTool {
    private val versionRegex = Regex("[Gg]it [Vv]ersion (?<version>[\\d.a-z-]+)(\\s.+)?")

    override fun command(workingDir: File?) = "git"

    // Require at least Git 2.29 on the client side as it has protocol "v2" enabled by default.
    override fun getVersionRequirement(): RangesList = RangesListFactory.create(">=2.29")

    override fun transformVersion(output: String): String =
        versionRegex.matchEntire(output.lineSequence().first())?.let { match ->
            @Suppress("UnsafeCallOnNullableType")
            match.groups["version"]!!.value
        }.orEmpty()

    override fun displayName(): String = "Git"
}

/**
 * This class provides functionality for interacting with Git repositories, utilizing either
 * [JGit][org.eclipse.jgit.api.Git] or the Git CLI for executing operations.
 *
 * The following [configuration options][GitConfig] are available:
 * - *historyDepth*: Depth of the commit history to fetch. Defaults to [DEFAULT_HISTORY_DEPTH].
 * - *updateNestedSubmodules*: Whether nested submodules should be updated, or if only top-level submodules should be
 *   considered. Defaults to true.
 */
class Git internal constructor(private val config: GitConfig) : VersionControlSystem() {
    companion object {
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

    class Factory : VersionControlSystemFactory<GitConfig>(VcsType.GIT.toString(), 100) {
        override fun create(config: GitConfig) = Git(config)

        override fun parseConfig(options: Options, secrets: Options) =
            GitConfig(
                historyDepth = options["historyDepth"]?.toIntOrNull() ?: DEFAULT_HISTORY_DEPTH,
                updateNestedSubmodules = options["updateNestedSubmodules"]?.toBooleanStrictOrNull() ?: true
            )
    }

    override val type = VcsType.GIT
    override val latestRevisionNames = listOf("HEAD", "@")

    override fun getVersion() = GitCommand.getVersion()

    override fun getDefaultBranchName(url: String): String {
        val refs = JGit.lsRemoteRepository().setRemote(url).callAsMap()
        return (refs["HEAD"] as? SymbolicRef)?.target?.name?.removePrefix("refs/heads/") ?: "master"
    }

    override fun getWorkingTree(vcsDirectory: File): WorkingTree = GitWorkingTree(vcsDirectory, type)

    override fun isAvailable(): Boolean = GitCommand.isInPath()

    override fun isApplicableUrlInternal(vcsUrl: String): Boolean =
        runCatching {
            LsRemoteCommand(null).setRemote(vcsUrl).call().isNotEmpty()
        }.onFailure {
            logger.debug { "Failed to check whether $type is applicable for $vcsUrl: ${it.collectMessages()}" }
        }.isSuccess

    override fun initWorkingTree(targetDir: File, vcs: VcsInfo): WorkingTree {
        try {
            JGit.init().setDirectory(targetDir).call().use { git ->
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
            JGit(this).use { git ->
                logger.info { "Updating working tree from ${workingTree.getRemoteUrl()}." }

                updateWorkingTreeWithoutSubmodules(workingTree, git, revision).mapCatching {
                    // In case this throws the exception gets encapsulated as a failure.
                    if (recursive) updateSubmodules(workingTree)

                    revision
                }
            }
        }

    /**
     * Update the [workingTree] using the [git] instance to a specific [revision] without updating any submodules. The
     * configured [historyDepth][GitConfig.historyDepth] is respected.
     */
    private fun updateWorkingTreeWithoutSubmodules(
        workingTree: WorkingTree,
        git: JGit,
        revision: String
    ): Result<String> =
        runCatching {
            logger.info { "Trying to fetch only revision '$revision' with depth limited to ${config.historyDepth}." }

            val fetch = git.fetch().setDepth(config.historyDepth)

            // See https://git-scm.com/docs/gitrevisions#_specifying_revisions for how Git resolves ambiguous
            // names. In particular, tag names have higher precedence than branch names.
            runCatching {
                fetch.setRefSpecs(revision).call()
            }.recoverCatching {
                // Note that in contrast to branches / heads, Git does not namespace tags per remote.
                val tagRefSpec = "+${Constants.R_TAGS}$revision:${Constants.R_TAGS}$revision"
                fetch.setRefSpecs(tagRefSpec).call()
            }.recoverCatching {
                val branchRefSpec = "+${Constants.R_HEADS}$revision:${Constants.R_REMOTES}origin/$revision"
                fetch.setRefSpecs(branchRefSpec).call()
            }.getOrThrow()
        }.recoverCatching {
            it.showStackTrace()

            logger.info { "Could not fetch only revision '$revision': ${it.collectMessages()}" }
            logger.info { "Falling back to fetching all refs with depth limited to ${config.historyDepth}." }

            git.fetch().setDepth(config.historyDepth).setTagOpt(TagOpt.FETCH_TAGS).call()
        }.recoverCatching {
            it.showStackTrace()

            logger.info { "Could not fetch with only a depth of ${config.historyDepth}: ${it.collectMessages()}" }
            logger.info { "Falling back to fetch everything including tags." }

            git.fetch().setUnshallow(true).setTagOpt(TagOpt.FETCH_TAGS).call()
        }.recoverCatching {
            it.showStackTrace()

            logger.info { "Could not fetch everything using JGit: ${it.collectMessages()}" }
            logger.info { "Falling back to Git CLI." }

            if (workingTree.isShallow()) {
                workingTree.runGit("fetch", "--unshallow", "--tags", "origin")
            } else {
                workingTree.runGit("fetch", "--tags", "origin")
            }

            // Do a dummy fetch call to create a JGit FetchResult based on the Git CLI call.
            git.fetch().call()
        }.onFailure {
            it.showStackTrace()

            logger.warn { "Failed to fetch everything: ${it.collectMessages()}" }
        }.mapCatching { fetchResult ->
            // TODO: Migrate this to JGit once sparse checkout (https://bugs.eclipse.org/bugs/show_bug.cgi?id=383772) is
            //       implemented. Also see the "reset" call below.
            GitCommand.run("checkout", revision, workingDir = workingTree.getRootPath()).requireSuccess()

            // In case of a non-fixed revision (branch or tag) reset the working tree to ensure that the previously
            // fetched changes are applied.
            if (!isFixedRevision(workingTree, revision).getOrThrow()) {
                val refs = fetchResult.advertisedRefs.filterIsInstance<ObjectIdRef>()

                val tags = refs.mapNotNull { ref ->
                    ref.name.withoutPrefix(Constants.R_TAGS)?.let { it to ref.objectId.name }
                }.toMap()

                logger.debug { "Tags fetched: ${tags.keys.joinToString()}" }

                val branches = refs.mapNotNull { ref ->
                    ref.name.withoutPrefix(Constants.R_HEADS)?.let { it to ref.objectId.name }
                }.toMap()

                logger.debug { "Branches fetched: ${branches.keys.joinToString()}" }

                // Tag names have higher precedence than branch names in case of ambiguity.
                val resolvedRevision = checkNotNull(tags[revision] ?: branches[revision]) {
                    "Requested revision '$revision' not found in refs advertised by the server."
                }

                GitCommand.run("reset", "--hard", resolvedRevision, workingDir = workingTree.getRootPath())
                    .requireSuccess()
            }

            revision
        }

    /**
     * Initialize and / or update the submodules in [workingTree], limiting the commit history to the configured
     * [historyDepth][GitConfig.historyDepth]. If [updateNestedSubmodules][GitConfig.updateNestedSubmodules] is true,
     * submodules are initialized / updated recursively.
     */
    private fun updateSubmodules(workingTree: WorkingTree) {
        if (!workingTree.getRootPath().resolve(".gitmodules").isFile) return

        val recursive = "--recursive".takeIf { config.updateNestedSubmodules }

        val insteadOf = REPOSITORY_URL_PREFIX_REPLACEMENTS.map { (prefix, replacement) ->
            "url.$replacement.insteadOf $prefix"
        }

        runCatching {
            // TODO: Migrate this to JGit once https://bugs.eclipse.org/bugs/show_bug.cgi?id=580731 is implemented.
            val updateArgs = listOfNotNull(
                "submodule", "update", "--init", recursive, "--depth", "${config.historyDepth}"
            )
            workingTree.runGit(*updateArgs.toTypedArray())

            insteadOf.forEach {
                val foreachArgs = listOfNotNull(
                    "submodule", "foreach", recursive, "git config $it"
                )
                workingTree.runGit(*foreachArgs.toTypedArray())
            }
        }.recover {
            // As Git's dumb HTTP transport does not support shallow capabilities, also try to not limit the depth.
            val updateArgs = listOfNotNull("submodule", "update", recursive)
            workingTree.runGit(*updateArgs.toTypedArray())
        }
    }

    private fun WorkingTree.runGit(vararg args: String) =
        GitCommand.run(*args, workingDir = workingDir).requireSuccess()
}

/**
 * A special JGit [CredentialsProvider] implementation that delegates requests for credentials to the current
 * [Authenticator]. An instance of this class is installed by [Git], making sure that JGit uses the exact same
 * authentication mechanism as ORT.
 */
internal object AuthenticatorCredentialsProvider : CredentialsProvider() {
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
