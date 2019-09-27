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

package com.here.ort.scanner

import com.here.ort.model.AccessStatistics
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.ScanResult
import com.here.ort.model.ScanResultContainer
import com.here.ort.model.ScannerDetails
import com.here.ort.model.config.ArtifactoryStorageConfiguration
import com.here.ort.model.config.LocalFileStorageConfiguration
import com.here.ort.model.config.PostgresStorageConfiguration
import com.here.ort.scanner.storages.*
import com.here.ort.utils.expandTilde
import com.here.ort.utils.log

import java.lang.IllegalArgumentException
import java.sql.DriverManager
import java.util.Properties

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
         * Given a [config] for a known storage backend, configure it as the current one.
         */
        fun configure(config: Any) {
            when (config) {
                is LocalFileStorageConfiguration -> configure(config)
                is ArtifactoryStorageConfiguration -> configure(config)
                is PostgresStorageConfiguration -> configure(config)
                else -> throw IllegalArgumentException("Unsupported configuration type '$config'.")
            }
        }

        /**
         * Configure a [LocalFileStorage] as the current storage backend.
         */
        fun configure(config: LocalFileStorageConfiguration) {
            storage = LocalFileStorage(config.directory.expandTilde()).also {
                log.info { "Using local file storage at ${it.scanResultsDirectory}." }
            }
        }

        /**
         * Configure an [ArtifactoryStorage] as the current storage backend.
         */
        fun configure(config: ArtifactoryStorageConfiguration) {
            require(config.url.isNotBlank()) {
                "URL for Artifactory storage is missing."
            }

            require(config.repository.isNotBlank()) {
                "Repository for Artifactory storage is missing."
            }

            require(config.apiToken.isNotBlank()) {
                "API token for Artifactory storage is missing."
            }

            storage = ArtifactoryStorage(config.url, config.repository, config.apiToken)

            log.info { "Using Artifactory storage at ${config.url}." }
        }

        /**
         * Configure a [PostgresStorage] as the current storage backend.
         */
        fun configure(config: PostgresStorageConfiguration) {
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

            val connection = DriverManager.getConnection(config.url, properties)

            storage = PostgresStorage(connection, config.schema).also { it.setupDatabase() }
        }
    }

    /**
     * The name to refer to this storage implementation.
     */
    val name: String = javaClass.simpleName

    /**
     * The access statistics for the scan result storage.
     */
    val stats = AccessStatistics()

    /**
     * Read all [ScanResult]s for this [id] from the storage.
     *
     * @param id The [Identifier] of the scanned [Package].
     *
     * @return The [ScanResultContainer] for this [id].
     */
    fun read(id: Identifier) =
        readFromStorage(id).also {
            stats.numReads.incrementAndGet()
            if (it.results.isNotEmpty()) {
                stats.numHits.incrementAndGet()
            }
        }

    /**
     * Read the [ScanResult]s matching the [id][Package.id] of [pkg] and the [scannerDetails] from the storage.
     * [ScannerDetails.isCompatible] is used to check if the results are compatible with the provided [scannerDetails].
     * Also [Package.sourceArtifact], [Package.vcs], and [Package.vcsProcessed] are used to check if the scan result
     * matches the expected source code location. This is important to find the correct results when different revisions
     * of a package using the same version name are used (e.g. multiple scans of 1.0-SNAPSHOT during development).
     *
     * @param pkg The [Package] to look up results for.
     * @param scannerDetails Details about the scanner that was used to scan the [Package].
     *
     * @return The [ScanResultContainer] matching the [id][Package.id] of [pkg] and the [scannerDetails].
     */
    fun read(pkg: Package, scannerDetails: ScannerDetails) =
        readFromStorage(pkg, scannerDetails).also {
            stats.numReads.incrementAndGet()
            if (it.results.isNotEmpty()) {
                stats.numHits.incrementAndGet()
            }
        }

    /**
     * Add a [ScanResult] to the [ScanResultContainer] for this [id] and write it to the storage.
     *
     * @param id The [Identifier] of the scanned [Package].
     * @param scanResult The [ScanResult]. The [ScanResult.rawResult] must not be null.
     *
     * @return If the [ScanResult] could be written to the storage.
     */
    fun add(id: Identifier, scanResult: ScanResult): Boolean {
        // Do not store empty scan results. It is likely that something went wrong when they were created, and if not,
        // it is cheap to re-create them.
        if (scanResult.summary.fileCount == 0) {
            log.info { "Not storing scan result for '${id.toCoordinates()}' because no files were scanned." }

            return false
        }

        // Do not store scan results without raw result. The raw result can be set to null for other usages, but in the
        // storage it must never be null.
        if (scanResult.rawResult == null) {
            log.info { "Not storing scan result for '${id.toCoordinates()}' because the raw result is null." }

            return false
        }

        // Do not store scan results without provenance information, because they cannot be assigned to the revision of
        // the package source code later.
        if (scanResult.provenance.sourceArtifact == null && scanResult.provenance.vcsInfo == null) {
            log.info {
                "Not storing scan result for '${id.toCoordinates()}' because no provenance information is available."
            }

            return false
        }

        return addToStorage(id, scanResult)
    }

    protected abstract fun readFromStorage(id: Identifier): ScanResultContainer

    protected abstract fun readFromStorage(pkg: Package, scannerDetails: ScannerDetails): ScanResultContainer

    protected abstract fun addToStorage(id: Identifier, scanResult: ScanResult): Boolean
}
