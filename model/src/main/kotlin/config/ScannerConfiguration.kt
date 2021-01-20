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

package org.ossreviewtoolkit.model.config

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

import org.ossreviewtoolkit.utils.storage.FileArchiver
import org.ossreviewtoolkit.utils.storage.FileStorage

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
     * Scanner specific configuration options. The key needs to match the name of the scanner class, e.g. "ScanCode"
     * for the ScanCode wrapper.
     */
    @JsonAlias("scanner")
    val options: Map<String, ScannerOptions>? = null,

    /**
     * A map with the configurations of the scan result storages available. Based on this information the actual
     * storages are created. Storages can be configured as readers or writers of scan results. Having this map
     * makes it possible for storage instances to act in both roles without having to duplicate configuration.
     */
    val storages: Map<String, ScanStorageConfiguration>? = null,

    /**
     * A list with the IDs of scan storages that are queried for existing scan results. The strings in this list
     * must match keys in the storages map.
     */
    val storageReaders: List<String>? = null,

    /**
     * A list with the IDs of scan storages that are called to persist scan results. The strings in this list
     * must match keys in the storages map.
     */
    val storageWriters: List<String>? = null,

    /**
     * A list of glob expressions that match file paths which are to be excluded from scan results.
     */
    val ignorePatterns: List<String> = listOf(
        "**/*.ort.yml",
        "**/META-INF/DEPENDENCIES"
    )
)

/**
 * A class defining scanner-specific option.
 *
 * In the global [ScannerConfiguration], the _options_ map allows assigning an instance of this class to each
 * scanner. That way, this scanner can be configured in a special way. The options consist of a part that is common to
 * all scanners, and a generic map of properties to be evaluated by specific scanner implementations.
 */
data class ScannerOptions(
    /**
     * The configuration of the criteria when a scan result loaded from a results storage is considered compatible with
     * the current scanner version.
     */
    val compatibility: ScannerCompatibilityConfiguration? = null,

    /**
     * Scanner specific configuration options. See the documentation of the scanner for available options.
     */
    val properties: Map<String, String>? = null
)
