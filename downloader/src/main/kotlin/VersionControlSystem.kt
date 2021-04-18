/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.downloader

import com.vdurmont.semver4j.Semver

import java.io.File
import java.io.IOException
import java.util.ServiceLoader

import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.LicenseFilenamePatterns
import org.ossreviewtoolkit.utils.CommandLineTool
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.showStackTrace
import org.ossreviewtoolkit.utils.uppercaseFirstChar

abstract class VersionControlSystem {
    companion object {
        private val LOADER = ServiceLoader.load(VersionControlSystem::class.java)!!

        /**
         * The (prioritized) list of all available Version Control Systems in the classpath.
         */
        val ALL by lazy { LOADER.iterator().asSequence().toList().sortedByDescending { it.priority } }

        /**
         * Return the applicable VCS for the given [vcsType], or null if none is applicable.
         */
        fun forType(vcsType: VcsType) = ALL.find {
            it.isAvailable() && it.isApplicableType(vcsType)
        }

        /**
         * A map to cache the [VersionControlSystem], if any, for previously queried URLs. This helps to speed up
         * subsequent queries for the same URLs as identifying the [VersionControlSystem] for arbitrary URLs might
         * require network access.
         */
        private val urlToVcsMap = mutableMapOf<String, VersionControlSystem?>()

        /**
         * Return the applicable VCS for the given [vcsUrl], or null if none is applicable.
         */
        @Synchronized
        fun forUrl(vcsUrl: String) =
            if (vcsUrl in urlToVcsMap) {
                urlToVcsMap[vcsUrl]
            } else {
                ALL.find {
                    it.isAvailable() && it.isApplicableUrl(vcsUrl)
                }.also {
                    urlToVcsMap[vcsUrl] = it
                }
            }

        /**
         * A map to cache the [WorkingTree], if any, for previously queried directories. This helps to speed up
         * subsequent queries for the same directories and to reduce log output from running external VCS tools.
         */
        private val dirToVcsMap = mutableMapOf<File, WorkingTree?>()

        /**
         * Return the applicable VCS working tree for the given [vcsDirectory], or null if none is applicable.
         */
        @Synchronized
        fun forDirectory(vcsDirectory: File): WorkingTree? {
            val absoluteVcsDirectory = vcsDirectory.absoluteFile

            return if (absoluteVcsDirectory in dirToVcsMap) {
                dirToVcsMap[absoluteVcsDirectory]
            } else {
                ALL.asSequence().mapNotNull {
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

                        log.debug {
                            "Exception while validating ${it.vcsType} working tree, treating it as non-applicable: " +
                                    e.collectMessagesAsString()
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
        fun getCloneInfo(workingDir: File) = forDirectory(workingDir)?.getInfo() ?: VcsInfo.EMPTY

        /**
         * Return all VCS information about a specific [path]. If [path] points to a nested VCS (like a Git submodule or
         * a separate Git repository within a GitRepo working tree), information for that nested VCS is returned.
         */
        fun getPathInfo(path: File): VcsInfo {
            val dir = path.takeIf { it.isDirectory } ?: path.parentFile
            return forDirectory(dir)?.let { workingTree ->
                // Always return the relative path to the (nested) VCS root.
                workingTree.getInfo().copy(path = workingTree.getPathToRoot(path))
            } ?: VcsInfo.EMPTY
        }

        /**
         * Return glob patterns matching all potential license or patent files.
         */
        internal fun getLicenseFileGlobPatterns(): List<String> =
            LicenseFilenamePatterns.getInstance().allLicenseFilenames.generateCapitalizationVariants().map { "**/$it" }

        private fun Collection<String>.generateCapitalizationVariants() =
            flatMap { listOf(it, it.uppercase(), it.uppercaseFirstChar()) }
    }

    /**
     * The [VcsType] of this [VersionControlSystem].
     */
    abstract val type: VcsType

    /**
     * The priority in which this VCS should be probed. A higher value means a higher priority.
     */
    protected open val priority: Int = 0

    /**
     * A list of symbolic names that point to the latest revision.
     */
    protected abstract val latestRevisionNames: List<String>

    /**
     * Return the VCS command's version string, or an empty string if the version cannot be determined.
     */
    abstract fun getVersion(): String

    /**
     * Return the name of the default branch for the repository at [url], or null if there is no default remote branch.
     */
    abstract fun getDefaultBranchName(url: String): String?

    /**
     * Return a working tree instance for this VCS.
     */
    abstract fun getWorkingTree(vcsDirectory: File): WorkingTree

    /**
     * Return true if this VCS can handle the given [vcsType].
     */
    fun isApplicableType(vcsType: VcsType) = vcsType == type

    /**
     * Return true if this [VersionControlSystem] can be used to download from the provided [vcsUrl]. First, try to find
     * this out by only parsing the URL, but as a fallback implementations may actually probe the URL and make a network
     * request.
     */
    fun isApplicableUrl(vcsUrl: String): Boolean {
        if (vcsUrl.isBlank() || vcsUrl.endsWith(".html")) return false

        return VcsHost.toVcsInfo(vcsUrl).type == type || isApplicableUrlInternal(vcsUrl)
    }

    /**
     * Return true if this [VersionControlSystem] is available for use.
     */
    fun isAvailable(): Boolean = this !is CommandLineTool || isInPath()

    protected abstract fun isApplicableUrlInternal(vcsUrl: String): Boolean

    /**
     * Download the source code as specified by the [pkg] information to [targetDir]. [allowMovingRevisions] toggles
     * whether symbolic names for which the revision they point might change are accepted or not. If [recursive] is
     * true, any nested repositories (like Git submodules or Mercurial subrepositories) are downloaded, too.
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

        // E.g. for NPM packages is it sometimes the case that the "gitHead" from the registry points to a non-fetchable
        // commit, but the repository still has a tag for the package version (pointing to a different commit). In order
        // to allow to fall back to the guessed revision based on the version in such cases, use a prioritized list of
        // revision candidates instead of a single revision.
        val revisionCandidates = mutableSetOf<String>()

        val emptyRevisionCandidatesException = DownloadException("Unable to determine a revision to checkout.")

        runCatching {
            pkg.vcsProcessed.revision.also {
                if (it.isNotBlank() && (allowMovingRevisions || isFixedRevision(workingTree, it))) {
                    if (revisionCandidates.add(it)) {
                        log.info {
                            "Adding $type revision '$it' (taken from package meta-data) as a candidate."
                        }
                    }
                }
            }
        }.onFailure {
            it.showStackTrace()

            log.info {
                "Meta-data has invalid $type revision '${pkg.vcsProcessed.revision}': ${it.collectMessagesAsString()}"
            }

            emptyRevisionCandidatesException.addSuppressed(it)
        }

        fun addGuessedRevision(project: String, version: String): Boolean =
            runCatching {
                workingTree.guessRevisionName(project, version).also {
                    if (revisionCandidates.add(it)) {
                        log.info {
                            "Adding $type revision '$it' (guessed from package '$project' and version " +
                                    "'$version') as a candidate."
                        }
                    }
                }
            }.onFailure {
                it.showStackTrace()

                log.info {
                    "No $type revision for package '$project' and version '$version' found: " +
                            it.collectMessagesAsString()
                }

                emptyRevisionCandidatesException.addSuppressed(it)
            }.isSuccess

        if (!addGuessedRevision(pkg.id.name, pkg.id.version)) {
            when {
                pkg.id.type == "NPM" && pkg.id.namespace.isNotEmpty() -> {
                    // Fallback for Lerna workspaces when scoped packages combined with independent versioning are used,
                    // e.g. support Git tag of the format "@organisation/my-component@x.x.x".
                    addGuessedRevision("${pkg.id.namespace}/${pkg.id.name}", pkg.id.version)
                }

                pkg.id.type == "GoMod" && pkg.vcsProcessed.path.isNotEmpty() -> {
                    // Fallback for GoMod packages from mono repos which use the tag format described in
                    // https://golang.org/ref/mod#vcs-version.
                    val tag = "${pkg.vcsProcessed.path}/${pkg.id.version}"

                    if (tag in workingTree.listRemoteTags()) revisionCandidates += tag
                }
            }
        }

        if (revisionCandidates.isEmpty()) throw emptyRevisionCandidatesException

        val results = mutableListOf<Result<String>>()

        revisionCandidates.forEachIndexed { index, revision ->
            log.info { "Trying revision candidate '$revision' (${index + 1} of ${revisionCandidates.size})..." }
            results += updateWorkingTree(workingTree, revision, pkg.vcsProcessed.path, recursive)
            if (results.last().isSuccess) return@forEachIndexed
        }

        val workingTreeRevision = results.last().getOrElse {
            throw DownloadException("$type failed to download from URL '${pkg.vcsProcessed.url}'.", it)
        }

        pkg.vcsProcessed.path.let {
            if (it.isNotBlank() && !workingTree.workingDir.resolve(it).exists()) {
                throw DownloadException(
                    "The $type working directory at '${workingTree.workingDir}' does not contain the requested path " +
                            "'$it'."
                )
            }
        }

        log.info {
            "Successfully downloaded revision $workingTreeRevision for package '${pkg.id.toCoordinates()}.'."
        }

        return workingTree
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
     * Check whether the given [revision] is likely to name a fixed revision that does not move.
     */
    fun isFixedRevision(workingTree: WorkingTree, revision: String) =
        revision.isNotBlank()
                && revision !in latestRevisionNames
                && (revision !in workingTree.listRemoteBranches() || revision in workingTree.listRemoteTags())

    /**
     * Check whether the VCS tool is at least of the specified [expectedVersion], e.g. to check for features.
     */
    fun isAtLeastVersion(expectedVersion: String): Boolean {
        val actualVersion = Semver(getVersion(), Semver.SemverType.LOOSE)
        return actualVersion.isGreaterThanOrEqualTo(expectedVersion)
    }
}
