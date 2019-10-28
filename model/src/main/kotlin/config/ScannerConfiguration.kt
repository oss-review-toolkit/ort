/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

import com.here.ort.utils.storage.FileArchiver
import com.here.ort.utils.storage.FileStorage

/**
 * The configuration model of the scanner.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ScannerConfiguration(
    /**
     * Configuration of a [FileArchiver] that archives certain scanned files in an external [FileStorage].
     */
    val archive: FileArchiverConfiguration? = null,

    /**
     * Configuration of a file based storage.
     */
    val fileBasedStorage: FileBasedStorageConfiguration? = null,

    /**
     * Configuration of the PostgreSQL scan results storage.
     */
    val postgresStorage: PostgresStorageConfiguration? = null,

    /**
     * Scanner specific configuration options. The key needs to match the name of the scanner class, e.g. "ScanCode"
     * for the ScanCode wrapper. See the documentation of the scanner for available options.
     */
    val scanner: Map<String, Map<String, String>>? = null
)
