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

package org.ossreviewtoolkit.downloader

import java.io.File
import java.io.IOException

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.LicenseFilePatterns
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.plugins.api.Plugin
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.titlecase
import org.ossreviewtoolkit.utils.ort.ORT_REPO_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.ort.showStackTrace

import org.semver4j.Semver

abstract class VersionControlSystem : Plugin {
    companion object {
        private fun getAllVcsByPriority(configs: Map<String, PluginConfig>) =
            VersionControlSystemFactory.ALL.map { (id, factory) ->
                val config = configs[id] ?: PluginConfig.EMPTY
                factory.create(config)
            }.sortedByDescending {
                it.priority
            }

        /**
         * Return the applicable VCS for the given [vcsType], or null if none is applicable.
         */
        fun forType(vcsType: VcsType, configs: Map<String, PluginConfig> = emptyMap()) =
            getAllVcsByPriority(configs).find { vcs ->
                vcs.type == vcsType && vcs.isAvailable()
            }

        /**
         * A map to cache the [VersionControlSystem], if any, for previously queried URLs and their respective plugin
         * configurations. This helps to speed up subsequent queries for the same URLs as identifying the
         * [VersionControlSystem] for arbitrary URLs might require network access.
         */
        private val urlToVcsMap = mutableMapOf<Pair<String, Map<String, PluginConfig>>, VersionControlSystem?>()

        /**
         * Return the applicable VCS for the given [vcsUrl], or null if none is applicable.
         */
        @Synchronized
        fun forUrl(vcsUrl: String, configs: Map<String, PluginConfig> = emptyMap()) =
            // Do not use getOrPut() here as it cannot handle null values, also see
            // https://youtrack.jetbrains.com/issue/KT-21392.
            if (vcsUrl to configs in urlToVcsMap) {
                urlToVcsMap[vcsUrl to configs]
            } else {
                // First try to determine the VCS type statically...
                when (val type = VcsHost.parseUrl(vcsUrl).type) {
                    VcsType.UNKNOWN -> {
                        // ...then eventually try to determine the type also dynamically.
                        getAllVcsByPriority(configs).find { vcs ->
                            vcs.isApplicableUrl(vcsUrl) && vcs.isAvailable()
                        }
                    }

                    else -> forType(type, configs)
                }.also {
                    urlToVcsMap[vcsUrl to configs] = it
                }
            }

        /**
         * A map to cache the [WorkingTree], if any, for previously queried directories and their respective plugin
         * configurations. This helps to speed up subsequent queries for the same directories and to reduce log output
         * from running external VCS tools.
         */
        private val dirToVcsMap = mutableMapOf<Pair<File, Map<String, PluginConfig>>, WorkingTree?>()

        /**
         * Return the applicable VCS working tree for the given [vcsDirectory], or null if none is applicable.
         */
        @Synchronized
        fun forDirectory(vcsDirectory: File, configs: Map<String, PluginConfig> = emptyMap()): WorkingTree? {
            val absoluteVcsDirectory = vcsDirectory.absoluteFile

            return if (absoluteVcsDirectory to configs in dirToVcsMap) {
                dirToVcsMap[absoluteVcsDirectory to configs]
            } else {
                getAllVcsByPriority(configs).mapNotNull {
                    if (it is CommandLineTool && !it.isInPath()) {
                        null
                    } else {
                        it.getWorkingTree(absoluteVcsDirectory)
                    }
                }.find {
                    try {
                        it.isValid()
                    } catch (e: IOException) {
                        e.showStackTrace()

                        logger.debug {
                            "Exception while validating ${it.vcsType} working tree, treating it as non-applicable: " +
                                e.collectMessages()
                        }

                        false
                    }
                }.also {
                    dirToVcsMap[absoluteVcsDirectory to configs] = it
                }
            }
        }

        /**
         * Return all VCS information about a [workingDir]. This is a convenience wrapper around [WorkingTree.getInfo].
         */
        fun getCloneInfo(workingDir: File): VcsInfo = forDirectory(workingDir)?.getInfo().orEmpty()

        /**
         * Return all VCS information about a specific [path]. If [path] points to a nested VCS (like a Git submodule or
         * a separate Git repository within a GitRepo working tree), information for that nested VCS is returned.
         */
        fun getPathInfo(path: File): VcsInfo {
            val dir = path.takeIf { it.isDirectory } ?: path.parentFile
            return forDirectory(dir)?.let { workingTree ->
                // Always return the relative path to the (nested) VCS root.
                workingTree.getInfo().copy(path = workingTree.getPathToRoot(path))
            }.orEmpty()
        }

        /**
         * Return glob patterns for files that should be checkout out in addition to explicit sparse checkout paths.
         */
        fun getSparseCheckoutGlobPatterns(): List<String> {
            val globPatterns = mutableListOf("*$ORT_REPO_CONFIG_FILENAME")
            val licensePatterns = LicenseFilePatterns.getInstance()
            return licensePatterns.allLicenseFilenames.generateCapitalizationVariants().mapTo(globPatterns) { "**/$it" }
        }

        private fun Collection<String>.generateCapitalizationVariants() =
            flatMap { listOf(it, it.uppercase(), it.titlecase()) }
    }

