/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SchemaUtils.withDataBaseLock
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction

import org.ossreviewtoolkit.model.Failure
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Result
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanResultContainer
import org.ossreviewtoolkit.model.Success
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.scanner.ScannerCriteria
import org.ossreviewtoolkit.scanner.storages.utils.ScanResultDao
import org.ossreviewtoolkit.scanner.storages.utils.ScanResults
import org.ossreviewtoolkit.scanner.storages.utils.arrayParam
import org.ossreviewtoolkit.scanner.storages.utils.execShow
import org.ossreviewtoolkit.scanner.storages.utils.rawParam
import org.ossreviewtoolkit.scanner.storages.utils.tilde
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.showStackTrace

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

    private val table = "scan_results" // TODO: make configurable

    init {
        setupDatabase()
    }

    /**
     * Setup the database.
     */
    private fun setupDatabase() {
        Database.connect(dataSource)

        transaction {
            withDataBaseLock {
                if (!tableExists()) {
                    checkDatabaseEncoding()

                    SchemaUtils.createMissingTablesAndColumns(
                        ScanResults
                    )

                    createIdentifierAndScannerVersionIndex()
                }
            }
        }
    }

    private fun Transaction.checkDatabaseEncoding() =
        execShow("SHOW client_encoding") { resultSet ->
            if (resultSet.next()) {
                val clientEncoding = resultSet.getString(1)
                if (clientEncoding != "UTF8") {
                    PostgresStorage.log.warn {
                        "The database's client_encoding is '$clientEncoding' but should be 'UTF8'."
                    }
                }
            }
        }

    private fun Transaction.tableExists(): Boolean =
        exec("SELECT to_regclass('$table')") { resultSet ->
            resultSet.next() && resultSet.getString(1).let { result ->
                // At least PostgreSQL 9.6 reports the result including the schema prefix.
                result == table || result == "$table"
            }
        } ?: false

    private fun Transaction.createIdentifierAndScannerVersionIndex() =
        exec(
            """
            CREATE INDEX identifier_and_scanner_version
                ON $table USING btree
                (
                    identifier,
                    (scan_result->'scanner'->>'name'),
                    $VERSION_ARRAY
                )
                TABLESPACE pg_default
            """.trimIndent()
        )

    override fun readFromStorage(id: Identifier): Result<ScanResultContainer> {
        @Suppress("TooGenericExceptionCaught")
        return try {
            transaction {
                val scanResults =
                    ScanResultDao.find { ScanResults.identifier eq id.toCoordinates() }.map { it.scanResult }

                Success(ScanResultContainer(id, scanResults))
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

    override fun readFromStorage(pkg: Package, scannerCriteria: ScannerCriteria): Result<ScanResultContainer> {
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

                Success(ScanResultContainer(pkg.id, scanResults))
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

    override fun addToStorage(id: Identifier, scanResult: ScanResult): Result<Unit> {
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
