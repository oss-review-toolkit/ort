/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
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

import kotlin.time.measureTimedValue

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

import org.ossreviewtoolkit.model.AccessStatistics
import org.ossreviewtoolkit.model.Failure
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Result
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.Success
import org.ossreviewtoolkit.model.config.ClearlyDefinedStorageConfiguration
import org.ossreviewtoolkit.model.config.FileBasedStorageConfiguration
import org.ossreviewtoolkit.model.config.PostgresStorageConfiguration
import org.ossreviewtoolkit.model.config.ScanStorageConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.config.Sw360StorageConfiguration
import org.ossreviewtoolkit.model.utils.DatabaseUtils
import org.ossreviewtoolkit.scanner.storages.*
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
            val dataSource = DatabaseUtils.createHikariDataSource(
                config = config,
                applicationNameSuffix = TOOL_NAME,
                // Use a value slightly higher than the number of threads accessing the storage.
                maxPoolSize = LocalScanner.NUM_STORAGE_THREADS + 3
            )

            return PostgresStorage(dataSource)
        }

        /**
         * Create a [ClearlyDefinedStorage] based on the [config] passed in.
         */
        private fun createClearlyDefinedStorage(config: ClearlyDefinedStorageConfiguration): ScanResultsStorage =
            ClearlyDefinedStorage(config)

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
     * Read all [ScanResult]s for a package with [id] from the storage. Return a list of [ScanResult]s wrapped in a
     * [Result], which is a [Failure] if an unexpected error occurred and a [Success] otherwise.
     */
    fun read(id: Identifier): Result<List<ScanResult>> {
        val (result, duration) = measureTimedValue { readInternal(id) }

        stats.numReads.incrementAndGet()

        if (result is Success) {
            if (result.result.isNotEmpty()) {
                stats.numHits.incrementAndGet()
            }

            log.perf {
                "Read ${result.result.size} scan results for '${id.toCoordinates()}' from " +
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
     * list of [ScanResult]s wrapped in a [Result], which is a [Failure] if an unexpected error occurred and a [Success]
     * otherwise.
     */
    fun read(pkg: Package, scannerCriteria: ScannerCriteria): Result<List<ScanResult>> {
        val (result, duration) = measureTimedValue { readInternal(pkg, scannerCriteria) }

        stats.numReads.incrementAndGet()

        if (result is Success) {
            if (result.result.isNotEmpty()) {
                stats.numHits.incrementAndGet()
            }

            log.perf {
                "Read ${result.result.size} scan results for '${pkg.id.toCoordinates()}' from " +
                        "${javaClass.simpleName} in ${duration.inMilliseconds}ms."
            }
        }

        return result
    }

    /**
     * Read those [ScanResult]s for the given [packages] from the storage that are
     * [compatible][ScannerCriteria.matches] with the provided [scannerCriteria]. Also, [Package.sourceArtifact],
     * [Package.vcs], and [Package.vcsProcessed] are used to check if the scan result matches the expected source code
     * location. That check is important to find the correct results when different revisions of a package using the
     * same version name are used (e.g. multiple scans of a "1.0-SNAPSHOT" version during development). Return the list
     * of [ScanResult]s mapped to the [Identifier]s of the [packages], wrapped in a [Result], which is a [Failure] if
     * an unexpected error occurred and a [Success] otherwise.
     */
    fun read(packages: List<Package>, scannerCriteria: ScannerCriteria): Result<Map<Identifier, List<ScanResult>>> {
        val (result, duration) = measureTimedValue { readInternal(packages, scannerCriteria) }

        stats.numReads.addAndGet(packages.size)

        if (result is Success) {
            stats.numHits.addAndGet(result.result.count { (_, results) -> results.isNotEmpty() })

            log.perf {
                "Read ${result.result.values.sumBy { it.size }} scan results from ${javaClass.simpleName} in " +
                        "${duration.inMilliseconds}ms."
            }
        }

        return result
    }

    /**
     * Add the given [scanResult] to the stored [ScanResult]s for the scanned [Package] with the provided [id].
     * Depending on the storage implementation this might first read any existing [ScanResult]s and write the new
     * [ScanResult]s to the storage again, implicitly deleting the original storage entry by overwriting it.
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

        val (result, duration) = measureTimedValue { addInternal(id, scanResult) }

        log.perf {
            "Added scan result for '${id.toCoordinates()}' to ${javaClass.simpleName} in ${duration.inMilliseconds}ms."
        }

        return result
    }

    /**
     * Internal version of [read] that does not update the [access statistics][stats].
     */
    protected abstract fun readInternal(id: Identifier): Result<List<ScanResult>>

    /**
     * Internal version of [read] that does not update the [access statistics][stats]. Implementations may want to
     * override this function if they can filter for the wanted [scannerCriteria] in a more efficient way.
     */
    protected open fun readInternal(pkg: Package, scannerCriteria: ScannerCriteria): Result<List<ScanResult>> {
        val scanResults = when (val readResult = readInternal(pkg.id)) {
            is Success -> readResult.result.toMutableList()
            is Failure -> return readResult
        }

        if (scanResults.isEmpty()) return Success(scanResults)

        // Only keep scan results whose provenance information matches the package information.
        scanResults.retainAll { it.provenance.matches(pkg) }
        if (scanResults.isEmpty()) {
            log.debug {
                "No stored scan results found for $pkg. The following entries with non-matching provenance have " +
                        "been ignored: ${scanResults.map { it.provenance }}"
            }
            return Success(scanResults)
        }

        // Only keep scan results from compatible scanners.
        scanResults.retainAll { scannerCriteria.matches(it.scanner) }
        if (scanResults.isEmpty()) {
            log.debug {
                "No stored scan results found for $scannerCriteria. The following entries with incompatible scanners " +
                        "have been ignored: ${scanResults.map { it.scanner }}"
            }
            return Success(scanResults)
        }

        return Success(scanResults)
    }

    /**
     * Internal version of [read] that does not update the [access statistics][stats]. The default implementation uses
     * [Dispatchers.IO] to run requests for individual packages in parallel. Implementations may want to override this
     * function if they can filter for the wanted [scannerCriteria] or fetch results for multiple packages in a more
     * efficient way.
     */
    protected open fun readInternal(
        packages: List<Package>,
        scannerCriteria: ScannerCriteria
    ): Result<Map<Identifier, List<ScanResult>>> {
        val results = runBlocking(Dispatchers.IO) {
            packages.map { async { it.id to readInternal(it, scannerCriteria) } }.awaitAll()
        }.associate { it }

        return if (results.all { it.value is Failure }) {
            Failure("Could not read any scan results from ${javaClass.simpleName}.")
        } else {
            Success(results.filter { it.value is Success }.mapValues { (it.value as Success).result })
        }
    }

    /**
     * Internal version of [add] that skips common sanity checks.
     */
    protected abstract fun addInternal(id: Identifier, scanResult: ScanResult): Result<Unit>
}
