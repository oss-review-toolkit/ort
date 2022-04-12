/*
 * Copyright (C) 2021 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.scanner.experimental

import java.io.IOException
import java.net.HttpURLConnection

import kotlinx.coroutines.runBlocking

import okhttp3.Request

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.SourceCodeOrigin
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.utils.common.collectMessagesAsString
import org.ossreviewtoolkit.utils.core.OkHttpClientHelper
import org.ossreviewtoolkit.utils.core.log
import org.ossreviewtoolkit.utils.core.showStackTrace

/**
 * The [PackageProvenanceResolver] provides a function to resolve the [Provenance] of a [Package].
 */
interface PackageProvenanceResolver {
    /**
     * Resolve the [KnownProvenance] of [pkg] based on the provided [sourceCodeOriginPriority].
     *
     * Throws an [IOException] if the provenance cannot be resolved.
     */
    fun resolveProvenance(pkg: Package, sourceCodeOriginPriority: List<SourceCodeOrigin>): KnownProvenance
}

/**
 * The default implementation of [PackageProvenanceResolver].
 */
class DefaultPackageProvenanceResolver(
    private val storage: PackageProvenanceStorage,
    private val workingTreeCache: WorkingTreeCache
) : PackageProvenanceResolver {
    /**
     * Resolve the [Provenance] of [pkg] based on the provided [sourceCodeOriginPriority]. For source artifacts it is
     * verified that the [RemoteArtifact] does exist. For a VCS it is verified that the revision exists. If the revision
     * provided by the [package][pkg] metadata does not exist or is missing, the function tries to guess the tag based
     * on the name and version of the [package][pkg].
     */
    override fun resolveProvenance(pkg: Package, sourceCodeOriginPriority: List<SourceCodeOrigin>): KnownProvenance {
        val errors = mutableMapOf<SourceCodeOrigin, Throwable>()

        sourceCodeOriginPriority.forEach { sourceCodeOrigin ->
            runCatching {
                when (sourceCodeOrigin) {
                    SourceCodeOrigin.ARTIFACT -> {
                        if (pkg.sourceArtifact != RemoteArtifact.EMPTY) {
                            return resolveSourceArtifact(pkg)
                        }
                    }

                    SourceCodeOrigin.VCS -> {
                        if (pkg.vcsProcessed != VcsInfo.EMPTY) {
                            return runBlocking { resolveVcs(pkg) }
                        }
                    }
                }
            }.onFailure {
                it.showStackTrace()
                errors[sourceCodeOrigin] = it

                log.info {
                    "Could not resolve $sourceCodeOrigin for ${pkg.id.toCoordinates()}: ${it.collectMessagesAsString()}"
                }
            }
        }

        val message = buildString {
            append(
                "Could not resolve provenance for ${pkg.id.toCoordinates()} for source code origins " +
                        "$sourceCodeOriginPriority."
            )

            errors.forEach { (origin, throwable) ->
                append("\nResolution of $origin failed with:\n${throwable.collectMessagesAsString()}")
            }
        }

        log.info { message }

        throw IOException(message)
    }

    private fun resolveSourceArtifact(pkg: Package): ArtifactProvenance {
        when (val storedResult = storage.readProvenance(pkg.id, pkg.sourceArtifact)) {
            is ResolvedArtifactProvenance -> {
                log.info {
                    "Found a stored artifact resolution for package ${pkg.id.toCoordinates()}."
                }

                return storedResult.provenance
            }

            is UnresolvedPackageProvenance -> {
                log.info {
                    "Found a stored artifact resolution for package ${pkg.id.toCoordinates()} which failed " +
                            "previously. Not attempting resolution again. The error was: ${storedResult.message}"
                }

                throw IOException(storedResult.message)
            }

            else -> {
                log.info {
                    "Could not find a stored artifact resolution result for package ${pkg.id.toCoordinates()}," +
                            "attempting resolution."
                }
            }
        }

        val request = Request.Builder().head().url(pkg.sourceArtifact.url).build()
        OkHttpClientHelper.execute(request).use { response ->
            if (response.code == HttpURLConnection.HTTP_OK) {
                val artifactProvenance = ArtifactProvenance(pkg.sourceArtifact)
                storage.putProvenance(pkg.id, pkg.sourceArtifact, ResolvedArtifactProvenance(artifactProvenance))
                return artifactProvenance
            }

            throw IOException("Could not verify existence of source artifact at ${pkg.sourceArtifact.url}.")
        }
    }

    private suspend fun resolveVcs(pkg: Package): RepositoryProvenance {
        // TODO: Currently the commit revision is resolved by checking out the provided revision. There are probably
        //       probably more efficient ways to do this depending on the VCS, especially for providers like GitHub
        //       or GitLab which provide an API.

        when (val storedResult = storage.readProvenance(pkg.id, pkg.vcsProcessed)) {
            is ResolvedRepositoryProvenance -> {
                if (storedResult.isFixedRevision) {
                    log.info {
                        "Found a stored repository resolution for package ${pkg.id.toCoordinates()} with the fixed " +
                                "revision ${storedResult.clonedRevision} which was resolved to " +
                                "${storedResult.provenance.resolvedRevision}."
                    }

                    return storedResult.provenance
                } else {
                    log.info {
                        "Found a stored repository resolution result for package ${pkg.id.toCoordinates()} with the " +
                                "non-fixed revision ${storedResult.clonedRevision} which was resolved to " +
                                "${storedResult.provenance.resolvedRevision}. Restarting resolution of the " +
                                "non-fixed revision."
                    }
                }
            }

            is UnresolvedPackageProvenance -> {
                log.info {
                    "Found a stored repository resolution result for package ${pkg.id.toCoordinates()} which failed " +
                            "previously. Not attempting resolution again. The error was: ${storedResult.message}"
                }

                throw IOException(storedResult.message)
            }

            else -> {
                log.info {
                    "Could not find a stored repository resolution result for package ${pkg.id.toCoordinates()}," +
                            "attempting resolution."
                }
            }
        }

        return workingTreeCache.use(pkg.vcsProcessed) { vcs, workingTree ->
            val revisionCandidates = runCatching {
                vcs.getRevisionCandidates(workingTree, pkg, allowMovingRevisions = true)
            }.getOrDefault(emptySet())

            if (revisionCandidates.isEmpty()) {
                val message = "Could not find any revision candidates for package ${pkg.id.toCoordinates()} with VCS " +
                        "${pkg.vcsProcessed}."

                storage.putProvenance(pkg.id, pkg.vcsProcessed, UnresolvedPackageProvenance(message))

                throw IOException(message)
            }

            revisionCandidates.forEachIndexed { index, revision ->
                log.info { "Trying revision candidate '$revision' (${index + 1} of ${revisionCandidates.size})." }
                val result = vcs.updateWorkingTree(workingTree, revision, pkg.vcsProcessed.path, recursive = false)
                if (result.isSuccess) {
                    val resolvedRevision = workingTree.getRevision()

                    log.info {
                        "Resolved revision for package ${pkg.id.toCoordinates()} to $resolvedRevision based on " +
                                "guessed revision $revision."
                    }

                    val repositoryProvenance = RepositoryProvenance(pkg.vcsProcessed, workingTree.getRevision())

                    storage.putProvenance(
                        pkg.id,
                        pkg.vcsProcessed,
                        ResolvedRepositoryProvenance(
                            repositoryProvenance, revision, vcs.isFixedRevision(workingTree, revision)
                        )
                    )

                    return@use repositoryProvenance
                }
            }

            val message = "Could not resolve any of the revision candidates $revisionCandidates for package " +
                    "${pkg.id.toCoordinates()} with VCS ${pkg.vcsProcessed}."

            storage.putProvenance(pkg.id, pkg.vcsProcessed, UnresolvedPackageProvenance(message))

            throw IOException(message)
        }
    }
}
