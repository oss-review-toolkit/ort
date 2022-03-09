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
    val source: String,

    /** Are there patent hints for this license.*/
    @SerialName("patent_hints")
    val patentHints: String? = null,

    /** Is this considered a copyleft license or not. */
    val copyleft: String? = null,

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
