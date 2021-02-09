/*
 * Copyright (C) 2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties

import kotlin.time.measureTimedValue

import org.ossreviewtoolkit.GlobalOptions
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.PostgresStorageConfiguration
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.utils.ORT_FULL_NAME
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.expandTilde
import org.ossreviewtoolkit.utils.formatSizeInMib
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.perf
import org.ossreviewtoolkit.utils.showStackTrace

class UploadResultToPostgresCommand : CliktCommand(
    name = "upload-result-to-postgres",
    help = "Upload an ORT result to a PostgreSQL database.",
    epilog = "EXPERIMENTAL: The command is still in development and usage will likely change in the near future. The " +
            "command expects that a PostgresStorage for the scanner is configured."
) {
    private val ortFile by option(
        "--ort-file", "-i",
        help = "The ORT result file to read as input."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()
        .inputGroup()

    private val tableName by option(
        "--table-name",
        help = "The name of the table to upload results to."
    ).required()

    private val columnName by option(
        "--column-name",
        help = "The name of the JSONB column to store the ORT result."
    ).required()

    private val globalOptionsForSubcommands by requireObject<GlobalOptions>()

    override fun run() {
        val (ortResult, duration) = measureTimedValue { ortFile.readValue<OrtResult>() }

        log.perf {
            "Read ORT result from '${ortFile.name}' (${ortFile.formatSizeInMib}) in ${duration.inMilliseconds}ms."
        }

        val postgresConfig = globalOptionsForSubcommands.config.scanner?.storages?.values
            ?.filterIsInstance<PostgresStorageConfiguration>()?.singleOrNull()

        requireNotNull(postgresConfig) {
            "No PostgreSQL storage is configured for the scanner."
        }

        require(tableName.isNotBlank()) {
            "The table name must not be blank."
        }

        require(columnName.isNotBlank()) {
            "The column name must not be blank."
        }

        createConnection(postgresConfig).use { connection ->
            val query = "INSERT INTO ${postgresConfig.schema}.$tableName ($columnName) VALUES (to_json(?::json)::jsonb)"

            val json = jsonMapper.writeValueAsString(ortResult.withResolvedScopes()).escapeNull()

            try {
                val statement = connection.prepareStatement(query)
                statement.setString(1, json)
                statement.execute()

                println("Successfully stored ORT result.")
            } catch (e: SQLException) {
                e.showStackTrace()

                println("Could not store ORT result: ${e.collectMessagesAsString()}")
            }
        }
    }

    private fun createConnection(config: PostgresStorageConfiguration): Connection {
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
        properties["ApplicationName"] = "$ORT_FULL_NAME - CLI - $commandName"

        // Configure SSL, see: https://jdbc.postgresql.org/documentation/head/connect.html
        // Note that the "ssl" property is only a fallback in case "sslmode" is not used. Since we always set
        // "sslmode", "ssl" is not required.
        properties["sslmode"] = config.sslmode
        config.sslcert?.let { properties["sslcert"] = it }
        config.sslkey?.let { properties["sslkey"] = it }
        config.sslrootcert?.let { properties["sslrootcert"] = it }

        return DriverManager.getConnection(config.url, properties)
    }

    private fun String.escapeNull() = replace("\\u0000", "\\\\u0000")
}
