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

import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.ort.storage.FileStorage
import org.ossreviewtoolkit.utils.ort.storage.HttpFileStorage
import org.ossreviewtoolkit.utils.ort.storage.LocalFileStorage
import org.ossreviewtoolkit.utils.ort.storage.XZCompressedLocalFileStorage

/**
 * The configuration model for a [FileStorage]. Only one of the storage options can be configured.
 */
data class FileStorageConfiguration(
    /**
     * The configuration of a [HttpFileStorage].
     */
    val httpFileStorage: HttpFileStorageConfiguration? = null,

    /**
     * The configuration of a [LocalFileStorage].
     */
    val localFileStorage: LocalFileStorageConfiguration? = null
) {
    /**
     * Create a [FileStorage] based on this configuration.
     */
    fun createFileStorage(): FileStorage {
        require((httpFileStorage == null) xor (localFileStorage == null)) {
            "Exactly one implementation must be configured for a FileStorage."
        }

        return httpFileStorage?.let { httpFileStorageConfiguration ->
            HttpFileStorage(
                httpFileStorageConfiguration.url,
                httpFileStorageConfiguration.query,
                httpFileStorageConfiguration.headers
            )
        } ?: localFileStorage!!.let { localFileStorageConfiguration ->
            val directory = localFileStorageConfiguration.directory.expandTilde()

            if (localFileStorageConfiguration.compression) {
                XZCompressedLocalFileStorage(directory)
            } else {
                LocalFileStorage(directory)
            }
        }
    }
}
