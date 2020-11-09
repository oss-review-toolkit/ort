/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

package org.ossreviewtoolkit.scanner

import java.sql.DriverManager
import java.util.Properties

import org.ossreviewtoolkit.model.AccessStatistics
import org.ossreviewtoolkit.model.Failure
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Result
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanResultContainer
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.Success
import org.ossreviewtoolkit.model.config.ClearlyDefinedStorageConfiguration
import org.ossreviewtoolkit.model.config.FileBasedStorageConfiguration
import org.ossreviewtoolkit.model.config.PostgresStorageConfiguration
import org.ossreviewtoolkit.model.config.ScanStorageConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.config.Sw360StorageConfiguration
import org.ossreviewtoolkit.scanner.storages.*
import org.ossreviewtoolkit.utils.ORT_FULL_NAME
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.ortDataDirectory
import org.ossreviewtoolkit.utils.storage.HttpFileStorage
import org.ossreviewtoolkit.utils.storage.LocalFileStorage
import org.ossreviewtoolkit.utils.storage.XZCompressedLocalFileStorage

/**
 * The abstract class that storage backends for scan results need to implement.
 */
abstract class ScanResultsStorage {
    /**
     * A companion object that allow to configure the globally used storage backend.
     */
    companion object {
        /**
         * The scan result storage in use. Needs to be set via the corresponding configure function.
         */
        var storage: ScanResultsStorage = NoStorage()

        /**
         * Configure the [ScanResultsStorage]. If [config] does not contain a storage configuration by default a
         * [FileBasedStorage] using a [XZCompressedLocalFileStorage] as backend is configured.
         */
        fun configure(config: ScannerConfiguration) {
            storage = if (config.storages.isNullOrEmpty()) {
                createDefaultStorage()
            } else {
                createCompositeStorage(config)
            }
            log.info { "ScanResultStorage has been configured to ${storage.name}." }
        }

        /**
         * Create a [CompositeStorage] that manages all storages defined in the given [config].
         */
        private fun createCompositeStorage(config: ScannerConfiguration): ScanResultsStorage {
            val storages = config.storages.orEmpty().mapValues { createStorage(it.value) }

            fun resolve(name: String): ScanResultsStorage =
                requireNotNull(storages[name]) {
                    "Could not resolve storage '$name'."
                }

            val readers = config.storageReaders.orEmpty().map { resolve(it) }
            val writers = config.storageWriters.orEmpty().map { resolve(it) }

            return CompositeStorage(readers, writers)
        }

        /**
         * Create the concrete [ScanResultsStorage] implementation based on the [config] passed in.
         */
        private fun createStorage(config: ScanStorageConfiguration): ScanResultsStorage =
            when (config) {
                is FileBasedStorageConfiguration -> createFileBasedStorage(config)
                is PostgresStorageConfiguration -> createPostgresStorage(config)
                is ClearlyDefinedStorageConfiguration -> createClearlyDefinedStorage(config)
                is Sw360StorageConfiguration -> TODO()
            }

        /**
         * Create a [FileBasedStorage] to be used as default if no other storage has been configured.
         */
        private fun createDefaultStorage(): ScanResultsStorage {
            val localFileStorage = XZCompressedLocalFileStorage(ortDataDirectory.resolve("$TOOL_NAME/results"))
            return FileBasedStorage(localFileStorage)
        }

        /**
         * Create a [FileBasedStorage] based on the [config] passed in.
         */
        private fun createFileBasedStorage(config: FileBasedStorageConfiguration): ScanResultsStorage {
            val backend = config.backend.createFileStorage()

            when (backend) {
                is HttpFileStorage -> log.info { "Using file based storage with HTTP backend '${backend.url}'." }
                is LocalFileStorage -> log.info {
                    "Using file based storage with local directory '${backend.directory.invariantSeparatorsPath}'."
                }
            }

            return FileBasedStorage(backend)
        }

        /**
         * Create a [PostgresStorage] based on the [config] passed in.
         */
        private fun createPostgresStorage(config: PostgresStorageConfiguration): ScanResultsStorage {
            require(config.url.isNotBlank()) {
                "URL for PostgreSQL storage is missing."
            }

            require(config.schema.isNotBlank()) {
                "Database for PostgreSQL storage is missing."
            }

            require(config.username.isNotBlank()) {
                "Username for PostgreSQL storage is missing."
            }

            require(config.password.isNotBlank()) {
                "Password for PostgreSQL storage is missing."
            }

            val properties = Properties()
            properties["user"] = config.username
            properties["password"] = config.password
            properties["ApplicationName"] = "$ORT_FULL_NAME - $TOOL_NAME"

            // Configure SSL, see: https://jdbc.postgresql.org/documentation/head/connect.html
            // Note that the "ssl" property is only a fallback in case "sslmode" is not used. Since we always set
            // "sslmode", "ssl" is not required.
            properties["sslmode"] = config.sslmode
            config.sslcert?.let { properties["sslcert"] = it }
            config.sslkey?.let { properties["sslkey"] = it }
            config.sslrootcert?.let { properties["sslrootcert"] = it }

            val connection = DriverManager.getConnection(config.url, properties)

            return PostgresStorage(connection, config.schema).also { it.setupDatabase() }
        }

        /**
         * Create a [ClearlyDefinedStorage] based on the [config] passed in.
         */
        private fun createClearlyDefinedStorage(config: ClearlyDefinedStorageConfiguration): ScanResultsStorage =
            ClearlyDefinedStorage(config)
    }

