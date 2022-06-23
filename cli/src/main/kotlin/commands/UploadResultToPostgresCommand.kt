/*
 * Copyright (C) 2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import java.sql.SQLException

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SchemaUtils.withDataBaseLock
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

import org.ossreviewtoolkit.cli.GlobalOptions
import org.ossreviewtoolkit.cli.utils.inputGroup
import org.ossreviewtoolkit.cli.utils.readOrtResult
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.PostgresStorageConfiguration
import org.ossreviewtoolkit.model.utils.DatabaseUtils
import org.ossreviewtoolkit.model.utils.DatabaseUtils.checkDatabaseEncoding
import org.ossreviewtoolkit.model.utils.DatabaseUtils.tableExists
import org.ossreviewtoolkit.scanner.storages.utils.jsonb
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.ort.showStackTrace

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

    private val createTable by option(
        "--create-table",
        help = "Create the table if it does not exist."
    ).flag()

    private val globalOptionsForSubcommands by requireObject<GlobalOptions>()

    override fun run() {
        val ortResult = readOrtResult(ortFile)

        val postgresConfig = globalOptionsForSubcommands.config.scanner.storages?.values
            ?.filterIsInstance<PostgresStorageConfiguration>()?.let { configs ->
                if (configs.size > 1) {
                    val config = configs.first()
                    println(
                        "Multiple PostgreSQL storages are configured, using the first one which points to schema " +
                                "${config.connection.schema} at ${config.connection.url}."
                    )
                }

                configs.firstOrNull()
            }

        requireNotNull(postgresConfig) {
            "No PostgreSQL storage is configured for the scanner."
        }

        require(tableName.isNotBlank()) {
            "The table name must not be blank."
        }

        require(columnName.isNotBlank()) {
            "The column name must not be blank."
        }

        val dataSource = DatabaseUtils.createHikariDataSource(
            config = postgresConfig.connection,
            applicationNameSuffix = "upload-result-command"
        )

        Database.connect(dataSource.value)

        val table = OrtResults(tableName, columnName)

        if (createTable) {
            transaction {
                withDataBaseLock {
                    if (!tableExists(tableName)) {
                        checkDatabaseEncoding()
                        SchemaUtils.createMissingTablesAndColumns(table)
                    }
                }
            }
        }

        try {
            transaction {
                table.insert {
                    it[result] = ortResult
                }
            }

            println("Successfully stored ORT result.")
        } catch (e: SQLException) {
            e.showStackTrace()

            println("Could not store ORT result: ${e.collectMessages()}")
        }
    }
}

private class OrtResults(tableName: String, columnName: String) : IntIdTable(tableName) {
    val result: Column<OrtResult> = jsonb(columnName, OrtResult::class)
}
