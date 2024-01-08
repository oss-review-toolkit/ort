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

package org.ossreviewtoolkit.model.utils

import java.io.InputStream

import javax.sql.DataSource

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils.createMissingTablesAndColumns
import org.jetbrains.exposed.sql.SchemaUtils.withDataBaseLock
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.utils.DatabaseUtils.checkDatabaseEncoding
import org.ossreviewtoolkit.model.utils.DatabaseUtils.tableExists
import org.ossreviewtoolkit.model.utils.DatabaseUtils.transaction

/**
 * A [DataSource]-based implementation of [ProvenanceFileStorage] that stores files associated by [KnownProvenance] in a
 * (Postgres) database.
 */
class PostgresProvenanceFileStorage(
    /**
     * The JDBC data source to obtain database connections.
     */
    dataSource: Lazy<DataSource>,

    /**
     * The name of the table to use for storing the contents of the associated files.
     */
    tableName: String
) : ProvenanceFileStorage {
    private val table = ProvenanceFileTable(tableName)

    /** Stores the database connection used by this object. */
    private val database by lazy {
        Database.connect(dataSource.value, databaseConfig = DatabaseConfig { defaultFetchSize = 1000 }).apply {
            transaction {
                withDataBaseLock {
                    if (!tableExists(table.tableName)) {
                        checkDatabaseEncoding()
                        createMissingTablesAndColumns(table)
                    }
                }

                commit()
            }
        }
    }

    override fun hasData(provenance: KnownProvenance): Boolean =
        database.transaction {
            table.select(table.provenance.count()).where {
                table.provenance eq provenance.storageKey()
            }.first()[table.provenance.count()].toInt()
        } == 1

    override fun putData(provenance: KnownProvenance, data: InputStream, size: Long) {
        database.transaction {
            table.deleteWhere {
                table.provenance eq provenance.storageKey()
            }

            table.insert { statement ->
                statement[this.provenance] = provenance.storageKey()
                statement[zipData] = data.use { it.readBytes() }
            }
        }
    }

    override fun getData(provenance: KnownProvenance): InputStream? {
        val bytes = database.transaction {
            table.selectAll().where {
                table.provenance eq provenance.storageKey()
            }.map {
                it[table.zipData]
            }.firstOrNull()
        } ?: return null

        return bytes.inputStream()
    }
}

private class ProvenanceFileTable(tableName: String) : IntIdTable(tableName) {
    val provenance: Column<String> = text("provenance").uniqueIndex()

    // TODO: Generalize the name of the 'zip_data' column for consistency.
    val zipData: Column<ByteArray> = binary("zip_data")
}

private fun KnownProvenance.storageKey(): String =
    when (this) {
        is ArtifactProvenance -> "source-artifact|${sourceArtifact.url}|${sourceArtifact.hash}"
        // The trailing "|" is kept for backward compatibility because there used to be an additional parameter.
        is RepositoryProvenance -> "vcs|${vcsInfo.type}|${vcsInfo.url}|$resolvedRevision|"
    }
