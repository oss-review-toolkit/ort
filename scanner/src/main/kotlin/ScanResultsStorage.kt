/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

import kotlin.time.measureTimedValue

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

import org.ossreviewtoolkit.model.AccessStatistics
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.config.ClearlyDefinedStorageConfiguration
import org.ossreviewtoolkit.model.config.FileBasedStorageConfiguration
import org.ossreviewtoolkit.model.config.PostgresStorageConfiguration
import org.ossreviewtoolkit.model.config.ScanStorageConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.config.Sw360StorageConfiguration
import org.ossreviewtoolkit.model.utils.DatabaseUtils
import org.ossreviewtoolkit.scanner.experimental.NestedProvenance
import org.ossreviewtoolkit.scanner.experimental.NestedProvenanceScanResult
import org.ossreviewtoolkit.scanner.experimental.PackageBasedScanStorage
import org.ossreviewtoolkit.scanner.experimental.ScanStorageException
import org.ossreviewtoolkit.scanner.experimental.toNestedProvenanceScanResult
import org.ossreviewtoolkit.scanner.storages.*
import org.ossreviewtoolkit.utils.ort.log
import org.ossreviewtoolkit.utils.ort.ortDataDirectory
import org.ossreviewtoolkit.utils.ort.storage.HttpFileStorage
import org.ossreviewtoolkit.utils.ort.storage.LocalFileStorage
import org.ossreviewtoolkit.utils.ort.storage.XZCompressedLocalFileStorage

/**
 * The abstract class that storage backends for scan results need to implement.
 */
