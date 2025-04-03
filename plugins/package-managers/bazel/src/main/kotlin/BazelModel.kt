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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

/**
 * The data model for MODULE.bazel.lock.
 */
@Serializable
internal data class Lockfile(
    // The flags containing the registry URLs (Bazel < 7.2.0).
    val flags: Flags? = null,

    // The registry URLs of the project's dependencies packages  (Bazel >= 7.2.0).
    val registryFileHashes: Map<String, String>? = null
) {
    init {
        require((flags == null) xor (registryFileHashes == null)) {
            "A lockfile must either set the 'flags' or the 'registryFileHashes' property."
        }
    }

    @Serializable
    data class Flags(
        val cmdRegistries: List<String>
    )

    /**
     * Return a list of URLs for the service registries defined for this project in case Bazel < 7.2.0 is used, or an
     * empty list otherwise.
     */
    fun registryUrls(): List<String> = flags?.cmdRegistries.orEmpty()
}

internal fun parseLockfile(lockfile: File) = json.decodeFromString<Lockfile>(lockfile.readText())

/**
 * The data model for the output of "bazel mod graph --output json --extension_info=usages".
 */
@Serializable
internal data class BazelModule(
    val key: String,
    val name: String? = null,
    val version: String? = null,
    val dependencies: List<BazelModule> = emptyList(),
    val extensionUsages: List<BazelExtension> = emptyList()
)

@Serializable
internal data class BazelExtension(
    val key: String,
    val unexpanded: Boolean,
    @SerialName("used_repos")
    val usedRepos: List<String> = emptyList(),
    @SerialName("unused_repos")
    val unusedRepos: List<String> = emptyList()
)

internal fun String.parseBazelModule() = json.decodeFromString<BazelModule>(this)
