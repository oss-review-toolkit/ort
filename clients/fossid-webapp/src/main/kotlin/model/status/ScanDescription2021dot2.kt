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

package org.ossreviewtoolkit.clients.fossid.model.status

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * A description of scan status. This class is for FossID version 2021.2.
 */
data class ScanDescription2021dot2(
    val id: Long?,
    val scanId: String,
    val scanName: String,
    val scanCode: String,

    val pid: String?,
    val type: ScanStatusType,

    override val status: ScanStatus,
    val state: String?,

    val isFinished: Boolean,

    val percentageDone: String?,

    val currentFile: String?,
    val currentFilename: String?,
    val ignoredFiles: String?,
    val failedFiles: String?,

    val totalFiles: String?,

    val currentStep: String?,
    val info: String?,
    val blindSource: String?,

    override val comment: String?,
    @JsonProperty("comment_2")
    val comment2: String?,
    @JsonProperty("comment_3")
    val comment3: String?,

    @JsonProperty("started")
    val startedAt: String?,
    @JsonProperty("finished")
    val finishedAt: String?
) : UnversionedScanDescription
