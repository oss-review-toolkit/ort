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

package org.ossreviewtoolkit.plugins.packagemanagers.bazel

import java.io.File

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

/**
 * The data model for MODULE.bazel.lock.
 */
@Serializable
internal data class Lockfile(
    val flags: Flags? = null
) {
    // TODO Support multiple registries.
    fun registryUrl(): String? = flags?.cmdRegistries?.getOrElse(0) { null }
}

@Serializable
internal data class Flags(
    val cmdRegistries: List<String>
)

internal fun parseLockfile(lockfile: File): Lockfile = json.decodeFromString<Lockfile>(lockfile.readText())

/**
 * The data model for the output of "bazel mod graph --output json".
 */
@Serializable
internal data class ModuleGraphNode(
    val key: String,
    val name: String? = null,
    val version: String? = null,
    val dependencies: List<ModuleGraphNode> = emptyList()
)
