/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import java.sql.Connection
import java.time.Instant

import org.ossreviewtoolkit.helper.utils.ORTH_NAME
import org.ossreviewtoolkit.helper.utils.writeOrtResult
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.config.PostgresStorageConfiguration
import org.ossreviewtoolkit.model.config.StorageType
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.utils.DatabaseUtils
import org.ossreviewtoolkit.scanner.provenance.PackageProvenanceResolutionResult
import org.ossreviewtoolkit.scanner.provenance.ResolvedArtifactProvenance
import org.ossreviewtoolkit.scanner.provenance.ResolvedRepositoryProvenance
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.ort.ORT_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory

internal class CreateAnalyzerResultCommand : CliktCommand(
    help = "Creates an analyzer result that contains packages for the given list of package ids. The result contains " +
            "only packages which have a corresponding ScanCode scan result in the postgres storage."
) {
    private val packageIdsFile by option(
        "--package-ids-file",
        help = "The list of package ids to put into the output analyzer result in plain text with one entry per line."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val ortFile by option(
        "--ort-file", "-o",
        help = "The ORT file to write the generated synthetic analyzer result to."
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

    private val configArguments by option(
        "-P",
        help = "Override a key-value pair in the configuration file. For example: " +
                "-P ort.scanner.storages.postgres.connection.schema=testSchema"
    ).associate()

    private val scancodeVersion by option(
        "--scancode-version",
        help = "The ScanCode version to match for in the scan results. If blank, any ScanCode version is matched."
    )

    override fun run() {
        val ids = packageIdsFile.readLines().filterNot { it.isBlank() }.map { Identifier(it.trim()) }
        val packages = openDatabaseConnection().use { connection ->
            getScannedPackages(connection, ids, scancodeVersion?.takeIf { it.isNotBlank() })
        }.filterMaxByRowId()

        val ortResult = createAnalyzerResult(packages)

        println("Writing analyzer result with ${packages.size} packages to '${ortFile.absolutePath}'.")
        writeOrtResult(ortResult, ortFile)
    }

    private fun openDatabaseConnection(): Connection {
        val ortConfig = OrtConfiguration.load(configArguments, configFile)

        val storageConfig = ortConfig.scanner.storages.orEmpty().values
            .filterIsInstance<PostgresStorageConfiguration>()
            .firstOrNull { it.type == StorageType.PROVENANCE_BASED }
            ?: throw IllegalArgumentException("postgresStorage not configured.")

        val dataSource = DatabaseUtils.createHikariDataSource(
            config = storageConfig.connection,
            applicationNameSuffix = ORTH_NAME,
            maxPoolSize = 1
        )

        return dataSource.value.connection
    }
}

private data class ScannedPackage(
    val rowId: Int,
    val id: Identifier,
    val provenance: Provenance

)

private fun getScannedPackages(
    connection: Connection,
    ids: Collection<Identifier>,
    scanCodeVersion: String?
): List<ScannedPackage> {
    val whereClause = listOfNotNull(
        "p.identifier = ANY(?)",
        "p.vcs_type IS NOT DISTINCT FROM s.vcs_type",
        "p.vcs_url IS NOT DISTINCT FROM s.vcs_url",
        "p.vcs_revision IS NOT DISTINCT FROM s.vcs_revision",
        "p.artifact_url IS NOT DISTINCT FROM s.artifact_url",
        "p.artifact_hash IS NOT DISTINCT FROM s.artifact_hash",
        "s.scanner_name = 'ScanCode'",
        scanCodeVersion?.takeUnless { it.isEmpty() }?.let { "s.scanner_version = '$it'" }
    ).joinToString(" AND ")

    val query = """
        SELECT DISTINCT
            p.id,
            p.identifier,
            p.result
        FROM 
            package_provenances p, provenance_scan_results s 
        WHERE
            $whereClause;
    """.trimIndent()

    val resultSet = connection.prepareStatement(query).apply {
        val array = connection.createArrayOf("VARCHAR", ids.distinct().map { it.toCoordinates() }.toTypedArray())
        setArray(1, array)
    }.executeQuery()

    val result = mutableListOf<ScannedPackage>()

    while (resultSet.next()) {
        val rowId = resultSet.getInt("id")
        val id = Identifier(resultSet.getString("identifier"))

        val provenanceResolutionResult = jsonMapper.readValue(
            resultSet.getString("result"),
            PackageProvenanceResolutionResult::class.java
        )

        val provenance = when (provenanceResolutionResult) {
            is ResolvedRepositoryProvenance -> provenanceResolutionResult.provenance
            is ResolvedArtifactProvenance -> provenanceResolutionResult.provenance
            else -> continue
        }

        result += ScannedPackage(rowId, id, provenance)
    }

    return result.distinct()
}

private fun createAnalyzerResult(packages: Collection<ScannedPackage>) = OrtResult.EMPTY.copy(
    analyzer = AnalyzerRun(
        startTime = Instant.now(),
        endTime = Instant.now(),
        environment = Environment(),
        config = AnalyzerConfiguration(),
        result = AnalyzerResult(
            projects = emptySet(),
            packages = packages.mapTo(mutableSetOf()) { it.toPackage() }
        )
    )
)

private fun Collection<ScannedPackage>.filterMaxByRowId(): List<ScannedPackage> {
    fun filterMaxByRowId(predicate: (ScannedPackage) -> Boolean) =
        filter(predicate).groupBy { it.id }.mapNotNull { (_, packages) ->
            packages.maxByOrNull { it.rowId }
        }

    return filterMaxByRowId { it.provenance is RepositoryProvenance } +
        filterMaxByRowId { it.provenance is ArtifactProvenance }
}

private fun ScannedPackage.toPackage(): Package {
    val sourceArtifact = if (provenance is ArtifactProvenance) {
        provenance.sourceArtifact
    } else {
        RemoteArtifact.EMPTY
    }

    val vcs = if (provenance is RepositoryProvenance) {
        provenance.vcsInfo.copy(revision = provenance.resolvedRevision)
    } else {
        VcsInfo.EMPTY
    }

    return Package(
        id = id,
        declaredLicenses = emptySet(),
        concludedLicense = null,
        description = "",
        homepageUrl = "",
        binaryArtifact = RemoteArtifact.EMPTY,
        sourceArtifact = sourceArtifact,
        vcs = vcs,
        vcsProcessed = vcs
    )
}
