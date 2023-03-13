/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.clients.fossid.model.result

data class Snippet(
    val id: Int,
    val created: String,
    val scanId: Int,
    val scanFileId: Int,
    val fileId: Int,
    val matchType: MatchType,

    val reason: String?,
    val author: String,
    val artifact: String,
    val version: String,

    val artifactLicense: String?,
    val releaseDate: String?,
    val mirror: String?,

    val file: String,
    val fileLicense: String?,
    val url: String?,
    val hits: String,
    val size: Int?,

    val updated: String?,
    val cpe: String?,
    val score: String,
    val matchFileId: String,
    val classification: String?,
    val highlighting: String?
)
