/*
 * Copyright (C) 2021 HERE Europe B.V.
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

import okhttp3.Request

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.SourceCodeOrigin
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.utils.common.collectMessagesAsString
import org.ossreviewtoolkit.utils.core.OkHttpClientHelper
import org.ossreviewtoolkit.utils.core.createOrtTempDir
import org.ossreviewtoolkit.utils.core.log
import org.ossreviewtoolkit.utils.core.showStackTrace

/**
 * The [PackageProvenanceResolver] provides a function to resolve the [Provenance] of a [Package].
 */
interface PackageProvenanceResolver {
    /**
     * Resolve the [Provenance] of [pkg] based on the provided [sourceCodeOriginPriority]. If no provenance can be
     * resolved [UnknownProvenance] is returned.
     */
    fun resolveProvenance(pkg: Package, sourceCodeOriginPriority: List<SourceCodeOrigin>): Provenance
}

/**
 * The default implementation of [PackageProvenanceResolver].
 */
class DefaultPackageProvenanceResolver : PackageProvenanceResolver {
    /**
     * Resolve the [Provenance] of [pkg] based on the provided [sourceCodeOriginPriority]. For source artifacts it is
     * verified that the [RemoteArtifact] does exist. For a VCS it is verified that the revision exists. If the revision
     * provided by the [package][pkg] metadata does not exist or is missing, the function tries to guess the tag based
     * on the name and version of the [package][pkg].
     */
    override fun resolveProvenance(pkg: Package, sourceCodeOriginPriority: List<SourceCodeOrigin>): Provenance {
        sourceCodeOriginPriority.forEach { sourceCodeOrigin ->
            runCatching {
                when (sourceCodeOrigin) {
                    SourceCodeOrigin.ARTIFACT -> {
                        if (pkg.sourceArtifact != RemoteArtifact.EMPTY) {
                            return resolveSourceArtifact(pkg.sourceArtifact)
                        }
                    }

                    SourceCodeOrigin.VCS -> {
                        if (pkg.vcsProcessed != VcsInfo.EMPTY) {
                            return resolveVcs(pkg)
                        }
                    }
                }
            }.onFailure {
                it.showStackTrace()

                log.info {
                    "Could not resolve $sourceCodeOrigin for ${pkg.id.toCoordinates()}: ${it.collectMessagesAsString()}"
                }
            }
        }

        return UnknownProvenance
    }

    private fun resolveSourceArtifact(sourceArtifact: RemoteArtifact): ArtifactProvenance {
        val request = Request.Builder().head().url(sourceArtifact.url).build()
        OkHttpClientHelper.execute(request).use { response ->
            if (response.code == HttpURLConnection.HTTP_OK) {
                return ArtifactProvenance(sourceArtifact)
            }

            throw IOException("Could not verify existence of source artifact at ${sourceArtifact.url}.")
        }
    }

    private fun resolveVcs(pkg: Package): RepositoryProvenance {
        val vcs = VersionControlSystem.forType(pkg.vcsProcessed.type)
            ?: VersionControlSystem.forUrl(pkg.vcsProcessed.url)
            ?: throw IOException("Could not determine VCS type for ${pkg.vcsProcessed}.")

        // TODO: Currently the commit revision is resolved by checking out the provided revision. There are probably
        //       probably more efficient ways to do this depending on the VCS, especially for provides like GitHub
        //       or GitLab which provide an API. Another option to prevent downloading the same repository multiple
        //       times would be to improve the Downloader to manage the locations of already downloaded
        //       repositories.
        val targetDir = createOrtTempDir()
        val workingTree = vcs.initWorkingTree(targetDir, pkg.vcsProcessed)

        // TODO: If the provided revision is a fixed revision we could store the resolution result to prevent having
        //       to perform the resolution again for the same package.
        val revisionCandidates = vcs.getRevisionCandidates(workingTree, pkg, allowMovingRevisions = false)

        if (revisionCandidates.isEmpty()) {
            throw IOException(
                "Could not find any revision candidates for package ${pkg.id.toCoordinates()} with VCS " +
                        "${pkg.vcsProcessed}."
            )
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
                return RepositoryProvenance(pkg.vcsProcessed, workingTree.getRevision())
            }
        }

        throw IOException(
            "Could not resolve any of the revision candidates $revisionCandidates for package " +
                    "${pkg.id.toCoordinates()} with VCS ${pkg.vcsProcessed}."
        )
    }
}
