/*
 * Copyright (C) 2021 HERE Europe B.V.
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

package org.ossreviewtoolkit.model.utils

import java.io.File
import java.io.IOException

import javax.sql.DataSource

import kotlin.io.path.createTempFile

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils.createMissingTablesAndColumns
import org.jetbrains.exposed.sql.SchemaUtils.withDataBaseLock
import org.jetbrains.exposed.sql.transactions.transaction

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.utils.DatabaseUtils.checkDatabaseEncoding
import org.ossreviewtoolkit.model.utils.DatabaseUtils.tableExists
import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.log

/**
 * A PostgreSQL based storage for archive files.
 */
class PostgresFileArchiverStorage(
    /**
     * The JDBC data source to obtain database connections.
     */
    dataSource: DataSource
) : FileArchiverStorage {
    init {
        Database.connect(dataSource).apply { defaultFetchSize(1000) }

        transaction {
            withDataBaseLock {
                if (!tableExists(FileArchiveTable.tableName)) {
                    checkDatabaseEncoding()
                    createMissingTablesAndColumns(FileArchiveTable)
                }
            }

            commit()
        }
    }

    override fun hasArchive(provenance: KnownProvenance): Boolean =
        transaction {
            queryFileArchive(provenance)
        } != null

    override fun addArchive(provenance: KnownProvenance, zipFile: File) =
        transaction {
            if (queryFileArchive(provenance) == null) {
                try {
                    FileArchive.new {
                        this.provenance = provenance.storageKey()
                        this.zipData = zipFile.readBytes()
                    }
                } catch (e: ExposedSQLException) {
                    // The exception can happen when an archive with the same provenance has been inserted in parallel.
                    // That race condition is possible because [java.sql.Connection.TRANSACTION_READ_COMMITTED] is used
                    // as transaction isolation level (by default).
                    log.warn(e) { "Could not insert archive for '${provenance.storageKey()}'." }
                }
            }
        }

    override fun getArchive(provenance: KnownProvenance): File? {
        val fileArchive = transaction {
            queryFileArchive(provenance)
        } ?: return null

        val file = createTempFile(ORT_NAME, ".zip").toFile()

        try {
            file.writeBytes(fileArchive.zipData)
        } catch (e: IOException) {
            file.delete()
            throw e
        }

        return file
    }
}

private object FileArchiveTable : IntIdTable("file_archives") {
    val provenance: Column<String> = text("provenance").uniqueIndex()
    val zipData: Column<ByteArray> = binary("zip_data")
}

internal class FileArchive(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<FileArchive>(FileArchiveTable)

    var provenance: String by FileArchiveTable.provenance
    var zipData: ByteArray by FileArchiveTable.zipData
}

private fun KnownProvenance.storageKey(): String =
    when (this) {
        is ArtifactProvenance -> "source-artifact|${sourceArtifact.url}|${sourceArtifact.hash}"
        is RepositoryProvenance -> {
            // The content on the archives does not depend on the VCS path in general, thus that path must not be part
            // of the storage key. However, for Git-Repo that path must be part of the storage key because it denotes
            // the Git-Repo manifest location rather than the path to be (sparse) checked out.
            val path = vcsInfo.path.takeIf { vcsInfo.type == VcsType.GIT_REPO }.orEmpty()
            "vcs|${vcsInfo.type}|${vcsInfo.url}|${vcsInfo.resolvedRevision}|$path"
        }
    }

private fun queryFileArchive(provenance: KnownProvenance): FileArchive? =
    FileArchive.find { FileArchiveTable.provenance eq provenance.storageKey() }.singleOrNull()
