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

import java.io.File
import java.io.IOException

import javax.sql.DataSource

import org.apache.logging.log4j.kotlin.Logging

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils.createMissingTablesAndColumns
import org.jetbrains.exposed.sql.SchemaUtils.withDataBaseLock
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.select

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.utils.DatabaseUtils.checkDatabaseEncoding
import org.ossreviewtoolkit.model.utils.DatabaseUtils.tableExists
import org.ossreviewtoolkit.model.utils.DatabaseUtils.transaction
import org.ossreviewtoolkit.utils.ort.createOrtTempFile

/**
 * A PostgreSQL based implementation of [FileArchiverFileStorage].
 */
class PostgresFileArchiverStorage(
    /**
     * The JDBC data source to obtain database connections.
     */
    dataSource: Lazy<DataSource>,

    /**
     * The name of the table used for storing package provenances.
     */
    tableName: String
) : FileArchiverStorage {
    private companion object : Logging

    private val table = FileArchiveTable(tableName)

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

    override fun hasArchive(provenance: KnownProvenance): Boolean =
        database.transaction {
            table.slice(table.provenance.count()).select {
                table.provenance eq provenance.storageKey()
            }.first()[table.provenance.count()].toInt()
        } == 1

    override fun addArchive(provenance: KnownProvenance, zipFile: File) {
        database.transaction {
            table.insertIgnore {
                it[this.provenance] = provenance.storageKey()
                it[zipData] = zipFile.readBytes()
            }
        }
    }

    override fun getArchive(provenance: KnownProvenance): File? {
        val bytes = database.transaction {
            table.select {
                table.provenance eq provenance.storageKey()
            }.map {
                it[table.zipData]
            }.firstOrNull()
        } ?: return null

        val file = createOrtTempFile(suffix = ".zip")

        try {
            file.writeBytes(bytes)
        } catch (e: IOException) {
            file.delete()
            throw e
        }

        return file
    }
}

private class FileArchiveTable(tableName: String) : IntIdTable(tableName) {
    val provenance: Column<String> = text("provenance").uniqueIndex()
    val zipData: Column<ByteArray> = binary("zip_data")
}

private fun KnownProvenance.storageKey(): String =
    when (this) {
        is ArtifactProvenance -> "source-artifact|${sourceArtifact.url}|${sourceArtifact.hash}"
        // The trailing "|" is kept for backward compatibility because there used to be an additional parameter.
        is RepositoryProvenance -> "vcs|${vcsInfo.type}|${vcsInfo.url}|$resolvedRevision|"
    }
