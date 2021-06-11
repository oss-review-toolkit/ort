/*
 * Copyright (C) 2019 HERE Europe B.V.
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

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

import com.sksamuel.hoplite.ConfigAlias

import org.ossreviewtoolkit.model.utils.DatabaseUtils
import org.ossreviewtoolkit.model.utils.FileArchiver
import org.ossreviewtoolkit.model.utils.FileArchiverFileStorage
import org.ossreviewtoolkit.model.utils.PostgresFileArchiverStorage
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.storage.FileStorage
import org.ossreviewtoolkit.utils.storage.LocalFileStorage

/**
 * The configuration model for a [FileArchiver].
 */
@JsonIgnoreProperties(value = ["patterns"])
data class FileArchiverConfiguration(
    /**
     * Configuration of the [FileStorage] used for archiving the files.
     */
    @ConfigAlias("storage")
    @JsonAlias("storage")
    val fileStorage: FileStorageConfiguration? = null,

    /**
     * Configuration of the [PostgresFileArchiverStorage] used for archiving the files.
     */
    val postgresStorage: PostgresStorageConfiguration? = null
) {
    init {
        if (fileStorage != null && postgresStorage != null) {
            log.warn {
                "'fileStorage' and 'postgresStorage' are both configured but only one storage can be used. Using " +
                        "'fileStorage'."
            }
        }
    }
}

/**
 * Create a [FileArchiver] based on this configuration.
 */
fun FileArchiverConfiguration?.createFileArchiver(): FileArchiver {
    val storage = when {
        this?.fileStorage != null -> FileArchiverFileStorage(fileStorage.createFileStorage())

        this?.postgresStorage != null -> {
            val dataSource = DatabaseUtils.createHikariDataSource(
                config = postgresStorage,
                applicationNameSuffix = "file-archiver"
            )

            PostgresFileArchiverStorage(dataSource)
        }

        else -> FileArchiverFileStorage(LocalFileStorage(FileArchiver.DEFAULT_ARCHIVE_DIR))
    }

    val patterns = LicenseFilenamePatterns.getInstance().allLicenseFilenames

    return FileArchiver(patterns, storage)
}
