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

import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.utils.DatabaseUtils
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.ort.ortDataDirectory
import org.ossreviewtoolkit.utils.ort.storage.LocalFileStorage

/**
 * A storage for resolved [NestedProvenance]s.
 */
interface NestedProvenanceStorage {
    companion object {
        /**
         * Create a [NestedProvenanceStorage] from a [ScannerConfiguration]. If no provenance storage is configured, a
         * local [FileBasedNestedProvenanceStorage] is created.
         */
        fun createFromConfig(config: ScannerConfiguration): NestedProvenanceStorage {
            config.provenanceStorage?.fileStorage?.let { fileStorageConfiguration ->
                return FileBasedNestedProvenanceStorage(fileStorageConfiguration.createFileStorage())
            }

            config.provenanceStorage?.postgresStorage?.let { postgresStorageConfiguration ->
                return PostgresNestedProvenanceStorage(
                    DatabaseUtils.createHikariDataSource(postgresStorageConfiguration.connection)
                )
            }

            return FileBasedNestedProvenanceStorage(
                LocalFileStorage(ortDataDirectory / "scanner" / "nested_provenance")
            )
        }
    }

    /**
     * Return the [NestedProvenanceResolutionResult] for the [root] provenance, or null if no result was stored.
     */
    fun readNestedProvenance(root: RepositoryProvenance): NestedProvenanceResolutionResult?

    /**
     * Write the resolution [result] for the [root] provenance into the storage. If the storage already contains an
     * entry for [root] it is overwritten.
     */
    fun writeNestedProvenance(root: RepositoryProvenance, result: NestedProvenanceResolutionResult)
}

/**
 * The result of a [NestedProvenance] resolution.
 */
data class NestedProvenanceResolutionResult(
    /**
     * The resolved [NestedProvenance].
     */
    val nestedProvenance: NestedProvenance,

    /**
     * True if the revisions of all [RepositoryProvenance.vcsInfo] within [nestedProvenance] are fixed revisions.
     */
    val hasOnlyFixedRevisions: Boolean
)
