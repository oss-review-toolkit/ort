/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

package org.ossreviewtoolkit.clients.fossid.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

@JsonIgnoreProperties(ignoreUnknown = true)
data class Scan(
    val id: Int,
    val created: String?,
    val updated: String?,

    val userId: Int?,
    val projectId: Int?,

    val name: String?,
    val code: String?,

    val description: String?,
    val comment: String?,

    @JsonDeserialize(using = IntBooleanDeserializer::class)
    val isArchived: Boolean?,

    /**
     * Used when the files are available on the FossID server prior to the api call creating the scan.
     * This property and [gitRepoUrl] are exclusive and should never be used at the same time.
     */
    val targetPath: String?,

    @JsonDeserialize(using = IntBooleanDeserializer::class)
    val isBlindAudit: Boolean?,

    val filesNotScanned: String?,

    val pendingItems: String?,

    val isFromReport: String?,

    val gitRepoUrl: String?,

    val gitBranch: String?,

    val importedMetadata: String?,

    val hasFileExtension: Int?,

    val jarExtraction: String?,

    val anyArchivesExpanded: String?,

    val uploadedFiles: String?
)