    /**
     * The name to refer to this storage implementation.
     */
    open val name: String = javaClass.simpleName

    /**
     * The access statistics for the scan result storage.
     */
    val stats = AccessStatistics()

    /**
     * Read all [ScanResult]s for a package with [id] from the storage. Return a [ScanResultContainer] wrapped in a
     * [Result], which is a [Failure] if no [ScanResult] was found and a [Success] otherwise.
     */
    fun read(id: Identifier): Result<ScanResultContainer> =
        readFromStorage(id).also {
            stats.numReads.incrementAndGet()
            if (it is Success && it.result.results.isNotEmpty()) {
                stats.numHits.incrementAndGet()
            }
        }

    /**
     * Read those [ScanResult]s for the given [package][pkg] from the storage that are
     * [compatible][ScannerDetails.isCompatible] with the provided [scannerDetails]. Also, [Package.sourceArtifact],
     * [Package.vcs], and [Package.vcsProcessed] are used to check if the scan result matches the expected source code
     * location. That check is important to find the correct results when different revisions of a package using the
     * same version name are used (e.g. multiple scans of a "1.0-SNAPSHOT" version during development). Return a
     * [ScanResultContainer] wrapped in a [Result], which is a [Failure] if no [ScanResult] was found and a [Success]
     * otherwise.
     */
    fun read(pkg: Package, scannerDetails: ScannerDetails): Result<ScanResultContainer> =
        readFromStorage(pkg, scannerDetails).also {
            stats.numReads.incrementAndGet()
            if (it is Success && it.result.results.isNotEmpty()) {
                stats.numHits.incrementAndGet()
            }
        }

    /**
     * Add the given [scanResult], which must include a [ScanResult.rawResult], to the [ScanResultContainer] for the
     * scanned [Package] with the provided [id]. Depending on the storage implementation this might first read any
     * existing [ScanResultContainer] and write the new [ScanResultContainer] to the storage again, implicitly deleting
     * the original storage entry by overwriting it. Return a [Result] describing whether the operation was successful.
     */
    fun add(id: Identifier, scanResult: ScanResult): Result<Unit> {
        // Do not store empty scan results. It is likely that something went wrong when they were created, and if not,
        // it is cheap to re-create them.
        if (scanResult.summary.fileCount == 0) {
            val message = "Not storing scan result for '${id.toCoordinates()}' because no files were scanned."
            log.info { message }

            return Failure(message)
        }

        // Do not store scan results without raw result. The raw result can be set to null for other usages, but in the
        // storage it must never be null.
        if (scanResult.rawResult == null) {
            val message = "Not storing scan result for '${id.toCoordinates()}' because the raw result is null."
            log.info { message }

            return Failure(message)
        }

        // Do not store scan results without provenance information, because they cannot be assigned to the revision of
        // the package source code later.
        if (scanResult.provenance.sourceArtifact == null && scanResult.provenance.vcsInfo == null) {
            val message =
                "Not storing scan result for '${id.toCoordinates()}' because no provenance information is available."
            log.info { message }

            return Failure(message)
        }

        return addToStorage(id, scanResult)
    }

    /**
     * Internal version of [read] that does not update the [access statistics][stats].
     */
    protected abstract fun readFromStorage(id: Identifier): Result<ScanResultContainer>

    /**
     * Internal version of [read] that does not update the [access statistics][stats]. Implementations may want to
     * override this function if they can filter for the wanted [scannerDetails] in a more efficient way.
     */
    protected open fun readFromStorage(pkg: Package, scannerDetails: ScannerDetails): Result<ScanResultContainer> {
        val scanResults = when (val readResult = readFromStorage(pkg.id)) {
            is Success -> readResult.result.results.toMutableList()
            is Failure -> return readResult
        }

        if (scanResults.isEmpty()) return Success(ScanResultContainer(pkg.id, scanResults))

        // Only keep scan results whose provenance information matches the package information.
        scanResults.retainAll { it.provenance.matches(pkg) }
        if (scanResults.isEmpty()) {
            log.debug {
                "No stored scan results found for $pkg. The following entries with non-matching provenance have " +
                        "been ignored: ${scanResults.map { it.provenance }}"
            }
            return Success(ScanResultContainer(pkg.id, scanResults))
        }

        // Only keep scan results from compatible scanners.
        scanResults.retainAll { scannerDetails.isCompatible(it.scanner) }
        if (scanResults.isEmpty()) {
            log.debug {
                "No stored scan results found for $scannerDetails. The following entries with incompatible scanners " +
                        "have been ignored: ${scanResults.map { it.scanner }}"
            }
            return Success(ScanResultContainer(pkg.id, scanResults))
        }

        return Success(ScanResultContainer(pkg.id, scanResults))
    }

    /**
     * Internal version of [add] that skips common sanity checks.
     */
    protected abstract fun addToStorage(id: Identifier, scanResult: ScanResult): Result<Unit>
}