abstract class ScanResultsStorage : PackageBasedScanStorage {
    /**
     * A companion object that allow to configure the globally used storage backend.
     */
    companion object {
        /**
         * A successful [Result] with an empty list of [ScanResult]s.
         */
        val EMPTY_RESULT = Result.success<List<ScanResult>>(emptyList())

        /**
         * The scan result storage in use. Needs to be set via the corresponding [configure] function.
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

            log.info {
                "Using composite storage with readers ${readers.joinToString { it.name }} and writers " +
                        "${writers.joinToString { it.name }}."
            }

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
                is Sw360StorageConfiguration -> createSw360Storage(config)
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
                config = config.connection,
                applicationNameSuffix = TOOL_NAME,
                // Use a value slightly higher than the number of transactions accessing the storage.
                maxPoolSize = config.connection.parallelTransactions + 3
            )

            log.info {
                "Using Postgres storage with URL '${config.connection.url}' and schema '${config.connection.schema}'."
            }

            return PostgresStorage(dataSource, config.connection.parallelTransactions)
        }

        /**
         * Create a [ClearlyDefinedStorage] based on the [config] passed in.
         */
        private fun createClearlyDefinedStorage(config: ClearlyDefinedStorageConfiguration): ScanResultsStorage =
            ClearlyDefinedStorage(config).also {
                log.info { "Using ClearlyDefined storage with URL '${config.serverUrl}'." }
            }

        /**
         * Configure a [Sw360Storage] as the current storage backend.
         */
        private fun createSw360Storage(config: Sw360StorageConfiguration): ScanResultsStorage =
            Sw360Storage(config).also {
                log.info { "Using SW360 storage with auth URL '${config.authUrl}' and REST URL '${config.restUrl}'." }
            }
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
     * Return all [ScanResult]s contained in this [ScanResultsStorage] corresponding to the package denoted by the given
     * [id] wrapped in a [Result].
     */
    fun read(id: Identifier): Result<List<ScanResult>> {
        val (result, duration) = measureTimedValue { readInternal(id) }

        stats.numReads.incrementAndGet()

        result.onSuccess { results ->
            if (results.isNotEmpty()) {
                stats.numHits.incrementAndGet()
            }

            log.info {
                "Read ${results.size} scan result(s) for '${id.toCoordinates()}' from ${javaClass.simpleName} in " +
                        "$duration."
            }
        }

        return result
    }

    /**
     * Return all [ScanResult]s contained in this [ScanResultsStorage] corresponding to the given [package][pkg] that
     * are [compatible][ScannerCriteria.matches] with the provided [scannerCriteria] wrapped in a [Result]. Also,
     * [Package.sourceArtifact], [Package.vcs], and [Package.vcsProcessed] are used to check if the scan result matches
     * the expected source code location. That check is important to find the correct results when different revisions
     * of a package using the same version name are used (e.g. multiple scans of a "1.0-SNAPSHOT" version during
     * development).
     */
    fun read(pkg: Package, scannerCriteria: ScannerCriteria): Result<List<ScanResult>> {
        val (result, duration) = measureTimedValue { readInternal(pkg, scannerCriteria) }

        stats.numReads.incrementAndGet()

        result.onSuccess { results ->
            if (results.isNotEmpty()) {
                stats.numHits.incrementAndGet()
            }

            log.info {
                "Read ${results.size} scan result(s) for '${pkg.id.toCoordinates()}' from ${javaClass.simpleName} in " +
                        "$duration."
            }
        }

        return result
    }

    /**
     * Return all [ScanResult]s contained in this [ScanResultsStorage] corresponding to the given [packages] that
     * are [compatible][ScannerCriteria.matches] with the provided [scannerCriteria] wrapped in a [Result]. Also,
     * [Package.sourceArtifact], [Package.vcs], and [Package.vcsProcessed] are used to check if the scan result matches
     * the expected source code location. That check is important to find the correct results when different revisions
     * of a package using the same version name are used (e.g. multiple scans of a "1.0-SNAPSHOT" version during
     * development).
     */
    fun read(
        packages: Collection<Package>,
        scannerCriteria: ScannerCriteria
    ): Result<Map<Identifier, List<ScanResult>>> {
        val (result, duration) = measureTimedValue { readInternal(packages, scannerCriteria) }

        stats.numReads.addAndGet(packages.size)

        result.onSuccess { results ->
            stats.numHits.addAndGet(results.count { (_, results) -> results.isNotEmpty() })

            log.info {
                "Read ${results.values.sumOf { it.size }} scan result(s) from ${javaClass.simpleName} in $duration."
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
        // Do not store scan results without provenance information, because they cannot be assigned to the revision of
        // the package source code later.
        if (scanResult.provenance is UnknownProvenance) {
            val message =
                "Not storing scan result for '${id.toCoordinates()}' because no provenance information is available."
            log.info { message }

            return Result.failure(ScanStorageException(message))
        }

        val (result, duration) = measureTimedValue { addInternal(id, scanResult) }

        log.info { "Added scan result for '${id.toCoordinates()}' to ${javaClass.simpleName} in $duration." }

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
    protected open fun readInternal(pkg: Package, scannerCriteria: ScannerCriteria): Result<List<ScanResult>> =
        readInternal(pkg.id).map { results ->
            if (results.isEmpty()) {
                results
            } else {
                val scanResults = results.toMutableList()

                // Only keep scan results whose provenance information matches the package information.
                scanResults.retainAll { it.provenance.matches(pkg) }
                if (scanResults.isEmpty()) {
                    log.debug {
                        "No stored scan results found for $pkg. The following entries with non-matching provenance " +
                                "have been ignored: ${scanResults.map { it.provenance }}"
                    }
                } else {
                    // Only keep scan results from compatible scanners.
                    scanResults.retainAll { scannerCriteria.matches(it.scanner) }
                    if (scanResults.isEmpty()) {
                        log.debug {
                            "No stored scan results found for $scannerCriteria. The following entries with " +
                                    "incompatible scanners have been ignored: ${scanResults.map { it.scanner }}"
                        }
                    }
                }

                scanResults
            }
        }

    /**
     * Internal version of [read] that does not update the [access statistics][stats]. The default implementation uses
     * [Dispatchers.IO] to run requests for individual packages in parallel. Implementations may want to override this
     * function if they can filter for the wanted [scannerCriteria] or fetch results for multiple packages in a more
     * efficient way.
     */
    protected open fun readInternal(
        packages: Collection<Package>,
        scannerCriteria: ScannerCriteria
    ): Result<Map<Identifier, List<ScanResult>>> {
        val results = runBlocking(Dispatchers.IO) {
            packages.map { async { it.id to readInternal(it, scannerCriteria) } }.awaitAll()
        }.associate { it }

        val successfulResults = results.mapNotNull { (id, scanResults) ->
            scanResults.getOrNull()?.let { id to it }
        }.toMap()

        return if (successfulResults.isEmpty()) {
            Result.failure(ScanStorageException("Could not read any scan results from ${javaClass.simpleName}."))
        } else {
            Result.success(successfulResults)
        }
    }

    /**
     * Internal version of [add] that skips common sanity checks.
     */
    protected abstract fun addInternal(id: Identifier, scanResult: ScanResult): Result<Unit>

    override fun read(pkg: Package, nestedProvenance: NestedProvenance): List<NestedProvenanceScanResult> =
        read(pkg.id).toNestedProvenanceScanResult(nestedProvenance)

    override fun read(
        pkg: Package,
        nestedProvenance: NestedProvenance,
        scannerCriteria: ScannerCriteria
    ): List<NestedProvenanceScanResult> =
        read(pkg, scannerCriteria).toNestedProvenanceScanResult(nestedProvenance)

    private fun Result<List<ScanResult>>.toNestedProvenanceScanResult(nestedProvenance: NestedProvenance) =
        map { scanResults ->
            scanResults.filter { it.provenance == nestedProvenance.root }
                .map { it.toNestedProvenanceScanResult(nestedProvenance) }
        }.getOrThrow()

    override fun write(pkg: Package, nestedProvenanceScanResult: NestedProvenanceScanResult) {
        nestedProvenanceScanResult.merge().forEach { scanResult ->
            add(pkg.id, scanResult).getOrThrow()
        }
    }
}
