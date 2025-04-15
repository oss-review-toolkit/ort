/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.helper.commands

import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import java.io.File
import java.sql.Connection

import kotlin.time.measureTime

import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream

import org.ossreviewtoolkit.helper.utils.ORTH_NAME
import org.ossreviewtoolkit.helper.utils.OrtHelperCommand
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.config.PostgresStorageConfiguration
import org.ossreviewtoolkit.model.utils.DatabaseUtils
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.ort.ORT_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory

internal class DownloadResultsFromPostgresCommand : OrtHelperCommand(
    name = "download-results-from-postgres",
    help = "Download an ORT result from a PostgreSQL database. The symmetric command to ORT's " +
        " upload-result-to-postgres command."
) {
    private val outputDir by option(
        "--output-dir", "-o",
        help = "The directory to write the ORT results to."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val tableName by option(
        "--table-name",
        help = "The name of the table which holds the ORT results."
    ).required()

    private val columnName by option(
        "--column-name",
        help = "The name of the JSONB column containing the ORT result."
    ).required()

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
            "-P ort.scanner.storages.postgres.connection.schema=testSchema"
    ).associate()

    private val startId by option(
        "--start-id",
        help = "The smallest row-id to download."
    ).convert { it.toInt() }
        .default(-1)

    override fun run() {
        if (!outputDir.exists()) outputDir.mkdirs()

        val localStorage = OrtResultStorage(outputDir)
        val localRowIds = localStorage.getIds()

        openDatabaseConnection().use { connection ->
            val remoteRowIds = fetchRowIds(connection)
            val missingRowIds = remoteRowIds - localRowIds
            println(
                "Requested the download of ${remoteRowIds.size} results of which ${missingRowIds.size} are missing."
            )

            missingRowIds.forEachIndexed { i, rowId ->
                println("[${i + 1} / ${missingRowIds.size}] downloading row $rowId")

                val duration = measureTime {
                    val ortResultJson = fetchOrtResult(connection, rowId)

                    if (ortResultJson != null) localStorage.add(rowId, ortResultJson)
                }

                println("[${i + 1} / ${missingRowIds.size}] took $duration.")
            }
        }
    }

    private fun fetchOrtResult(connection: Connection, rowId: Int): String? {
        // TODO: The transmission is relatively slow currently due to lack of compression. Check whether the gzip
        //       extension can help speed this up https://github.com/pramsey/pgsql-gzip.

        val query = "SELECT t.$columnName::TEXT AS result FROM $tableName t WHERE id = ?;"

        val resultSet = connection.prepareStatement(query).apply { setInt(1, rowId) }.executeQuery()

        return if (resultSet.next()) {
            resultSet.getString("result")
        } else {
            null
        }
    }

    private fun fetchRowIds(connection: Connection): Set<Int> {
        val query = "SELECT t.id FROM $tableName t WHERE id >= ?;"

        val resultSet = connection.prepareStatement(query).apply {
            setInt(1, startId)
        }.executeQuery()

        val result = mutableSetOf<Int>()

        while (resultSet.next()) {
            result += resultSet.getInt("id")
        }

        return result
    }

    private fun openDatabaseConnection(): Connection {
        val storageConfig = OrtConfiguration.load(configArguments, configFile).scanner.storages?.values
            ?.filterIsInstance<PostgresStorageConfiguration>()?.firstOrNull()
            ?: throw IllegalArgumentException("postgresStorage not configured.")

        val dataSource = DatabaseUtils.createHikariDataSource(
            config = storageConfig.connection,
            applicationNameSuffix = ORTH_NAME,
            maxPoolSize = 1
        )

        return dataSource.value.connection
    }
}

private class OrtResultStorage(private val storageDir: File) {
    fun getIds(): Set<Int> {
        val ids = storageDir.walk().maxDepth(1).filter { it.isDirectory }.mapNotNull { file -> file.name.toIntOrNull() }

        return ids.filterTo(mutableSetOf()) { hasFile(it) }
    }

    fun add(id: Int, ortResultJson: String) {
        val ortResultFile = ortResultFile(id)
        ortResultFile.parentFile.mkdirs()

        XZCompressorOutputStream(ortResultFile.outputStream()).use { outputStream ->
            ortResultJson.byteInputStream().use { it.copyTo(outputStream) }
        }

        val hash = HashAlgorithm.MD5.calculate(ortResultFile)
        md5sumFile(id).writeText(hash)
    }

    private fun hasFile(id: Int): Boolean {
        val md5sumFile = md5sumFile(id)
        val ortResultFile = ortResultFile(id)

        return md5sumFile.isFile && ortResultFile.isFile &&
            HashAlgorithm.MD5.calculate(ortResultFile) == md5sumFile.readText()
    }

    private fun ortResultFile(id: Int): File = storageDir.resolve("$id/ort-result.json.xz")

    private fun md5sumFile(id: Int): File = storageDir.resolve("$id/ort-result.json.xz.md5")
}
