/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

data class ScanDescription(
    val scanId: String,
    val scanName: String,
    val scanCode: String,

    val pid: String?,
    val type: ScanStatusType,

    override val status: ScanStatus,

    val isFinished: Int,

    val percentageDone: String,

    override val comment: String,
    @JsonProperty("comment_2")
    val comment2: String,
    @JsonProperty("comment_3")
    val comment3: String,

    @JsonProperty("started")
    val startedAt: String?,
    @JsonProperty("finished")
    val finishedAt: String?
) : UnversionedScanDescription
