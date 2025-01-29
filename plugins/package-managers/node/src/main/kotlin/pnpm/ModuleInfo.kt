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

import java.io.ByteArrayInputStream

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeToSequence

private val JSON = Json { ignoreUnknownKeys = true }

/**
 * Parse the given [json] output of a PNPM list command. Normally, the resulting [Sequence] contains only a single
 * [List] with the [ModuleInfo] objects of the project. If there are nested projects, PNPM outputs multiple arrays,
 * which leads to syntactically invalid JSON. This is handled by this function by returning a [Sequence] with a
 * corresponding number of elements. In this case, callers are responsible for correctly mapping the elements to
 * projects.
 */
internal fun parsePnpmList(json: String): Sequence<List<ModuleInfo>> =
    JSON.decodeToSequence<List<ModuleInfo>>(
        ByteArrayInputStream(json.toByteArray()),
        DecodeSequenceMode.WHITESPACE_SEPARATED
    )

@Serializable
data class ModuleInfo(
    val name: String? = null,
    val version: String? = null,
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
