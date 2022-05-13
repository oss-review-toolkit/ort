/*
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

import com.fasterxml.jackson.annotation.JsonTypeInfo

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.VcsInfo

/**
 * A storage for the resolved [RepositoryProvenance]s of [Package]s.
 */
interface PackageProvenanceStorage {
    /**
     * Return the [PackageProvenanceResolutionResult] for the [id] and [sourceArtifact], or null if no result was
     * stored.
     */
    fun readProvenance(id: Identifier, sourceArtifact: RemoteArtifact): PackageProvenanceResolutionResult?

    /**
     * Return the [PackageProvenanceResolutionResult] for the [id] and [vcs], or null if no result was stored.
     */
    fun readProvenance(id: Identifier, vcs: VcsInfo): PackageProvenanceResolutionResult?

    /**
     * Put the resolution [result] for the [id] and [sourceArtifact] into the storage. If the storage already contains
     * an entry for [id] and [sourceArtifact] it is overwritten.
     */
    fun putProvenance(id: Identifier, sourceArtifact: RemoteArtifact, result: PackageProvenanceResolutionResult)

    /**
     * Put the resolution [result] for the [id] and [vcs] into the storage. If the storage already contains an entry
     * for [id] and [vcs] it is overwritten.
     */
    fun putProvenance(id: Identifier, vcs: VcsInfo, result: PackageProvenanceResolutionResult)
}

/**
 * The result of a package provenance resolution.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
sealed interface PackageProvenanceResolutionResult

/**
 * A successful package provenance resolution result for an [ArtifactProvenance].
 */
data class ResolvedArtifactProvenance(
    /**
     * The resolved [ArtifactProvenance].
     */
    val provenance: ArtifactProvenance
) : PackageProvenanceResolutionResult

/**
 * A successful package provenance resolution result for a [RepositoryProvenance].
 */
data class ResolvedRepositoryProvenance(
    /**
     * The resolved [RepositoryProvenance].
     */
    val provenance: RepositoryProvenance,

    /**
     * The revision that was used for cloning. This is either the revision from the
     * [vcsInfo][RepositoryProvenance.vcsInfo] of [provenance] or a revision candidate obtained from
     * [VersionControlSystem.getRevisionCandidates].
     */
    val clonedRevision: String,

    /**
     * True if the [clonedRevision] is a fixed revision.
     */
    val isFixedRevision: Boolean
) : PackageProvenanceResolutionResult

/**
 * A failed package provenance resolution result.
 */
data class UnresolvedPackageProvenance(
    /**
     * A message that describes the error that occurred during resolution.
     */
    val message: String
) : PackageProvenanceResolutionResult
