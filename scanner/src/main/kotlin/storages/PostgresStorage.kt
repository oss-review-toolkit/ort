/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
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

package org.ossreviewtoolkit.scanner.storages

import com.fasterxml.jackson.core.JsonProcessingException

import java.sql.SQLException

import javax.sql.DataSource

import kotlin.math.max

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils.createMissingTablesAndColumns
import org.jetbrains.exposed.sql.SchemaUtils.withDataBaseLock
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransactionAsync
import org.jetbrains.exposed.sql.transactions.transaction

import org.ossreviewtoolkit.model.Failure
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Result
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.Success
import org.ossreviewtoolkit.model.utils.DatabaseUtils.checkDatabaseEncoding
import org.ossreviewtoolkit.model.utils.DatabaseUtils.tableExists
import org.ossreviewtoolkit.model.utils.arrayParam
import org.ossreviewtoolkit.model.utils.rawParam
import org.ossreviewtoolkit.model.utils.tilde
import org.ossreviewtoolkit.scanner.LocalScanner
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.scanner.ScannerCriteria
import org.ossreviewtoolkit.scanner.storages.utils.ScanResultDao
import org.ossreviewtoolkit.scanner.storages.utils.ScanResults
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.showStackTrace

private val TABLE_NAME = ScanResults.tableName

/**
 * The Postgres storage back-end.
 */
