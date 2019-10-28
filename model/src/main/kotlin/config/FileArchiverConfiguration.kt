/*
 * Copyright (C) 2019 HERE Europe B.V.
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

package com.here.ort.model.config

import com.here.ort.utils.storage.FileArchiver
import com.here.ort.utils.storage.FileStorage

/**
 * The configuration model for a [FileArchiver].
 */
data class FileArchiverConfiguration(
    /**
     * A list of glob patterns that define which files will be archived.
     */
    val patterns: List<String>,

    /**
     * Configuration of the [FileStorage] used for archiving the files.
     */
    val storage: FileStorageConfiguration
) {
    /**
     * Create a [FileArchiver] based on this configuration.
     */
    fun createFileArchiver() = FileArchiver(patterns, storage.createFileStorage())
}
