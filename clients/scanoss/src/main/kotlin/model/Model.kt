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

@Serializable
data class Server(
    val version: String,
    @SerialName("kb_version")
    val kbVersion: KnowledgeBaseVersion
)

@Serializable
data class KnowledgeBaseVersion(
    val monthly: String,
    val daily: String
)

/**
 * The "licenses" section of the raw report.
 */
@Serializable
data class License(
    /** Name of the license */
    val name: String,

    /** Location where the license was mined from. */
    val source: Source,

    /** Are there patent hints for this license.*/
    @SerialName("patent_hints")
    @Serializable(with = BooleanSerializer::class)
    val patentHints: Boolean? = null,

    /** Is this considered a copyleft license or not. */
    @Serializable(with = BooleanSerializer::class)
    val copyleft: Boolean? = null,

    /** URL of the OSADL checklist for this license. */
    @SerialName("checklist_url")
    val checklistUrl: String? = null,

    @SerialName("incompatible_with")
    val incompatibleWith: String? = null,

    /** Date the OSADL data was last updated. */
    @SerialName("osadl_updated")
    val osadlUpdated: String? = null
)

/**
 * The "copyrights" section of the raw report.
 */
@Serializable
data class Copyright(
    /** The copyright found. */
    val name: String,

    /**
     * Location where the copyright was mined from. Reuse the [Source] enum for simplicity even if the
     * [Source.FILE_SPDX_TAG] value is not supported here.
     */
    val source: Source
)

@Serializable
enum class Source {
    /** A component level declaration was found in the component’s repository for the matched file. */
    @SerialName("component_declared")
    COMPONENT_DECLARED,

    /** Minr detected a license text in the file header. */
    @SerialName("file_header")
    FILE_HEADER,

    /** The matched file contains a SPDX-License-Identifier tag in its header. */
    @SerialName("file_spdx_tag")
    FILE_SPDX_TAG,

    /** Minr detected a license in the LICENSE file in the component of the matched file. */
    @SerialName("license_file")
    LICENSE_FILE,

    /** Scancode detected a license declaration in the matched file. */
    @SerialName("scancode")
    SCANCODE
}

/**
 * Type of identification for the scanned file.
 */
@Serializable
enum class IdentificationType {
    @SerialName("file")
    FILE,

    @SerialName("none")
    NONE,

    @SerialName("snippet")
    SNIPPET
}

/**
 * Status of the file match.
 */
@Serializable
enum class FileMatchStatus {
    @SerialName("identified")
    IDENTIFIED,

    @SerialName("pending")
    PENDING
}
