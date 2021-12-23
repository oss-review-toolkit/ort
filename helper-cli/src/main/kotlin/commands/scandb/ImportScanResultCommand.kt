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

package org.ossreviewtoolkit.helper.commands.scandb

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.common.ORTH_NAME
import org.ossreviewtoolkit.helper.common.readOrtResult
import org.ossreviewtoolkit.model.CuratedPackage
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.config.PostgresStorageConfiguration
import org.ossreviewtoolkit.model.utils.DatabaseUtils
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.core.ORT_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.core.ortConfigDirectory
import java.io.File
import java.sql.*

class ImportScanResultCommand : CliktCommand(
    help = "Removes stored scan results matching the options or all results if no options are given."
) {
    private val ortFile by option(
        "--ort-file",
        help = "The ORT file to import."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val configFile by option(
        "--config",
        help = "The path to the ORT configuration file that configures the scan results storage."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .default(ortConfigDirectory.resolve(ORT_CONFIG_FILENAME))

    private val recreateTables by option(
        "--recreate-tables",
        help = "Drop database tables to re-create the schema."
    ).flag()

    private val configArguments by option(
        "-P",
        help = "Override a key-value pair in the configuration file. For example: " +
                "-P ort.scanner.storages.postgres.schema=testSchema"
    ).associate()

    override fun run() {
        openDatabaseConnection().use { connection ->
            connection.apply {
                autoCommit = false

                if (recreateTables) dropTables()

                createTables()

                val ortResult = readOrtResult(ortFile)
                importOrtResult(ortResult)

                connection.commit()
            }
        }
    }

    private fun openDatabaseConnection(): Connection {
        val storageConfig = OrtConfiguration.load(configArguments, configFile).scanner.storages?.values
            ?.filterIsInstance<PostgresStorageConfiguration>()?.firstOrNull()
            ?: throw IllegalArgumentException("postgresStorage not configured.")

        return DatabaseUtils.createHikariDataSource(
            config = storageConfig.copy(url = "${storageConfig.url}?stringtype=unspecified"),
            applicationNameSuffix = ORTH_NAME,
            maxPoolSize = 1
        ).connection
    }
}

private fun Connection.importOrtResult(ortResult: OrtResult): Boolean {
    val ortResultId = insertOrtResult(ortResult)
    if (ortResultId == -1) return false

    val packageIds = ortResult.getPackages().associateBy({ it.pkg.id }) { insertPackages(it) }

    insertOrtResultHasPackage(ortResultId, packageIds, ortResult)
    insertOrtResultLabels(ortResultId, ortResult)
    insertNestedRepositories(ortResultId, ortResult)

    return true
}

private fun Connection.insertOrtResult(ortResult: OrtResult): Int {
    val query = """
        INSERT INTO ort_result (
            analysis_start_time,
            vcs_type,
            vcs_url,
            vcs_path,
            vcs_revision
        )
        VALUES (?,?,?,?,?)
        ON CONFLICT ON CONSTRAINT ort_result_uq DO UPDATE SET analysis_start_time=EXCLUDED.analysis_start_time
        RETURNING id;
    """// FIXME: DO NOTHING ON CONFLICT!

    return prepareStatement(query,  Statement.RETURN_GENERATED_KEYS).run {
        setTimestamp(1, Timestamp.from(ortResult.analyzer!!.startTime))
        setString(2, ortResult.repository.vcsProcessed.type.toString())
        setString(3, ortResult.repository.vcsProcessed.url)
        setString(4, ortResult.repository.vcsProcessed.path)
        setString(5, ortResult.repository.vcsProcessed.revision)

        executeUpdate()
        generatedIntKey()
    }
}

private fun Connection.insertPackages(curatedPackage: CuratedPackage): Int {
    val query = """
        INSERT INTO package (
            c_type,
            c_namespace,
            c_name,
            c_version,
            authors,
            declared_licenses,
            description,
            homepage_url,
            vcs_type,
            vcs_url,
            vcs_revision,
            vcs_path,
            source_artifact_url,
            binary_artifact_url,
            is_meta_data_only
        ) 
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) 
        ON CONFLICT DO NOTHING
        RETURNING id;
    """

    return prepareStatement(query, Statement.RETURN_GENERATED_KEYS).run {
        val pkg = curatedPackage.toUncuratedPackage()
        val id = pkg.id
        val vcs = pkg.vcsProcessed
        val authors = connection.createArrayOf("TEXT", pkg.authors.toTypedArray())
        val declaredLicenses = connection.createArrayOf("TEXT", pkg.declaredLicenses.toTypedArray())

        setString(1, id.type)
        setString(2, id.namespace)
        setString(3, id.name)
        setString(4, id.version)
        setArray(5, authors)
        setArray(6, declaredLicenses)
        setString(7, pkg.description)
        setString(8, pkg.homepageUrl)
        setString(9, vcs.type.toString())
        setString(10, vcs.url)
        setString(11, vcs.path)
        setString(12, vcs.revision)
        setString(13, pkg.sourceArtifact.url)
        setString(14, pkg.binaryArtifact.url)
        setBoolean(15, pkg.isMetaDataOnly)

        executeUpdate()
        generatedIntKey()
    }
}

private fun Connection.insertOrtResultHasPackage(
    ortResultId: Int,
    packageIds: Map<Identifier, Int>,
    ortResult: OrtResult
) {
    val query = """
        INSERT INTO ort_result_has_package (
            ort_result_id,
            package_id,
            is_excluded
        )
        VALUES(?,?,?) ON CONFLICT DO NOTHING;
    """

    packageIds.keys.forEach { id ->
        prepareStatement(query).run {
            setInt(1, ortResultId)
            setInt(2, packageIds.getValue(id))
            setBoolean(3, ortResult.isExcluded(id))
            executeUpdate()
        }
    }
}

private fun Connection.insertOrtResultLabels(
    ortResultId: Int,
    ortResult: OrtResult
) {
    val query = """
        INSERT INTO ort_result_label (
            ort_result_id,
            k,
            v
        )
        VALUES(?,?,?) ON CONFLICT DO NOTHING;
    """

    ortResult.labels.forEach { (key, csv) ->
        csv.split(",").forEach { value ->
            prepareStatement(query).run {
                setInt(1, ortResultId)
                setString(2, key)
                setString(3, value)
                executeUpdate()
            }
        }
    }
}

private fun Connection.insertNestedRepositories(
    ortResultId: Int,
    ortResult: OrtResult
) {
    val query = """
        INSERT INTO ort_result_nested_repository (
            ort_result_id,
            path,
            vcs_url
        )
        VALUES(?,?,?) ON CONFLICT DO NOTHING;
    """

    ortResult.repository.nestedRepositories.forEach { (path, vcsInfo) ->
        prepareStatement(query).run {
            setInt(1, ortResultId)
            setString(2, path)
            setString(3, vcsInfo.url)
            executeUpdate()
        }
    }
}

private fun Connection.hasOrtFile(ortFile: File): Boolean {
    val parser = JsonFactory().createParser(ortFile)

    while (parser.nextToken() !== JsonToken.END_OBJECT) {
        val fieldname = parser.getCurrentName()
        println(fieldname)

    }
    parser.close()

    return true
}

private fun Connection.dropTables(): Unit =
    executeSqlFromResource("/scandb/drop-tables.sql")

private fun Connection.createTables(): Unit =
    executeSqlFromResource("/scandb/create-tables.sql")

private fun Connection.executeSqlFromResource(resourceFile: String): Unit =
    ImportScanResultCommand::class.java.getResource(resourceFile).readText().let { prepareStatement(it).executeUpdate() }

private fun PreparedStatement.generatedIntKey(): Int = generatedKeys.run {
    if (next()) {
        getInt(1)
    } else {
        -1
    }
}
