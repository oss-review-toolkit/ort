/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder

import com.sksamuel.hoplite.ConfigAlias

import org.ossreviewtoolkit.model.utils.FileArchiver
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.utils.ort.ORT_REPO_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.ort.storage.FileStorage
import org.ossreviewtoolkit.utils.spdx.SpdxConstants

/**
 * The configuration model of the scanner. This class is (de-)serialized in the following places:
 * - Deserialized from "config.yml" as part of [OrtConfiguration] (via Hoplite).
 * - (De-)Serialized as part of [org.ossreviewtoolkit.model.OrtResult] (via Jackson).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScannerConfiguration(
    /**
     * A flag to indicate whether packages that have a concluded license and authors set (to derive copyrights from)
     * should be skipped in the scan in favor of only using the declared information.
     */
    val skipConcluded: Boolean = false,

    /**
     * A flag to control whether excluded scopes and paths should be skipped during the scan.
     */
    val skipExcluded: Boolean = false,

    /**
     * A flag to indicate whether the scanner should add files without license to the scanner results.
     */
    val includeFilesWithoutFindings: Boolean = false,

    /**
     * Configuration of a [FileArchiver] that archives certain scanned files in an external [FileStorage].
     */
    val archive: FileArchiverConfiguration? = null,

    /**
     * Mappings from licenses returned by the scanner to valid SPDX licenses. Note that these mappings are only applied
     * in new scans, stored scan results are not affected.
     */
    @JsonPropertyOrder(alphabetic = true)
    val detectedLicenseMapping: Map<String, String> = mapOf(
        // https://scancode-licensedb.aboutcode.org/?search=generic
        "LicenseRef-scancode-agpl-generic-additional-terms" to SpdxConstants.NOASSERTION,
        "LicenseRef-scancode-generic-cla" to SpdxConstants.NOASSERTION,
        "LicenseRef-scancode-generic-exception" to SpdxConstants.NOASSERTION,
        "LicenseRef-scancode-generic-export-compliance" to SpdxConstants.NOASSERTION,
        "LicenseRef-scancode-generic-tos" to SpdxConstants.NOASSERTION,
        "LicenseRef-scancode-generic-trademark" to SpdxConstants.NOASSERTION,
        "LicenseRef-scancode-gpl-generic-additional-terms" to SpdxConstants.NOASSERTION,
        "LicenseRef-scancode-patent-disclaimer" to SpdxConstants.NOASSERTION,
        "LicenseRef-scancode-warranty-disclaimer" to SpdxConstants.NOASSERTION,

        // https://scancode-licensedb.aboutcode.org/?search=other
        "LicenseRef-scancode-other-copyleft" to SpdxConstants.NOASSERTION,
        "LicenseRef-scancode-other-permissive" to SpdxConstants.NOASSERTION,

        // https://scancode-licensedb.aboutcode.org/?search=unknown
        "LicenseRef-scancode-free-unknown" to SpdxConstants.NOASSERTION,
        "LicenseRef-scancode-unknown" to SpdxConstants.NOASSERTION,
        "LicenseRef-scancode-unknown-license-reference" to SpdxConstants.NOASSERTION,
        "LicenseRef-scancode-unknown-spdx" to SpdxConstants.NOASSERTION
    ),

    /**
     * The storage to store the file lists by provenance.
     */
    val fileListStorage: FileListStorageConfiguration? = null,

    /**
     * Scanner-specific configuration options. The key needs to match the name of the scanner class, e.g. "ScanCode"
     * for the ScanCode wrapper. See the documentation of the scanner for available options.
     */
    @ConfigAlias("config")
    @JsonAlias("config")
    val scanners: Map<String, PluginConfig>? = null,

    /**
     * A map with the configurations of the scan result storages available. Based on this information the actual
     * storages are created. Storages can be configured as readers or writers of scan results. Having this map
     * makes it possible for storage instances to act in both roles without having to duplicate configuration.
     */
    val storages: Map<String, ScanStorageConfiguration>? = null,

    /**
     * A list with the IDs of scan storages that are queried for existing scan results. The strings in this list
     * must match keys in the [storages] map.
     */
    val storageReaders: List<String>? = null,

    /**
     * A list with the IDs of scan storages that are called to persist scan results. The strings in this list
     * must match keys in the [storages] map.
     */
    val storageWriters: List<String>? = null,

    /**
     * A list of glob expressions that match file paths which are to be excluded from scan results.
     */
    val ignorePatterns: List<String> = listOf(
        "**/*$ORT_REPO_CONFIG_FILENAME",
        "**/*.spdx.yml",
        "**/*.spdx.yaml",
        "**/*.spdx.json",
        "**/META-INF/DEPENDENCIES",
        "**/META-INF/DEPENDENCIES.txt",
        "**/META-INF/NOTICE",
        "**/META-INF/NOTICE.txt"
    ),

    /**
     * Configuration of the storage for provenance information.
     */
    val provenanceStorage: ProvenanceStorageConfiguration? = null
)
