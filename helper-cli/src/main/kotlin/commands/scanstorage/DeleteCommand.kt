/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.helper.commands.scanstorage

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.regexp
import org.jetbrains.exposed.sql.compoundAnd
import org.jetbrains.exposed.sql.compoundOr
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select

import org.ossreviewtoolkit.helper.common.ORTH_NAME
import org.ossreviewtoolkit.model.SourceCodeOrigin
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.config.PostgresStorageConfiguration
import org.ossreviewtoolkit.model.utils.DatabaseUtils
import org.ossreviewtoolkit.model.utils.DatabaseUtils.transaction
import org.ossreviewtoolkit.model.utils.rawParam
import org.ossreviewtoolkit.scanner.storages.utils.ScanResults
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.core.ORT_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.core.log
import org.ossreviewtoolkit.utils.core.ortConfigDirectory

internal class DeleteCommand : CliktCommand(
    help = "Removes stored scan results matching the options or all results if no options are given."
) {
    private val configFile by option(
        "--config",
        help = "The path to the ORT configuration file that configures the scan results storage."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .default(ortConfigDirectory.resolve(ORT_CONFIG_FILENAME))

    private val configArguments by option(
        "-P",
        help = "Override a key-value pair in the configuration file. For example: " +
                "-P ort.scanner.storages.postgres.schema=testSchema"
    ).associate()

    private val sourceCodeOrigins by option(
        "--source-code-origins",
        help = "The origin of the scan results that should be deleted."
    ).enum<SourceCodeOrigin>().split(",").default(emptyList())

    private val packageId by option(
        "--package-id",
        help = "A regular expression for matching the package id."
    )

    private val dryRun by option(
        "--dry-run",
        help = "Perform a dry run without actually deleting anything."
    ).flag()

    override fun run() {
        val database = connectPostgresStorage(OrtConfiguration.load(configArguments, configFile))

        val provenanceKeys = sourceCodeOrigins.map { origin ->
            when (origin) {
                SourceCodeOrigin.ARTIFACT -> "source_artifact"
                SourceCodeOrigin.VCS -> "vcs_info"
            }
        }

        val identifierCondition = packageId?.let { ScanResults.identifier regexp it }
        val provenanceConditions = provenanceKeys.map { key ->
            rawParam("scan_result->'provenance'->>'$key'").isNotNull()
        }.takeIf { it.isNotEmpty() }?.compoundOr()

        val conditions = listOfNotNull(identifierCondition, provenanceConditions)
        if (conditions.isEmpty()) {
            // Default to the safe option to not delete anything if no conditions are given.
            println("Not specified what entries to delete. Not deleting anything.")

            return
        }

        val condition = conditions.compoundAnd()

        if (dryRun) {
            val count = database.transaction {
                ScanResults.select { condition }.count()
            }

            println("Would delete $count scan result(s).")

            if (log.delegate.isDebugEnabled) {
                database.transaction {
                    ScanResults.slice(ScanResults.identifier).select { condition }
                        .forEach(this@DeleteCommand.log::debug)
                }
            }
        } else {
            val count = database.transaction {
                ScanResults.deleteWhere { condition }
            }

            println("Successfully deleted $count stored scan result(s).")
        }
    }

    private fun connectPostgresStorage(config: OrtConfiguration): Database {
        val storageConfig = config.scanner.storages?.get("postgresStorage") as? PostgresStorageConfiguration
            ?: throw IllegalArgumentException("postgresStorage not configured.")

        log.info {
            "Using Postgres storage with URL '${storageConfig.url}' and schema '${storageConfig.schema}'."
        }

        val dataSource = DatabaseUtils.createHikariDataSource(
            config = storageConfig,
            applicationNameSuffix = ORTH_NAME,
            maxPoolSize = 1
        )

        return Database.connect(dataSource.value)
    }
}