class PostgresStorage(
    /**
     * The JDBC data source to obtain database connections.
     */
    private val dataSource: DataSource
) : ScanResultsStorage() {
    companion object {
        /** Expression to reference the scanner version as an array. */
        private const val VERSION_ARRAY =
            "string_to_array(regexp_replace(scan_result->'scanner'->>'version', '[^0-9.]', '', 'g'), '.')"

        /** Expression to convert the scanner version to a numeric array for comparisons. */
        private const val VERSION_EXPRESSION = "$VERSION_ARRAY::int[]"
    }

    init {
        setupDatabase()
    }

    /**
     * Setup the database.
     */
    private fun setupDatabase() {
        Database.connect(dataSource).apply {
            defaultFetchSize(1000)
        }

        transaction {
            withDataBaseLock {
                if (!tableExists(TABLE_NAME)) {
                    checkDatabaseEncoding()

                    createMissingTablesAndColumns(ScanResults)

                    createIdentifierAndScannerVersionIndex()
                }
            }
        }
    }

    private fun Transaction.createIdentifierAndScannerVersionIndex() =
        exec(
            """
            CREATE INDEX identifier_and_scanner_version
                ON $TABLE_NAME USING btree
                (
                    identifier,
                    (scan_result->'scanner'->>'name'),
                    $VERSION_ARRAY
                )
                TABLESPACE pg_default
            """.trimIndent()
        )

    override fun readInternal(id: Identifier): Result<List<ScanResult>> {
        @Suppress("TooGenericExceptionCaught")
        return try {
            transaction {
                val scanResults =
                    ScanResultDao.find { ScanResults.identifier eq id.toCoordinates() }.map { it.scanResult }

                Success(scanResults)
            }
        } catch (e: Exception) {
            when (e) {
                is JsonProcessingException, is SQLException -> {
                    e.showStackTrace()

                    val message = "Could not read scan results for ${id.toCoordinates()} from database: " +
                            e.collectMessagesAsString()

                    log.info { message }
                    Failure(message)
                }
                else -> throw e
            }
        }
    }

    override fun readInternal(pkg: Package, scannerCriteria: ScannerCriteria): Result<List<ScanResult>> {
        val minVersionArray = with(scannerCriteria.minVersion) { intArrayOf(major, minor, patch) }
        val maxVersionArray = with(scannerCriteria.maxVersion) { intArrayOf(major, minor, patch) }

        @Suppress("TooGenericExceptionCaught")
        return try {
            transaction {
                val scanResults = ScanResultDao.find {
                    (ScanResults.identifier eq pkg.id.toCoordinates()) and
                            (rawParam("scan_result->'scanner'->>'name'") tilde scannerCriteria.regScannerName) and
                            (rawParam(VERSION_EXPRESSION) greaterEq arrayParam(minVersionArray)) and
                            (rawParam(VERSION_EXPRESSION) less arrayParam(maxVersionArray))
                }.map { it.scanResult }
                    // TODO: Currently the query only accounts for the scanner criteria. Ideally also the provenance
                    //       should be checked in the query to reduce the downloaded data.
                    .filter { it.provenance.matches(pkg) }
                    // The scanner compatibility is already checked in the query, but filter here again to be on the
                    // safe side.
                    .filter { scannerCriteria.matches(it.scanner) }

                Success(scanResults)
            }
        } catch (e: Exception) {
            when (e) {
                is JsonProcessingException, is SQLException -> {
                    e.showStackTrace()

                    val message = "Could not read scan results for ${pkg.id.toCoordinates()} with " +
                            "$scannerCriteria from database: ${e.collectMessagesAsString()}"

                    log.info { message }
                    Failure(message)
                }
                else -> throw e
            }
        }
    }

    override fun readInternal(
        packages: List<Package>,
        scannerCriteria: ScannerCriteria
    ): Result<Map<Identifier, List<ScanResult>>> {
        if (packages.isEmpty()) return Success(emptyMap())

        val minVersionArray = with(scannerCriteria.minVersion) { intArrayOf(major, minor, patch) }
        val maxVersionArray = with(scannerCriteria.maxVersion) { intArrayOf(major, minor, patch) }

        @Suppress("TooGenericExceptionCaught")
        return try {
            val scanResults = runBlocking(Dispatchers.IO) {
                packages.chunked(max(packages.size / LocalScanner.NUM_STORAGE_THREADS, 1)).map { chunk ->
                    suspendedTransactionAsync {
                        @Suppress("MaxLineLength")
                        ScanResultDao.find {
                            (ScanResults.identifier inList chunk.map { it.id.toCoordinates() }) and
                                    (rawParam("scan_result->'scanner'->>'name'") tilde scannerCriteria.regScannerName) and
                                    (rawParam(VERSION_EXPRESSION) greaterEq arrayParam(minVersionArray)) and
                                    (rawParam(VERSION_EXPRESSION) less arrayParam(maxVersionArray))
                        }.map { it.identifier to it.scanResult }
                    }
                }.flatMap { it.await() }
                    .groupBy { it.first }
                    .mapValues { (_, results) -> results.map { it.second } }
                    .mapValues { (id, results) ->
                        val pkg = packages.single { it.id == id }

                        results
                            // TODO: Currently the query only accounts for the scanner criteria. Ideally also the
                            //       provenance should be checked in the query to reduce the downloaded data.
                            .filter { it.provenance.matches(pkg) }
                            // The scanner compatibility is already checked in the query, but filter here again to be on
                            // the safe side.
                            .filter { scannerCriteria.matches(it.scanner) }
                    }
            }

            Success(scanResults)
        } catch (e: Exception) {
            when (e) {
                is JsonProcessingException, is SQLException -> {
                    e.showStackTrace()

                    val message = "Could not read scan results with $scannerCriteria from database: " +
                            e.collectMessagesAsString()

                    log.info { message }
                    Failure(message)
                }
                else -> throw e
            }
        }
    }

    override fun addInternal(id: Identifier, scanResult: ScanResult): Result<Unit> {
        log.info { "Storing scan result for ${id.toCoordinates()} in storage." }

        // TODO: Check if there is already a matching entry for this provenance and scanner details.

        try {
            transaction {
                ScanResultDao.new {
                    identifier = id
                    this.scanResult = scanResult
                }
            }
        } catch (e: SQLException) {
            e.showStackTrace()

            val message = "Could not store scan result for '${id.toCoordinates()}': ${e.collectMessagesAsString()}"
            log.warn { message }

            return Failure(message)
        }

        return Success(Unit)
    }
}
