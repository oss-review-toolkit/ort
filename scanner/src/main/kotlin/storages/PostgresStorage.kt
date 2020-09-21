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
import java.sql.Connection
import java.sql.SQLException

import org.ossreviewtoolkit.model.Failure
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Result
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanResultContainer
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.Success
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.showStackTrace

/**
 * The Postgres storage back-end.
 */
class PostgresStorage(
    /**
     * The JDBC connection to the database.
     */
    private val connection: Connection,

    /**
     * The name of the database to use.
     */
    private val schema: String
) : ScanResultsStorage() {
    private val table = "scan_results" // TODO: make configurable

    /**
     * Setup the database.
     */
    fun setupDatabase() {
        val statement = connection.createStatement()
        val resultSet = statement.executeQuery("SHOW client_encoding")
        if (resultSet.next()) {
            val clientEncoding = resultSet.getString(1)
            if (clientEncoding != "UTF8") {
                log.warn { "The database's client_encoding is '$clientEncoding' but should be 'UTF8'." }
            }
        }

        if (!tableExists()) {
            log.info { "Trying to create table '$table'." }
            if (!createTable()) {
                throw IOException("Failed to create table '$table'.")
            }
            log.info { "Successfully created table '$table'." }
        }
    }

    private fun tableExists(): Boolean {
        val statement = connection.createStatement()
        val resultSet = statement.executeQuery("SELECT to_regclass('$schema.$table')")
        return resultSet.next() && resultSet.getString(1).let { result ->
            // At least PostgreSQL 9.6 reports the result including the schema prefix.
            result == table || result == "$schema.$table"
        }
    }

    private fun createTable(): Boolean {
        val statement = connection.createStatement()

        statement.execute("CREATE SEQUENCE $schema.${table}_id_seq")

        statement.execute(
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

        statement.execute(
            """
            CREATE INDEX identifier
                ON $schema.$table USING btree
                (identifier COLLATE pg_catalog."default")
                TABLESPACE pg_default
            """.trimIndent()
        )

        statement.execute(
            """
            CREATE INDEX identifier_and_scanner_version
                ON $schema.$table USING btree
                (
                    identifier,
                    (scan_result->'scanner'->>'name'),
                    substring(scan_result->'scanner'->>'version' from '([0-9]+\.[0-9]+)\.?.*'),
                    (scan_result->'scanner'->>'configuration')
                )
                TABLESPACE pg_default
            """.trimIndent()
        )

        return tableExists()
    }

    override fun readFromStorage(id: Identifier): Result<ScanResultContainer> {
        val query = "SELECT scan_result FROM $schema.$table WHERE identifier = ?"

        @Suppress("TooGenericExceptionCaught")
        return try {
            val statement = connection.prepareStatement(query).apply {
                setString(1, id.toCoordinates())
            }

            val resultSet = statement.executeQuery()
            val scanResults = mutableListOf<ScanResult>()

            while (resultSet.next()) {
                val scanResult = jsonMapper.readValue<ScanResult>(resultSet.getString(1).unescapeNull())
                scanResults += scanResult
            }

            Success(ScanResultContainer(id, scanResults))
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

    override fun readFromStorage(pkg: Package, scannerDetails: ScannerDetails): Result<ScanResultContainer> {
        val version = Semver(scannerDetails.version)

        val query = """
            SELECT scan_result
              FROM $schema.$table
              WHERE identifier = ?
                AND scan_result->'scanner'->>'name' = ?
                AND substring(scan_result->'scanner'->>'version' from '([0-9]+\.[0-9]+)\.?.*') = ?
                AND scan_result->'scanner'->>'configuration' = ?;
        """.trimIndent()

        @Suppress("TooGenericExceptionCaught")
        return try {
            val statement = connection.prepareStatement(query).apply {
                setString(1, pkg.id.toCoordinates())
                setString(2, scannerDetails.name)
                setString(3, "${version.major}.${version.minor}")
                setString(4, scannerDetails.configuration)
            }

            val resultSet = statement.executeQuery()
            val scanResults = mutableListOf<ScanResult>()

            while (resultSet.next()) {
                val scanResult = jsonMapper.readValue<ScanResult>(resultSet.getString(1).unescapeNull())
                scanResults += scanResult
            }

            // TODO: Currently the query only accounts for the scanner details. Ideally also the provenance should be
            //       checked in the query to reduce the downloaded data.
            scanResults.retainAll { it.provenance.matches(pkg) }
            // The scanner compatibility is already checked in the query, but filter here again to be on the safe side.
            scanResults.retainAll { scannerDetails.isCompatible(it.scanner) }

            Success(ScanResultContainer(pkg.id, scanResults))
        } catch (e: Exception) {
            when (e) {
                is JsonProcessingException, is SQLException -> {
                    e.showStackTrace()

                    val message = "Could not read scan results for ${pkg.id.toCoordinates()} with " +
                            "$scannerDetails from database: ${e.collectMessagesAsString()}"

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

        val scanResultJson = jsonMapper.writeValueAsString(scanResult).escapeNull()

        try {
            val statement = connection.prepareStatement(query)
            statement.setString(1, id.toCoordinates())
            statement.setString(2, scanResultJson)
            statement.execute()
        } catch (e: SQLException) {
            e.showStackTrace()

            val message = "Could not store scan result for '${id.toCoordinates()}': ${e.collectMessagesAsString()}"
            log.warn { message }

            return Failure(message)
        }

        return Success(Unit)
    }

    /**
     * The null character "\u0000" can appear in raw scan results, for example in ScanCode if the matched text for a
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
