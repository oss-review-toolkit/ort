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
import com.fasterxml.jackson.module.kotlin.readValue

import com.vdurmont.semver4j.Semver

import java.io.IOException
import java.sql.Array
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.Statement

import javax.sql.DataSource

import kotlin.time.measureTime
import kotlin.time.measureTimedValue

import org.ossreviewtoolkit.model.Failure
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Result
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanResultContainer
import org.ossreviewtoolkit.model.Success
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.scanner.ScannerCriteria
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.perf
import org.ossreviewtoolkit.utils.showStackTrace

/**
 * The Postgres storage back-end.
 */
class PostgresStorage(
    /**
     * The JDBC data source to obtain database connections.
     */
    private val dataSource: DataSource,

    /**
     * The name of the database to use.
     */
    private val schema: String
) : ScanResultsStorage() {
    companion object {
        /** Expression to reference the scanner version as an array. */
        private const val VERSION_ARRAY =
            "string_to_array(regexp_replace(scan_result->'scanner'->>'version', '[^0-9.]', '', 'g'), '.')"

        /** Expression to convert the scanner version to a numeric array for comparisons. */
        private const val VERSION_EXPRESSION = "$VERSION_ARRAY::int[]"

        /**
         * The null character "\u0000" can appear in scan results, for example in ScanCode if the matched text for a
         * license or copyright contains this character. Since it is not allowed in PostgreSQL JSONB columns we need to
         * escape it before writing a string to the database.
         * See: [https://www.postgresql.org/docs/11/datatype-json.html]
         */
        private fun String.escapeNull() = replace("\\u0000", "\\\\u0000")

        /**
         * Unescape the null character "\u0000". For details see [escapeNull].
         */
        private fun String.unescapeNull() = replace("\\\\u0000", "\\u0000")
    }

    private val table = "scan_results" // TODO: make configurable

    /**
     * Setup the database.
     */
    fun setupDatabase() {
        executeStatement {
            executeQuery("SHOW client_encoding").use { resultSet ->
                if (resultSet.next()) {
                    val clientEncoding = resultSet.getString(1)
                    if (clientEncoding != "UTF8") {
                        PostgresStorage.log.warn {
                            "The database's client_encoding is '$clientEncoding' but should be 'UTF8'."
                        }
                    }
                }

                if (!tableExists()) {
                    PostgresStorage.log.info { "Trying to create table '$table'." }
                    if (!createTable()) {
                        throw IOException("Failed to create table '$table'.")
                    }
                    PostgresStorage.log.info { "Successfully created table '$table'." }
                }
            }
        }
    }

    private fun tableExists(): Boolean =
        executeStatement {
            executeQuery("SELECT to_regclass('$schema.$table')").use { resultSet ->
                resultSet.next() && resultSet.getString(1).let { result ->
                    // At least PostgreSQL 9.6 reports the result including the schema prefix.
                    result == table || result == "$schema.$table"
                }
            }
        }

    private fun createTable(): Boolean {
        executeStatement {
            execute("CREATE SEQUENCE $schema.${table}_id_seq")

            execute(
                """
            CREATE TABLE $schema.$table
            (
                id integer NOT NULL DEFAULT nextval('$schema.${table}_id_seq'::regclass),
                identifier text COLLATE pg_catalog."default" NOT NULL,
                scan_result jsonb NOT NULL,
                CONSTRAINT ${table}_pkey PRIMARY KEY (id)
            )
            WITH (
                OIDS = FALSE
            )
            TABLESPACE pg_default
            """.trimIndent()
            )

            execute(
                """
            CREATE INDEX identifier
                ON $schema.$table USING btree
                (identifier COLLATE pg_catalog."default")
                TABLESPACE pg_default
            """.trimIndent()
            )

            execute(
                """
            CREATE INDEX identifier_and_scanner_version
                ON $schema.$table USING btree
                (
                    identifier,
                    (scan_result->'scanner'->>'name'),
                    $VERSION_ARRAY
                )
                TABLESPACE pg_default
            """.trimIndent()
            )
        }

        return tableExists()
    }

    override fun readFromStorage(id: Identifier): Result<ScanResultContainer> {
        val query = "SELECT scan_result FROM $schema.$table WHERE identifier = ?"

        @Suppress("TooGenericExceptionCaught")
        return try {
            executePreparedStatement(query) {
                setString(1, id.toCoordinates())

                val (resultSet, queryDuration) = measureTimedValue { executeQuery() }

                resultSet.use {
                    PostgresStorage.log.perf {
                        "Fetched scan results for '${id.toCoordinates()}' from ${javaClass.simpleName} in " +
                                "${queryDuration.inMilliseconds}ms."
                    }

                    val scanResults = mutableListOf<ScanResult>()

                    val deserializationDuration = measureTime {
                        while (it.next()) {
                            val scanResult = jsonMapper.readValue<ScanResult>(it.getString(1).unescapeNull())
                            scanResults += scanResult
                        }
                    }

                    PostgresStorage.log.perf {
                        "Deserialized ${scanResults.size} scan results for '${id.toCoordinates()}' in " +
                                "${deserializationDuration.inMilliseconds}ms."
                    }

                    Success(ScanResultContainer(id, scanResults))
                }
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
        val query = """
            SELECT scan_result
              FROM $schema.$table
              WHERE identifier = ?
                AND scan_result->'scanner'->>'name' ~ ?
                AND $VERSION_EXPRESSION >= ?
                AND $VERSION_EXPRESSION < ?;
        """.trimIndent()

        @Suppress("TooGenericExceptionCaught")
        return try {
            executePreparedStatement(query) {
                setString(1, pkg.id.toCoordinates())
                setString(2, scannerCriteria.regScannerName)
                setArray(3, scannerCriteria.minVersion.toSqlArray(connection))
                setArray(4, scannerCriteria.maxVersion.toSqlArray(connection))

                val (resultSet, queryDuration) = measureTimedValue { executeQuery() }

                resultSet.use {
                    PostgresStorage.log.perf {
                        "Fetched scan results for '${pkg.id.toCoordinates()}' from ${javaClass.simpleName} in " +
                                "${queryDuration.inMilliseconds}ms."
                    }

                    val scanResults = mutableListOf<ScanResult>()

                    val deserializationDuration = measureTime {
                        while (it.next()) {
                            val scanResult = jsonMapper.readValue<ScanResult>(it.getString(1).unescapeNull())
                            scanResults += scanResult
                        }
                    }

                    PostgresStorage.log.perf {
                        "Deserialized ${scanResults.size} scan results for '${pkg.id.toCoordinates()}' in " +
                                "${deserializationDuration.inMilliseconds}ms."
                    }

                    // TODO: Currently the query only accounts for the scanner criteria. Ideally also the provenance
                    //       should be checked in the query to reduce the downloaded data.
                    scanResults.retainAll { it.provenance.matches(pkg) }
                    // The scanner compatibility is already checked in the query, but filter here again to be on the
                    // safe side.
                    scanResults.retainAll { scannerCriteria.matches(it.scanner) }

                    Success(ScanResultContainer(pkg.id, scanResults))
                }
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
        val query = "INSERT INTO $schema.$table (identifier, scan_result) VALUES (?, to_json(?::json)::jsonb)"

        val (scanResultJson, serializationDuration) = measureTimedValue {
            jsonMapper.writeValueAsString(scanResult).escapeNull()
        }

        log.perf {
            "Serialized scan result for '${id.toCoordinates()}' in ${serializationDuration.inMilliseconds}ms."
        }

        try {
            executePreparedStatement(query) {
                setString(1, id.toCoordinates())
                setString(2, scanResultJson)

                val insertDuration = measureTime { execute() }

                PostgresStorage.log.perf {
                    "Inserted scan result for '${id.toCoordinates()}' into ${javaClass.simpleName} in " +
                            "${insertDuration.inMilliseconds}ms."
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

    /**
     * Obtain a connection from the data source and execute the given [block] with it. Make sure that the connection
     * is correctly closed afterwards.
     */
    private fun <T> execute(block: (Connection) -> T): T =
        dataSource.connection.use {
            block(it)
        }

    /**
     * Create a [PreparedStatement] defined by the given [SQL statement][sql] and execute the given [block] with it
     * against the underlying database. Make sure that all resources are properly closed afterwards.
     */
    private fun <T> executePreparedStatement(sql: String, block: PreparedStatement.() -> T): T =
        execute { connection ->
            val statement = connection.prepareStatement(sql)
            statement.use(block)
        }

    /**
     * Create a [Statement] and execute the given [block] with it against the underlying database. Make sure that all
     * resources are properly closed afterwards.
     */
    private fun <T> executeStatement(block: Statement.() -> T): T =
        execute { connection ->
            val statement = connection.createStatement()
            statement.use(block)
        }

    /**
     * Generate an SQL array parameter for the version numbers contained in this [Semver] using the given [connection].
     */
    private fun Semver.toSqlArray(connection: Connection): Array {
        val versionArray = intArrayOf(major, minor, patch)
        return connection.createArrayOf("int4", versionArray.toTypedArray())
    }
}
