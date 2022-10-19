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

import org.ossreviewtoolkit.model.config.ClearlyDefinedStorageConfiguration
import org.ossreviewtoolkit.model.config.FileBasedStorageConfiguration
import org.ossreviewtoolkit.model.config.PostgresStorageConfiguration
import org.ossreviewtoolkit.model.config.ScanStorageConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.config.StorageType
import org.ossreviewtoolkit.model.config.Sw360StorageConfiguration
import org.ossreviewtoolkit.model.utils.DatabaseUtils
import org.ossreviewtoolkit.scanner.storages.ClearlyDefinedStorage
import org.ossreviewtoolkit.scanner.storages.FileBasedStorage
import org.ossreviewtoolkit.scanner.storages.PostgresStorage
import org.ossreviewtoolkit.scanner.storages.ProvenanceBasedFileStorage
import org.ossreviewtoolkit.scanner.storages.ProvenanceBasedPostgresStorage
import org.ossreviewtoolkit.scanner.storages.Sw360Storage
import org.ossreviewtoolkit.utils.ort.ortDataDirectory
import org.ossreviewtoolkit.utils.ort.storage.XZCompressedLocalFileStorage

/**
 * A holder for [ScanStorage] [readers] and [writers].
 */
class ScanStorages(
    val readers: List<ScanStorageReader>,
    val writers: List<ScanStorageWriter>
) {
    companion object {
        /**
         * Create [ScanStorages] from a scanner [config]. If no storage readers or writers are configured, a default
         * [XZCompressedLocalFileStorage] is created.
         */
        fun createFromConfig(config: ScannerConfiguration): ScanStorages {
            val storages = config.storages.orEmpty().mapValues { createStorage(it.value) }

            fun resolve(name: String): ScanStorage = requireNotNull(storages[name]) {
                "Could not resolve storage '$name'."
            }

            val defaultStorage = createDefaultStorage()

            val readers = config.storageReaders.orEmpty().map { resolve(it) }.takeIf { it.isNotEmpty() }
                ?: listOf(defaultStorage)
            val writers = config.storageWriters.orEmpty().map { resolve(it) }.takeIf { it.isNotEmpty() }
                ?: listOf(defaultStorage)

            return ScanStorages(readers, writers)
        }
    }
}

private fun createDefaultStorage(): ScanStorage {
    val localFileStorage = XZCompressedLocalFileStorage(ortDataDirectory.resolve("$TOOL_NAME/results"))
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
        StorageType.PACKAGE_BASED -> FileBasedStorage(config.backend.createFileStorage())
        StorageType.PROVENANCE_BASED -> ProvenanceBasedFileStorage(config.backend.createFileStorage())
    }

private fun createPostgresStorage(config: PostgresStorageConfiguration) =
    when (config.type) {
        StorageType.PACKAGE_BASED -> PostgresStorage(
            DatabaseUtils.createHikariDataSource(config = config.connection, applicationNameSuffix = TOOL_NAME),
            config.connection.parallelTransactions
        )
        StorageType.PROVENANCE_BASED -> ProvenanceBasedPostgresStorage(
            DatabaseUtils.createHikariDataSource(config = config.connection, applicationNameSuffix = TOOL_NAME)
        )
    }

private fun createClearlyDefinedStorage(config: ClearlyDefinedStorageConfiguration) = ClearlyDefinedStorage(config)

private fun createSw360Storage(config: Sw360StorageConfiguration) = Sw360Storage(config)
