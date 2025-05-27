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

package org.ossreviewtoolkit.plugins.scanners.boyterlc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

// See https://github.com/boyter/lc/blob/7a7c5750857efde2f1d50b1c3b62c07943587421/parsers/structs.go#L20-L31.
@Serializable
@JsonIgnoreUnknownKeys
class BoyterLcResult(
    @SerialName("Directory") val directory: String,
    @SerialName("Filename") val filename: String,
    @SerialName("LicenseGuesses") val licenseGuesses: List<LicenseGuess>
)

// See https://github.com/boyter/lc/blob/v1.3.1/parsers/structs.go#L15-L18.
@Serializable
data class LicenseGuess(
    @SerialName("LicenseId") val licenseId: String,
    @SerialName("Percentage") val percentage: Float
)
