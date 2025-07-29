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

import com.fasterxml.jackson.annotation.JsonTypeInfo

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.utils.DatabaseUtils
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.ort.ortDataDirectory
import org.ossreviewtoolkit.utils.ort.storage.LocalFileStorage

/**
 * A storage for the resolved [RepositoryProvenance]s of [Package]s.
 */
interface PackageProvenanceStorage {
    companion object {
        /**
         * Create a [PackageProvenanceStorage] from a [ScannerConfiguration]. If no provenance storage is configured, a
         * local [FileBasedPackageProvenanceStorage] is created.
         */
        fun createFromConfig(config: ScannerConfiguration): PackageProvenanceStorage {
            config.provenanceStorage?.fileStorage?.let { fileStorageConfiguration ->
                return FileBasedPackageProvenanceStorage(fileStorageConfiguration.createFileStorage())
            }

            config.provenanceStorage?.postgresStorage?.let { postgresStorageConfiguration ->
                return PostgresPackageProvenanceStorage(
                    DatabaseUtils.createHikariDataSource(postgresStorageConfiguration.connection)
                )
            }

            return FileBasedPackageProvenanceStorage(
                LocalFileStorage(ortDataDirectory / "scanner" / "package_provenance")
            )
        }
    }

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
     * Return all [PackageProvenanceResolutionResult]s for the [id].
     */
    fun readProvenances(id: Identifier): List<PackageProvenanceResolutionResult>

    /**
     * Write the resolution [result] for the [id] and [sourceArtifact] into the storage. If the storage already contains
     * an entry for [id] and [sourceArtifact] it is overwritten.
     */
    fun writeProvenance(id: Identifier, sourceArtifact: RemoteArtifact, result: PackageProvenanceResolutionResult)

    /**
     * Write the resolution [result] for the [id] and [vcs] into the storage. If the storage already contains an entry
     * for [id] and [vcs] it is overwritten.
     */
    fun writeProvenance(id: Identifier, vcs: VcsInfo, result: PackageProvenanceResolutionResult)

    /**
     * Delete all [PackageProvenanceResolutionResult]s for the [id].
     */
    fun deleteProvenances(id: Identifier)
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
