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

package org.ossreviewtoolkit.plugins.packagemanagers.composer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.serializer

@Serializable
internal data class Lockfile(
    val packages: List<PackageInfo> = emptyList(),
    @SerialName("packages-dev")
    val packagesDev: List<PackageInfo> = emptyList()
)

@Serializable
internal data class PackageInfo(
    val name: String? = null,
    // See https://getcomposer.org/doc/04-schema.md#version.
    val version: String? = null,
    val homepage: String? = null,
    val description: String? = null,
    val source: Source? = null,
    val authors: List<Author> = emptyList(),
    @Serializable(LicenseListSerializer::class)
    val license: List<String> = emptyList(),
    val provide: Map<String, String> = emptyMap(),
    val replace: Map<String, String> = emptyMap(),
    val require: Map<String, String> = emptyMap(),
    @SerialName("require-dev")
    val requireDev: Map<String, String> = emptyMap(),
    val dist: Dist? = null
) {
    @Serializable
    data class Author(
        val name: String? = null,
        val email: String? = null,
        val homepage: String? = null,
        val role: String? = null
    )

    @Serializable
    data class Source(
        val type: String? = null,
        val url: String? = null,
        val reference: String? = null
    )

    @Serializable
    data class Dist(
        val type: String? = null,
        val url: String? = null,
        val reference: String? = null,
        val shasum: String? = null
    )
}

private val JSON = Json { ignoreUnknownKeys = true }

private object LicenseListSerializer : JsonTransformingSerializer<List<String>>(serializer<List<String>>()) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element !is JsonArray) JsonArray(listOf(element)) else element
}

internal fun parseLockfile(json: String): Lockfile = JSON.decodeFromString<Lockfile>(json)

internal fun parsePackageInfo(json: String): PackageInfo = JSON.decodeFromString<PackageInfo>(json)
