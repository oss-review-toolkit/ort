/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.node.pnpm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val JSON = Json { ignoreUnknownKeys = true }

internal fun parsePnpmList(json: String): List<ModuleInfo> = JSON.decodeFromString(json)

@Serializable
internal data class ModuleInfo(
    val name: String,
    val version: String,
    val path: String,
    val private: Boolean,
    val dependencies: Map<String, Dependency> = emptyMap(),
    val devDependencies: Map<String, Dependency> = emptyMap(),
    val optionalDependencies: Map<String, Dependency> = emptyMap()
) {
    @Serializable
    data class Dependency(
        val from: String,
        val version: String,
        val resolved: String? = null,
        val path: String,
        val dependencies: Map<String, Dependency> = emptyMap(),
        val optionalDependencies: Map<String, Dependency> = emptyMap()
    )
}