    /**
     * The type of VCS supported by this implementation.
     */
    abstract val type: VcsType

    /**
     *  The [priority] defines the order in which VCS implementations are to be used. A higher value means a higher
     *  priority.
     */
    abstract val priority: Int

    /**
     * A list of symbolic names that point to the latest revision.
     */
    protected abstract val latestRevisionNames: List<String>

    /**
     * Return the VCS command's version string, or an empty string if the version cannot be determined.
     */
    abstract fun getVersion(): String

    /**
     * Return the name of the default branch for the repository at [url]. It is expected that there always is a default
     * branch name that implementations can fall back to, and that the returned name is non-empty.
     */
    abstract fun getDefaultBranchName(url: String): String

    /**
     * Return a working tree instance for this VCS.
     */
    abstract fun getWorkingTree(vcsDirectory: File): WorkingTree

    /**
     * Return true if this [VersionControlSystem] can be used to download from the provided [vcsUrl]. First, try to find
     * this out by only parsing the URL, but as a fallback implementations may actually probe the URL and make a network
     * request.
     */
    fun isApplicableUrl(vcsUrl: String): Boolean {
        if (vcsUrl.isBlank() || vcsUrl.endsWith(".html")) return false

        return type == VcsHost.parseUrl(vcsUrl).type || isApplicableUrlInternal(vcsUrl)
    }

    /**
     * Return true if this [VersionControlSystem] is available for use.
     */
    open fun isAvailable(): Boolean = true

    /**
     * Test - in a way specific to this [VersionControlSystem] - whether it can be used to download from the provided
     * [vcsUrl]. This function is called by [isApplicableUrl] if it cannot be determined from parsing the [vcsUrl]
     * whether it is applicable for this [VersionControlSystem] or not. A concrete implementation can do specific
     * checks here that may also include network requests.
     */
    protected abstract fun isApplicableUrlInternal(vcsUrl: String): Boolean

    /**
     * Download the source code as specified by the [pkg] information to [targetDir]. [allowMovingRevisions] toggles
     * whether to allow downloads using symbolic names that point to moving revisions, like Git branches. If [recursive]
     * is `true`, any nested repositories (like Git submodules or Mercurial subrepositories) are downloaded, too.
     *
     * @return An object describing the downloaded working tree.
     *
     * @throws DownloadException in case the download failed.
     */
    fun download(
        pkg: Package,
        targetDir: File,
        allowMovingRevisions: Boolean = false,
        recursive: Boolean = true
    ): WorkingTree {
        val workingTree = try {
            initWorkingTree(targetDir, pkg.vcsProcessed)
        } catch (e: IOException) {
            throw DownloadException("Failed to initialize $type working tree at '$targetDir'.", e)
        }

        val revisionCandidates = getRevisionCandidates(workingTree, pkg, allowMovingRevisions).getOrElse {
            throw DownloadException("$type failed to get revisions from URL ${pkg.vcsProcessed.url}.", it)
        }

        val results = mutableListOf<Result<String>>()

        for ((index, revision) in revisionCandidates.withIndex()) {
            logger.info { "Trying revision candidate '$revision' (${index + 1} of ${revisionCandidates.size})..." }
            results += updateWorkingTree(workingTree, revision, pkg.vcsProcessed.path, recursive)
            if (results.last().isSuccess) break
        }

        val workingTreeRevision = results.last().getOrElse {
            throw DownloadException(
                "$type failed to download from ${pkg.vcsProcessed.url} to '${workingTree.getRootPath()}'.", it
            )
        }

        pkg.vcsProcessed.path.let {
            if (it.isNotBlank() && !workingTree.getRootPath().resolve(it).exists()) {
                throw DownloadException(
                    "The $type working directory at '${workingTree.getRootPath()}' does not contain the requested " +
                        "path '$it'."
                )
            }
        }

        logger.info {
            "Successfully downloaded revision '$workingTreeRevision' for package '${pkg.id.toCoordinates()}'."
        }

        return workingTree
    }

