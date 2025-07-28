/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.scanner.provenance

import java.io.IOException
import java.net.HttpURLConnection

import kotlin.coroutines.cancellation.CancellationException

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

import okhttp3.Request

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.downloader.WorkingTreeCache
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.SourceCodeOrigin
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.ort.await
import org.ossreviewtoolkit.utils.ort.okHttpClient

/**
 * The [PackageProvenanceResolver] provides a function to resolve the [Provenance] of a [Package].
 */
interface PackageProvenanceResolver {
    /**
     * Resolve the [Provenance] of [pkg] based on [Package.sourceCodeOrigins] if specified, or else on
     * [defaultSourceCodeOrigins]. Each source code origins are listed in order of priority.
     *
     * Throws an [IOException] if the provenance cannot be resolved.
     */
    suspend fun resolveProvenance(pkg: Package, defaultSourceCodeOrigins: List<SourceCodeOrigin>): KnownProvenance
}

/**
 * The default implementation of [PackageProvenanceResolver].
 */
class DefaultPackageProvenanceResolver(
    private val storage: PackageProvenanceStorage,
    private val workingTreeCache: WorkingTreeCache
) : PackageProvenanceResolver {
    /**
     * Resolve the [Provenance] of [pkg] based on [Package.sourceCodeOrigins] if specified, or else on
     * [defaultSourceCodeOrigins]. Each source code origins are listed in order of priority. For source artifacts it is
     * verified that the [RemoteArtifact] does exist. For a VCS it is verified that the revision exists. If the revision
     * provided by the [package][pkg] metadata does not exist or is missing, the function tries to guess the tag based
     * on the name and version of the [package][pkg].
     */
    override suspend fun resolveProvenance(
        pkg: Package,
        defaultSourceCodeOrigins: List<SourceCodeOrigin>
    ): KnownProvenance {
        val errors = mutableMapOf<SourceCodeOrigin, Throwable>()
        val sourceCodeOrigins = pkg.sourceCodeOrigins ?: defaultSourceCodeOrigins

        sourceCodeOrigins.forEach { sourceCodeOrigin ->
            runCatching {
                when (sourceCodeOrigin) {
                    SourceCodeOrigin.ARTIFACT -> {
                        if (pkg.sourceArtifact != RemoteArtifact.EMPTY) {
                            return resolveSourceArtifact(pkg)
                        }
                    }

                    SourceCodeOrigin.VCS -> {
                        if (pkg.vcsProcessed != VcsInfo.EMPTY) {
                            return resolveVcs(pkg)
                        }
                    }
                }
            }.onFailure {
                if (it is CancellationException) currentCoroutineContext().ensureActive()

                errors[sourceCodeOrigin] = it

                logger.info {
                    "Could not resolve $sourceCodeOrigin for '${pkg.id.toCoordinates()}': ${it.collectMessages()}"
                }
            }
        }

        val message = buildString {
            append(
                "Could not resolve provenance for package '${pkg.id.toCoordinates()}' for source code origins " +
                    "$sourceCodeOrigins."
            )

            errors.forEach { (origin, throwable) ->
                append("\nResolution of $origin failed with:\n${throwable.collectMessages()}")
            }
        }

        logger.info { message }

        throw IOException(message)
    }

    private suspend fun resolveSourceArtifact(pkg: Package): ArtifactProvenance {
        when (val storedResult = storage.readProvenance(pkg.id, pkg.sourceArtifact)) {
            is ResolvedArtifactProvenance -> {
                logger.info {
                    "Found a stored artifact resolution for package '${pkg.id.toCoordinates()}'."
                }

                return storedResult.provenance
            }

            is UnresolvedPackageProvenance -> {
                logger.info {
                    "Found a stored artifact resolution for package '${pkg.id.toCoordinates()}' which failed " +
                        "previously, re-attempting resolution. The error was: ${storedResult.message}"
                }
            }

            else -> {
                logger.info {
                    "Could not find a stored artifact resolution result for package '${pkg.id.toCoordinates()}', " +
                        "attempting resolution."
                }
            }
        }

        // Try a cheap HEAD request to probe for the artifact first, and only fall back to a GET request on failure.
        val responseCode = requestSourceArtifact(pkg, "HEAD").takeUnless { it == HttpURLConnection.HTTP_BAD_METHOD }
            ?: requestSourceArtifact(pkg, "GET")

        if (responseCode == HttpURLConnection.HTTP_OK) {
            val artifactProvenance = ArtifactProvenance(pkg.sourceArtifact)
            storage.writeProvenance(pkg.id, pkg.sourceArtifact, ResolvedArtifactProvenance(artifactProvenance))
            return artifactProvenance
        }

        throw IOException(
            "Could not verify existence of source artifact at ${pkg.sourceArtifact.url}. " +
                "HTTP request got response $responseCode."
        )
    }

    /**
     * Execute an HTTP request with the given [method] for the source artifact URL of the given [package][pkg].
     * Return the response status code, from which the existence of the artifact can be concluded.
     */
    private suspend fun requestSourceArtifact(pkg: Package, method: String): Int {
        logger.debug { "Request for source artifact: $method ${pkg.sourceArtifact.url}." }

        val request = Request.Builder().method(method, null).url(pkg.sourceArtifact.url).build()

        return okHttpClient.await(request).use { it.code }
    }

    private suspend fun resolveVcs(pkg: Package): RepositoryProvenance {
        // TODO: Currently the commit revision is resolved by checking out the provided revision. There are probably
        //       more efficient ways to do this depending on the VCS, especially for providers like GitHub or GitLab
        //       which provide an API.

        when (val storedResult = storage.readProvenance(pkg.id, pkg.vcsProcessed)) {
            is ResolvedRepositoryProvenance -> {
                if (storedResult.isFixedRevision) {
                    logger.info {
                        "Found a stored repository resolution for package '${pkg.id.toCoordinates()}' with the fixed " +
                            "revision ${storedResult.clonedRevision} which was resolved to " +
                            "${storedResult.provenance.resolvedRevision}."
                    }

                    return storedResult.provenance
                } else {
                    logger.info {
                        "Found a stored repository resolution result for package '${pkg.id.toCoordinates()}' with " +
                            "the non-fixed revision ${storedResult.clonedRevision} which was resolved to " +
                            "${storedResult.provenance.resolvedRevision}. Restarting resolution of the " +
                            "non-fixed revision."
                    }
                }
            }

            is UnresolvedPackageProvenance -> {
                logger.info {
                    "Found a stored repository resolution result for package '${pkg.id.toCoordinates()}' which " +
                        "failed previously, re-attempting resolution. The error was: ${storedResult.message}"
                }
            }

            else -> {
                logger.info {
                    "Could not find a stored repository resolution result for package '${pkg.id.toCoordinates()}', " +
                        "attempting resolution."
                }
            }
        }

        return workingTreeCache.use(pkg.vcsProcessed) { vcs, workingTree ->
            val revisionCandidates = vcs.getRevisionCandidates(workingTree, pkg, allowMovingRevisions = true)
                .getOrDefault(emptySet())

            val messages = mutableListOf<String>()

            fun addAndLogMessage(message: String) {
                logger.info { message }
                messages += message
            }

            if (revisionCandidates.isEmpty()) {
                addAndLogMessage(
                    "Could not find any revision candidates for package '${pkg.id.toCoordinates()}' with " +
                        "${pkg.vcsProcessed}."
                )
            }

            revisionCandidates.forEachIndexed { index, revision ->
                logger.info { "Trying revision candidate '$revision' (${index + 1} of ${revisionCandidates.size})." }
                val result = vcs.updateWorkingTree(workingTree, revision)

                if (pkg.vcsProcessed.path.isNotBlank() &&
                    !workingTree.getRootPath().resolve(pkg.vcsProcessed.path).exists()
                ) {
                    addAndLogMessage(
                        "Discarding revision '$revision' because the requested VCS path '${pkg.vcsProcessed.path}' " +
                            "does not exist."
                    )

                    return@forEachIndexed
                }

                result.onFailure {
                    addAndLogMessage("Could not resolve revision candidate '$revision': ${it.collectMessages()}")
                    return@forEachIndexed
                }

                val resolvedRevision = workingTree.getRevision()

                logger.info {
                    "Resolved revision for package '${pkg.id.toCoordinates()}' to $resolvedRevision based on " +
                        "guessed revision $revision."
                }

                val repositoryProvenance = RepositoryProvenance(pkg.vcsProcessed, workingTree.getRevision())

                vcs.isFixedRevision(workingTree, revision).onSuccess { isFixedRevision ->
                    storage.writeProvenance(
                        pkg.id,
                        pkg.vcsProcessed,
                        ResolvedRepositoryProvenance(repositoryProvenance, revision, isFixedRevision)
                    )

                    return@use repositoryProvenance
                }
            }

            val message = "Could not resolve revision for package '${pkg.id.toCoordinates()}' with " +
                "${pkg.vcsProcessed}:\n${messages.joinToString("\n") { "\t$it" }}"

            storage.writeProvenance(pkg.id, pkg.vcsProcessed, UnresolvedPackageProvenance(message))

            throw IOException(message)
        }
    }
}
