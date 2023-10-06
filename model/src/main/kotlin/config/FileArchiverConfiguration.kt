/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model.config

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.utils.DatabaseUtils
import org.ossreviewtoolkit.model.utils.FileArchiver
import org.ossreviewtoolkit.model.utils.FileProvenanceFileStorage
import org.ossreviewtoolkit.model.utils.PostgresProvenanceFileStorage
import org.ossreviewtoolkit.utils.ort.storage.FileStorage
import org.ossreviewtoolkit.utils.ort.storage.XZCompressedLocalFileStorage

/**
 * The configuration model for a [FileArchiver].
 */
data class FileArchiverConfiguration(
    /**
     * Toggle to enable or disable the file archiver functionality altogether.
     */
    val enabled: Boolean = true,

    /**
     * Configuration of the [FileStorage] used for archiving the files.
     */
    val fileStorage: FileStorageConfiguration? = null,

    /**
     * Configuration of the [PostgresProvenanceFileStorage] used for archiving the files.
     */
    val postgresStorage: PostgresStorageConfiguration? = null
) {
    companion object {
        const val ARCHIVE_FILENAME = "archive.zip"
        const val TABLE_NAME = "file_archives"
    }

    init {
        if (fileStorage != null && postgresStorage != null) {
            logger.warn {
                "'fileStorage' and 'postgresStorage' are both configured but only one storage can be used. Using " +
                    "'fileStorage'."
            }
        }
    }
}

/**
 * Create a [FileArchiver] based on this configuration.
 */
fun FileArchiverConfiguration?.createFileArchiver(): FileArchiver? {
    if (this?.enabled == false) return null

    val storage = when {
        this?.fileStorage != null -> FileProvenanceFileStorage(
            storage = fileStorage.createFileStorage(),
            filename = FileArchiverConfiguration.ARCHIVE_FILENAME
        )

        this?.postgresStorage != null -> {
            val dataSource = DatabaseUtils.createHikariDataSource(
                config = postgresStorage.connection,
                applicationNameSuffix = "file-archiver"
            )

            PostgresProvenanceFileStorage(dataSource, FileArchiverConfiguration.TABLE_NAME)
        }

        else -> FileProvenanceFileStorage(
            storage = XZCompressedLocalFileStorage(FileArchiver.DEFAULT_ARCHIVE_DIR),
            filename = FileArchiverConfiguration.ARCHIVE_FILENAME
        )
    }

    val patterns = LicenseFilePatterns.getInstance().allLicenseFilenames

    return FileArchiver(patterns, storage)
}
