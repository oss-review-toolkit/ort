/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.fossologynomossa

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class NomossaResult(
    val results: List<NomossaFileResult>
)

@Serializable
internal data class NomossaFileResult(
    val file: String,
    val licenses: List<NomossaLicenseInfo>
)

@Serializable
internal data class NomossaLicenseInfo(
    val license: String,
    val start: Int,
    val end: Int,
    val len: Int
)

private val json = Json {
    ignoreUnknownKeys = true
}

/**
 * Parses the JSON result string returned by Nomossa into a [NomossaResult] object.
 */
internal fun parseNomossaResult(result: String): NomossaResult = json.decodeFromString(result)
