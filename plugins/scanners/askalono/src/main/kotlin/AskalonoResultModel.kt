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

package org.ossreviewtoolkit.plugins.scanners.askalono

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

// See https://github.com/jpeddicord/askalono/blob/0.5.0/cli/src/formats.rs#L12-L23.
@Serializable
data class AskalonoResult(
    val path: String,
    val result: PathResult? = null,
    val error: String? = null
)

// See https://github.com/jpeddicord/askalono/blob/0.5.0/cli/src/formats.rs#L25-L30.
@Serializable
@JsonIgnoreUnknownKeys
data class PathResult(
    val score: Float,
    val license: LicenseResult
)

// See https://github.com/jpeddicord/askalono/blob/0.5.0/cli/src/formats.rs#L32-L37.
@Serializable
@JsonIgnoreUnknownKeys
data class LicenseResult(
    val name: String,
    val aliases: Set<String>
)