    /**
     * Get a list of distinct revision candidates for the [package][pkg]. The iteration order of the elements in the
     * list represents the priority of the revision candidates. If no revision candidates can be found a
     * [DownloadException] is thrown.
     *
     * The provided [workingTree] must have been created from the [processed VCS information][Package.vcsProcessed] of
     * the [package][pkg] for the function to return correct results.
     *
     * [allowMovingRevisions] toggles whether candidates with symbolic names that point to moving revisions, like Git
     * branches, are accepted or not.
     *
     * Revision candidates are created from the [processed VCS information][Package.vcsProcessed] of the [package][pkg]
     * and from [guessing revisions][WorkingTree.guessRevisionName] based on the name and version of the [package][pkg].
     * This is useful when the metadata of the package does not contain a revision or if the revision points to a
     * non-fetchable commit, but the repository still has a tag for the package version.
     */
    fun getRevisionCandidates(
        workingTree: WorkingTree,
        pkg: Package,
        allowMovingRevisions: Boolean
    ): Result<List<String>> {
        val revisionCandidates = mutableListOf<String>()
        val emptyRevisionCandidatesException = DownloadException("Unable to determine a revision to checkout.")

        fun addGuessedRevision(project: String, version: String): Boolean =
            runCatching {
                workingTree.guessRevisionName(project, version).also {
                    if (it !in revisionCandidates) {
                        logger.info {
                            "Adding $type revision '$it' (guessed from package '$project' and version '$version') as " +
                                "a candidate."
                        }

                        revisionCandidates += it
                    }
                }
            }.onFailure {
                logger.info {
                    "No $type revision for package '$project' and version '$version' found: " +
                        it.collectMessages()
                }

                emptyRevisionCandidatesException.addSuppressed(it)
            }.isSuccess

        fun addMetadataRevision(revision: String) {
            if (revision.isBlank() || revision in revisionCandidates) return

            isFixedRevision(workingTree, revision).onSuccess { isFixedRevision ->
                if (isFixedRevision) {
                    logger.info {
                        "Adding $type fixed revision '$revision' (taken from package metadata) as a candidate."
                    }

                    // Add a fixed revision from package metadata with the highest priority.
                    revisionCandidates.add(0, revision)
                } else if (allowMovingRevisions) {
                    logger.info {
                        "Adding $type moving revision '$revision' (taken from package metadata) as a candidate."
                    }

                    // Add a moving revision from package metadata with lower priority than guessed fixed revisions.
                    revisionCandidates += revision
                }
            }.onFailure {
                logger.info {
                    "Metadata has invalid $type revision '$revision': ${it.collectMessages()}"
                }

                emptyRevisionCandidatesException.addSuppressed(it)
            }
        }

        if (!addGuessedRevision(pkg.id.name, pkg.id.version)) {
            when {
                pkg.id.type == "NPM" && pkg.id.namespace.isNotEmpty() -> {
                    // Fallback for Lerna workspaces when scoped packages combined with independent versioning are used,
                    // e.g. support Git tag of the format "@organisation/my-component@x.x.x".
                    addGuessedRevision("${pkg.id.namespace}/${pkg.id.name}", pkg.id.version)
                }
            }
        }

        addMetadataRevision(pkg.vcsProcessed.revision)

        if (type == VcsType.GIT && pkg.vcsProcessed.revision == "master") {
            // Also try with Git's upcoming default branch name in case the repository is already using it.
            addMetadataRevision("main")
        }

        return if (revisionCandidates.isEmpty()) {
            Result.failure(emptyRevisionCandidatesException)
        } else {
            Result.success(revisionCandidates)
        }
    }

    /**
     * Initialize the working tree without checking out any files yet.
     *
     * @throws IOException in case the initialization failed.
     */
    abstract fun initWorkingTree(targetDir: File, vcs: VcsInfo): WorkingTree

    /**
     * Update the [working tree][workingTree] by checking out the given [revision], optionally limited to the given
     * [path] and [recursively][recursive] updating any nested working trees. Return a [Result] that encapsulates the
     * originally requested [revision] on success, or the occurred exception on failure.
     */
    abstract fun updateWorkingTree(
        workingTree: WorkingTree,
        revision: String,
        path: String = "",
        recursive: Boolean = false
    ): Result<String>

    /**
     * Check whether the given [revision] is likely to name a fixed revision that does not move. Return a [Result] with
     * a [Boolean] on success, or with a [Throwable] is there was a failure.
     */
    fun isFixedRevision(workingTree: WorkingTree, revision: String): Result<Boolean> =
        runCatching {
            revision.isNotBlank()
                && revision !in latestRevisionNames
                && (revision !in workingTree.listRemoteBranches() || revision in workingTree.listRemoteTags())
        }

    /**
     * Check whether the VCS tool is at least of the specified [expectedVersion], e.g. to check for features.
     */
    fun isAtLeastVersion(expectedVersion: String): Boolean {
        val actualVersion = Semver.coerce(getVersion())
        return Semver.coerce(expectedVersion)?.let { actualVersion?.isGreaterThanOrEqualTo(it) } == true
    }
}
