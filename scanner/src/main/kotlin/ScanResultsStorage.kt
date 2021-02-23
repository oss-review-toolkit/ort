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

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

import kotlin.time.measureTimedValue

import org.ossreviewtoolkit.model.AccessStatistics
import org.ossreviewtoolkit.model.Failure
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Result
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanResultContainer
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
import org.ossreviewtoolkit.utils.perf
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
        fun configure(config: ScannerConfiguration): ScanResultsStorage {
            // Unfortunately, the smart cast does not work when moving this to a capturing "when" subject.
            val configuredStorages = config.storages

            storage = when {
                configuredStorages.isNullOrEmpty() -> createDefaultStorage()
                configuredStorages.size == 1 -> createStorage(configuredStorages.values.first())
                else -> createCompositeStorage(config)
            }

            log.info { "ScanResultStorage has been configured to ${storage.name}." }

            return storage
        }

        /**
         * Create a [FileBasedStorage] to be used as default if no other storage has been configured.
         */
        private fun createDefaultStorage(): ScanResultsStorage {
            val localFileStorage = XZCompressedLocalFileStorage(ortDataDirectory.resolve("$TOOL_NAME/results"))
            return FileBasedStorage(localFileStorage)
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
                is Sw360StorageConfiguration -> configureSw360Storage(config)
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
                "Schema for PostgreSQL storage is missing."
            }

            require(config.username.isNotBlank()) {
                "Username for PostgreSQL storage is missing."
            }

            require(config.password.isNotBlank()) {
                "Password for PostgreSQL storage is missing."
            }

            val dataSourceConfig = HikariConfig().apply {
                jdbcUrl = config.url
                username = config.username
                password = config.password
                schema = config.schema

                // Use a value slightly higher than the number of threads accessing the storage.
                maximumPoolSize = LocalScanner.NUM_STORAGE_THREADS + 3

                addDataSourceProperty("ApplicationName", "$ORT_FULL_NAME - $TOOL_NAME")

                // Configure SSL, see: https://jdbc.postgresql.org/documentation/head/connect.html
                // Note that the "ssl" property is only a fallback in case "sslmode" is not used. Since we always set
                // "sslmode", "ssl" is not required.
                addDataSourceProperty("sslmode", config.sslmode)
                addDataSourcePropertyIfDefined("sslcert", config.sslcert)
                addDataSourcePropertyIfDefined("sslkey", config.sslkey)
                addDataSourcePropertyIfDefined("sslrootcert", config.sslrootcert)
            }

            return PostgresStorage(HikariDataSource(dataSourceConfig))
        }

        /**
         * Create a [ClearlyDefinedStorage] based on the [config] passed in.
         */
        private fun createClearlyDefinedStorage(config: ClearlyDefinedStorageConfiguration): ScanResultsStorage =
            ClearlyDefinedStorage(config)

        /**
         * Add a property with the given [key] and [value] to the [HikariConfig]. If the [value] is *null*, this
         * function has no effect. (It is not specified how the database driver deals with *null* values in its
         * properties; so it is safer to avoid them.)
         */
        private fun HikariConfig.addDataSourcePropertyIfDefined(key: String, value: String?) {
            value?.let { addDataSourceProperty(key, it) }
        }

        /**
         * Configure a [Sw360Storage] as the current storage backend.
         */
        private fun configureSw360Storage(config: Sw360StorageConfiguration): ScanResultsStorage =
            Sw360Storage(config)
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
    fun read(id: Identifier): Result<ScanResultContainer> {
        val (result, duration) = measureTimedValue { readFromStorage(id) }

        stats.numReads.incrementAndGet()

        if (result is Success) {
            if (result.result.results.isNotEmpty()) {
                stats.numHits.incrementAndGet()
            }

            log.perf {
                "Read ${result.result.results.size} scan results for '${id.toCoordinates()}' from " +
                        "${javaClass.simpleName} in ${duration.inMilliseconds}ms."
            }
        }

        return result
    }

    /**
     * Read those [ScanResult]s for the given [package][pkg] from the storage that are
     * [compatible][ScannerCriteria.matches] with the provided [scannerCriteria]. Also, [Package.sourceArtifact],
     * [Package.vcs], and [Package.vcsProcessed] are used to check if the scan result matches the expected source code
     * location. That check is important to find the correct results when different revisions of a package using the
     * same version name are used (e.g. multiple scans of a "1.0-SNAPSHOT" version during development). Return a
     * [ScanResultContainer] wrapped in a [Result], which is a [Failure] if no [ScanResult] was found and a [Success]
     * otherwise.
     */
    fun read(pkg: Package, scannerCriteria: ScannerCriteria): Result<ScanResultContainer> {
        val (result, duration) = measureTimedValue { readFromStorage(pkg, scannerCriteria) }

        stats.numReads.incrementAndGet()

        if (result is Success) {
            if (result.result.results.isNotEmpty()) {
                stats.numHits.incrementAndGet()
            }

            log.perf {
                "Read ${result.result.results.size} scan results for '${pkg.id.toCoordinates()}' from " +
                        "${javaClass.simpleName} in ${duration.inMilliseconds}ms."
            }
        }

        return result
    }

    /**
     * Add the given [scanResult] to the [ScanResultContainer] for the scanned [Package] with the provided [id].
     * Depending on the storage implementation this might first read any existing [ScanResultContainer] and write the
     * new [ScanResultContainer] to the storage again, implicitly deleting the original storage entry by overwriting it.
     * Return a [Result] describing whether the operation was successful.
     */
    fun add(id: Identifier, scanResult: ScanResult): Result<Unit> {
        // Do not store empty scan results. It is likely that something went wrong when they were created, and if not,
        // it is cheap to re-create them.
        if (scanResult.summary.fileCount == 0) {
            val message = "Not storing scan result for '${id.toCoordinates()}' because no files were scanned."
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

        val (result, duration) = measureTimedValue { addToStorage(id, scanResult) }

        log.perf {
            "Added scan result for '${id.toCoordinates()}' to ${javaClass.simpleName} in ${duration.inMilliseconds}ms."
        }

        return result
    }

    /**
     * Internal version of [read] that does not update the [access statistics][stats].
     */
    protected abstract fun readFromStorage(id: Identifier): Result<ScanResultContainer>

    /**
     * Internal version of [read] that does not update the [access statistics][stats]. Implementations may want to
     * override this function if they can filter for the wanted [scannerCriteria] in a more efficient way.
     */
    protected open fun readFromStorage(pkg: Package, scannerCriteria: ScannerCriteria): Result<ScanResultContainer> {
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
        scanResults.retainAll { scannerCriteria.matches(it.scanner) }
        if (scanResults.isEmpty()) {
            log.debug {
                "No stored scan results found for $scannerCriteria. The following entries with incompatible scanners " +
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
