/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
import org.ossreviewtoolkit.model.utils.FileProvenanceFileStorage
import org.ossreviewtoolkit.model.utils.PostgresProvenanceFileStorage
import org.ossreviewtoolkit.model.utils.ProvenanceFileStorage
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.ort.ortDataDirectory
import org.ossreviewtoolkit.utils.ort.storage.FileStorage
import org.ossreviewtoolkit.utils.ort.storage.XZCompressedLocalFileStorage

private const val TABLE_NAME = "provenance_file_lists"
private const val FILENAME = "file_list"

data class FileListStorageConfiguration(
    /**
     * Configuration of the [FileStorage] used for storing the file lists.
     */
    val fileStorage: FileStorageConfiguration? = null,

    /**
     * Configuration of the [PostgresProvenanceFileStorage] used for storing the file lists.
     */
    val postgresStorage: PostgresStorageConfiguration? = null
) {
    init {
        if (fileStorage != null && postgresStorage != null) {
            logger.warn {
                "'fileStorage' and 'postgresStorage' are both configured but only one storage can be used. " +
                    "Using 'fileStorage'."
            }
        }
    }
}

fun FileListStorageConfiguration?.createStorage(): ProvenanceFileStorage =
    when {
        this?.fileStorage != null -> FileProvenanceFileStorage(
            storage = fileStorage.createFileStorage(),
            filename = FILENAME
        )
        this?.postgresStorage != null -> PostgresProvenanceFileStorage(
            dataSource = DatabaseUtils.createHikariDataSource(
                config = postgresStorage.connection,
                applicationNameSuffix = "file-lists"
            ),
            tableName = TABLE_NAME
        )
        else -> FileProvenanceFileStorage(
            storage = XZCompressedLocalFileStorage(ortDataDirectory / "scanner" / "file-lists"),
            filename = FILENAME
        )
    }
