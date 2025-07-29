/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.scanner

import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.config.ClearlyDefinedStorageConfiguration
import org.ossreviewtoolkit.model.config.FileBasedStorageConfiguration
import org.ossreviewtoolkit.model.config.PostgresStorageConfiguration
import org.ossreviewtoolkit.model.config.ScanStorageConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.config.StorageType
import org.ossreviewtoolkit.model.config.Sw360StorageConfiguration
import org.ossreviewtoolkit.model.utils.DatabaseUtils
import org.ossreviewtoolkit.scanner.provenance.NestedProvenance
import org.ossreviewtoolkit.scanner.provenance.NestedProvenanceScanResult
import org.ossreviewtoolkit.scanner.provenance.NestedProvenanceStorage
import org.ossreviewtoolkit.scanner.provenance.PackageProvenanceStorage
import org.ossreviewtoolkit.scanner.provenance.ResolvedArtifactProvenance
import org.ossreviewtoolkit.scanner.provenance.ResolvedRepositoryProvenance
import org.ossreviewtoolkit.scanner.provenance.UnresolvedPackageProvenance
import org.ossreviewtoolkit.scanner.storages.ClearlyDefinedStorage
import org.ossreviewtoolkit.scanner.storages.PackageBasedFileStorage
import org.ossreviewtoolkit.scanner.storages.PackageBasedPostgresStorage
import org.ossreviewtoolkit.scanner.storages.ProvenanceBasedFileStorage
import org.ossreviewtoolkit.scanner.storages.ProvenanceBasedPostgresStorage
import org.ossreviewtoolkit.scanner.storages.Sw360Storage
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.ort.ortDataDirectory
import org.ossreviewtoolkit.utils.ort.storage.XZCompressedLocalFileStorage

/**
 * A holder for [ScanStorage] [readers] and [writers].
 */
class ScanStorages(
    val readers: List<ScanStorageReader>,
    val writers: List<ScanStorageWriter>,
    val packageProvenanceStorage: PackageProvenanceStorage,
    val nestedProvenanceStorage: NestedProvenanceStorage
) {
    companion object {
        /**
         * Create [ScanStorages] from a scanner [config]. If no storage readers or writers are configured, a default
         * [XZCompressedLocalFileStorage] is created.
         */
        fun createFromConfig(config: ScannerConfiguration): ScanStorages {
            val storages = config.storages.orEmpty().mapValues { createStorage(it.value) }

            fun resolve(name: String): ScanStorage =
                requireNotNull(storages[name]) {
                    "Could not resolve storage '$name'."
                }

            val defaultStorage by lazy { createDefaultStorage() }

            val readers = config.storageReaders.orEmpty().map { resolve(it) }.ifEmpty { listOf(defaultStorage) }
            val writers = config.storageWriters.orEmpty().map { resolve(it) }.ifEmpty { listOf(defaultStorage) }

            val packageProvenanceStorage = PackageProvenanceStorage.createFromConfig(config)
            val nestedProvenanceStorage = NestedProvenanceStorage.createFromConfig(config)

            return ScanStorages(readers, writers, packageProvenanceStorage, nestedProvenanceStorage)
        }
    }

    /**
     * Read all [ScanResult]s for the provided [package][pkg]. Returns an empty list if no stored scan results or no
     * stored provenance can be found.
     */
    fun read(pkg: Package): List<ScanResult> {
        val packageProvenances = packageProvenanceStorage.readProvenances(pkg.id)

        val nestedProvenances = packageProvenances.mapNotNull { result ->
            when (result) {
                is ResolvedArtifactProvenance -> {
                    NestedProvenance(root = result.provenance, subRepositories = emptyMap())
                }

                is ResolvedRepositoryProvenance -> {
                    nestedProvenanceStorage.readNestedProvenance(result.provenance)?.nestedProvenance
                }

                is UnresolvedPackageProvenance -> null
            }
        }

        val results = mutableListOf<ScanResult>()

        nestedProvenances.forEach { nestedProvenance ->
            results += readers.filterIsInstance<PackageBasedScanStorageReader>().flatMap { reader ->
                reader.read(pkg, nestedProvenance).flatMap { it.merge() }
            }

            results += readers.filterIsInstance<ProvenanceBasedScanStorageReader>().mapNotNull { reader ->
                val scanResults = nestedProvenance.allProvenances.associateWith { reader.read(it) }
                NestedProvenanceScanResult(nestedProvenance, scanResults).takeIf { it.isComplete() }?.merge()
            }.flatten()
        }

        return results
    }
}

private fun createDefaultStorage(): ScanStorage {
    val localFileStorage = XZCompressedLocalFileStorage(ortDataDirectory / "scanner" / "results")
    return ProvenanceBasedFileStorage(localFileStorage)
}

private fun createStorage(config: ScanStorageConfiguration): ScanStorage =
    when (config) {
        is FileBasedStorageConfiguration -> createFileBasedStorage(config)
        is PostgresStorageConfiguration -> createPostgresStorage(config)
        is ClearlyDefinedStorageConfiguration -> createClearlyDefinedStorage(config)
        is Sw360StorageConfiguration -> createSw360Storage(config)
    }

private fun createFileBasedStorage(config: FileBasedStorageConfiguration) =
    when (config.type) {
        StorageType.PACKAGE_BASED -> PackageBasedFileStorage(config.backend.createFileStorage())
        StorageType.PROVENANCE_BASED -> ProvenanceBasedFileStorage(config.backend.createFileStorage())
    }

private fun createPostgresStorage(config: PostgresStorageConfiguration) =
    when (config.type) {
        StorageType.PACKAGE_BASED -> PackageBasedPostgresStorage(
            DatabaseUtils.createHikariDataSource(config = config.connection, applicationNameSuffix = "scanner")
        )
        StorageType.PROVENANCE_BASED -> ProvenanceBasedPostgresStorage(
            DatabaseUtils.createHikariDataSource(config = config.connection, applicationNameSuffix = "scanner")
        )
    }

private fun createClearlyDefinedStorage(config: ClearlyDefinedStorageConfiguration) = ClearlyDefinedStorage(config)

private fun createSw360Storage(config: Sw360StorageConfiguration) = Sw360Storage(config)
