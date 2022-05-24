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

package org.ossreviewtoolkit.scanner.experimental

import java.sql.SQLException

import javax.sql.DataSource

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SchemaUtils.withDataBaseLock
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.utils.DatabaseUtils.checkDatabaseEncoding
import org.ossreviewtoolkit.model.utils.DatabaseUtils.tableExists
import org.ossreviewtoolkit.model.utils.DatabaseUtils.transaction
import org.ossreviewtoolkit.scanner.storages.utils.jsonb
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.ort.log
import org.ossreviewtoolkit.utils.ort.showStackTrace

class ProvenanceBasedPostgresStorage(
    /**
     * The JDBC data source to obtain database connections.
     */
    private val dataSource: Lazy<DataSource>,

    /**
     * The name of the table used for storing scan results.
     */
    private val tableName: String = "provenance_scan_results"
) : ProvenanceBasedScanStorage {
    private val table = ProvenanceScanResults(tableName)

    /** The [Database] instance on which all operations are executed. */
    private val database by lazy {
        Database.connect(dataSource.value).apply {
            transaction {
                withDataBaseLock {
                    if (!tableExists(tableName)) {
                        checkDatabaseEncoding()
                        SchemaUtils.createMissingTablesAndColumns(table)
                    }
                }
            }
        }
    }

    override fun read(provenance: KnownProvenance): List<ScanResult> {
        requireEmptyVcsPath(provenance)

        try {
            return database.transaction {
                val query = table.selectAll()

                when (provenance) {
                    is ArtifactProvenance -> {
                        query.andWhere {
                            table.artifactUrl eq provenance.sourceArtifact.url and
                                    (table.artifactHash eq provenance.sourceArtifact.hash.value)
                        }
                    }

                    is RepositoryProvenance -> {
                        query.andWhere {
                            table.vcsType eq provenance.vcsInfo.type.toString() and
                                    (table.vcsUrl eq provenance.vcsInfo.url) and
                                    (table.vcsRevision eq provenance.resolvedRevision)
                        }
                    }
                }

                // Use the provided provenance for the result instead of building it from the stored values, because in
                // the case of a RepositoryRevision only the resolved revision matters, therefore the VcsInfo.revision
                // is not stored in the database.
                query.map {
                    ScanResult(
                        provenance = provenance,
                        scanner = ScannerDetails(
                            name = it[table.scannerName],
                            version = it[table.scannerVersion],
                            configuration = it[table.scannerConfiguration]
                        ),
                        summary = it[table.scanSummary]
                    )
                }
            }
        } catch (e: SQLException) {
            e.showStackTrace()

            log.error { "Could not read scan results: ${e.collectMessages()}" }

            throw ScanStorageException(e)
        }
    }

    // TODO: Override read(provenance, scannerCriteria) to make it more efficient by matching the scanner details in the
    //       query.

    override fun write(scanResult: ScanResult) {
        val provenance = scanResult.provenance

        requireEmptyVcsPath(provenance)

        if (provenance !is KnownProvenance) {
            throw ScanStorageException("Scan result must have a known provenance, but it is $provenance.")
        }

        try {
            database.transaction {
                table.insert {
                    when (provenance) {
                        is ArtifactProvenance -> {
                            it[artifactUrl] = provenance.sourceArtifact.url
                            it[artifactHash] = provenance.sourceArtifact.hash.value
                        }

                        is RepositoryProvenance -> {
                            it[vcsType] = provenance.vcsInfo.type.toString()
                            it[vcsUrl] = provenance.vcsInfo.url
                            it[vcsRevision] = provenance.resolvedRevision
                        }
                    }

                    it[scannerName] = scanResult.scanner.name
                    it[scannerVersion] = scanResult.scanner.version
                    it[scannerConfiguration] = scanResult.scanner.configuration
                    it[scanSummary] = scanResult.summary
                }
            }
        } catch (e: SQLException) {
            e.showStackTrace()

            log.error { "Could not write scan result: ${e.collectMessages()}" }

            throw ScanStorageException(e)
        }
    }
}

private class ProvenanceScanResults(tableName: String) : IntIdTable(tableName) {
    val artifactUrl = text("artifact_url").nullable()
    val artifactHash = text("artifact_hash").nullable()
    val vcsType = text("vcs_type").nullable()
    val vcsUrl = text("vcs_url").nullable()
    val vcsRevision = text("vcs_revision").nullable()
    val scannerName = text("scanner_name")
    val scannerVersion = text("scanner_version")
    val scannerConfiguration = text("scanner_configuration")
    val scanSummary = jsonb("scan_summary", ScanSummary::class)

    init {
        // Indices to prevent duplicate entries.
        uniqueIndex(artifactUrl, artifactHash, scannerName, scannerVersion, scannerConfiguration)
        uniqueIndex(vcsType, vcsUrl, vcsRevision, scannerName, scannerVersion, scannerConfiguration)

        // Indices to improve lookup performance.
        index(isUnique = false, artifactUrl, artifactHash)
        index(isUnique = false, vcsType, vcsUrl, vcsRevision)
    }
}
