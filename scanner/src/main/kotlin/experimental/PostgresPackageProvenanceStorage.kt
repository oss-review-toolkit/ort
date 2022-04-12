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

import javax.sql.DataSource

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SchemaUtils.withDataBaseLock
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.utils.DatabaseUtils.checkDatabaseEncoding
import org.ossreviewtoolkit.model.utils.DatabaseUtils.tableExists
import org.ossreviewtoolkit.model.utils.DatabaseUtils.transaction
import org.ossreviewtoolkit.scanner.storages.utils.jsonb

class PostgresPackageProvenanceStorage(
    /**
     * The JDBC data source to obtain database connections.
     */
    private val dataSource: Lazy<DataSource>,

    /**
     * The name of the table used for storing package provenances.
     */
    private val tableName: String = "package_provenances"
) : PackageProvenanceStorage {
    private val table = PackageProvenances(tableName)

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

    override fun readProvenance(id: Identifier, sourceArtifact: RemoteArtifact): PackageProvenanceResolutionResult? =
        database.transaction {
            table.select {
                table.identifier eq id.toCoordinates() and
                        (table.artifactUrl eq sourceArtifact.url) and
                        (table.artifactHash eq sourceArtifact.hash.value)
            }.map { it[table.result] }.firstOrNull()
        }

    override fun readProvenance(id: Identifier, vcs: VcsInfo): PackageProvenanceResolutionResult? =
        database.transaction {
            table.select {
                table.identifier eq id.toCoordinates() and
                        (table.vcsType eq vcs.type.toString()) and
                        (table.vcsUrl eq vcs.url) and
                        (table.vcsRevision eq vcs.revision) and
                        (table.vcsPath eq vcs.path)
            }.map { it[table.result] }.firstOrNull()
        }

    override fun putProvenance(
        id: Identifier,
        sourceArtifact: RemoteArtifact,
        result: PackageProvenanceResolutionResult
    ) {
        database.transaction {
            table.deleteWhere {
                table.identifier eq id.toCoordinates() and
                        (table.artifactUrl eq sourceArtifact.url) and
                        (table.artifactHash eq sourceArtifact.hash.value)
            }

            table.insert {
                it[identifier] = id.toCoordinates()
                it[artifactUrl] = sourceArtifact.url
                it[artifactHash] = sourceArtifact.hash.value
                it[table.result] = result
            }
        }
    }

    override fun putProvenance(id: Identifier, vcs: VcsInfo, result: PackageProvenanceResolutionResult) {
        database.transaction {
            table.deleteWhere {
                table.identifier eq id.toCoordinates() and
                        (table.vcsType eq vcs.type.toString()) and
                        (table.vcsUrl eq vcs.url) and
                        (table.vcsRevision eq vcs.revision) and
                        (table.vcsPath eq vcs.path)
            }

            table.insert {
                it[identifier] = id.toCoordinates()
                it[vcsType] = vcs.type.toString()
                it[vcsUrl] = vcs.url
                it[vcsRevision] = vcs.revision
                it[vcsPath] = vcs.path
                it[table.result] = result
            }
        }
    }
}

private class PackageProvenances(tableName: String) : IntIdTable(tableName) {
    val identifier = text("identifier").index()
    val artifactUrl = text("artifact_url").nullable()
    val artifactHash = text("artifact_hash").nullable()
    val vcsType = text("vcs_type").nullable()
    val vcsUrl = text("vcs_url").nullable()
    val vcsRevision = text("vcs_revision").nullable()
    val vcsPath = text("vcs_path").nullable()
    val result = jsonb("result", PackageProvenanceResolutionResult::class)

    init {
        // Indices to prevent duplicate entries.
        uniqueIndex(identifier, artifactUrl, artifactHash)
        uniqueIndex(identifier, vcsType, vcsUrl, vcsRevision, vcsPath)
    }
}
