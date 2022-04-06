/*
 * Copyright (C) 2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.clients.scanoss.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response of the SCANOSS streaming API returned for each file that was requested to scan.
 */
@Serializable
data class ScanResponse(
    /** Type of identification for the scanned file. */
    val id: IdentificationType,

    /** Status of the file match. */
    val status: FileMatchStatus,

    /** Matching line numbers of a snippet in the source file. */
    val lines: String,

    /** Matching line numbers for the snippet in the open source file. */
    @SerialName("oss_lines")
    val ossLines: String,

    /** Percentage of the source file that matches the open source file. */
    val matched: String,

    /** List of possible Package URLs identifying the OSS component. */
    val purl: List<String>,

    /** Vendor for the matched OSS component */
    val vendor: String,

    /** Name for the matched OSS component. */
    val component: String,

    /** Earliest matched OSS component version. */
    val version: String,

    /** Latest matched OSS component version. */
    val latest: String,

    /** URL of the OSS component. */
    val url: String,

    /** Release date of the component. */
    @SerialName("release_date")
    val releaseDate: String,

    /** Name of the matching OSS file. */
    val file: String,

    /** MD5 hash of the project zip that was mined from. */
    @SerialName("url_hash")
    val urlHash: String,

    /** Unique hash for the matching OSS file stored in the KB. */
    @SerialName("file_hash")
    val fileHash: String,

    /** URL to download the matching OSS file from the KB. */
    @SerialName("file_url")
    val fileUrl: String,

    /** List of declared dependencies for the detected component. */
    val dependencies: List<Dependency> = emptyList(),

    /** List of licenses associated with the OSS component. */
    val licenses: List<License>,

    /** List of copyrights found in the OSS file. */
    val copyrights: List<Copyright> = emptyList(),

    /** List of known vulnerabilities associated with the OSS component. */
    val vulnerabilities: List<Vulnerability> = emptyList(),

    /** List of quality metrics associated with the OSS component. */
    val quality: List<Quality> = emptyList(),

    /** List of cryptographic algorithms found in the OSS component. */
    val cryptography: List<Cryptography> = emptyList(),

    /** Details about the SCANOSS server used to conduct the scan. */
    val server: Server
)
