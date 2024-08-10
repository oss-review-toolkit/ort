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

package org.ossreviewtoolkit.plugins.packagemanagers.bower

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.serializer

import org.ossreviewtoolkit.plugins.packagemanagers.bower.PackageMeta.Author

@Serializable
internal data class PackageInfo(
    val endpoint: Endpoint,
    val pkgMeta: PackageMeta,
    val dependencies: Map<String, PackageInfo> = emptyMap()
) {
    @Serializable
    data class Endpoint(
        val name: String,
        val source: String,
        val target: String
    )
}

/**
 * See https://github.com/bower/spec/blob/master/json.md.
 */
@Serializable
internal data class PackageMeta(
    val name: String? = null,
    @Serializable(AuthorListSerializer::class)
    val authors: List<Author> = emptyList(),
    val description: String? = null,
    val license: String? = null,
    val homepage: String? = null,
    val dependencies: Map<String, String> = emptyMap(),
    val devDependencies: Map<String, String> = emptyMap(),
    val version: String? = null,
    @SerialName("_resolution")
    val resolution: Resolution? = null,
    val repository: Repository? = null,
    @SerialName("_source")
    val source: String? = null
) {
    @Serializable
    data class Resolution(
        val type: String? = null,
        val tag: String? = null,
        val commit: String? = null
    )

    @Serializable
    data class Author(
        val name: String,
        val email: String? = null
    )

    @Serializable
    data class Repository(
        val type: String,
        val url: String
    )
}

private val JSON = Json { ignoreUnknownKeys = true }

internal fun parsePackageInfoJson(json: String): PackageInfo = JSON.decodeFromString<PackageInfo>(json)

/**
 * Parse information about the author. According to https://github.com/bower/spec/blob/master/json.md#authors,
 * there are two formats to specify the authors of a package (similar to NPM). The difference is that the
 * strings or objects are inside an array.
 *
 * Note: As of Kotlin 2.0.20 it will be supported to associate the serializer with a class annotation. So, the
 * serializer then can be simplified into a single item deserializer. See also
 * https://github.com/Kotlin/kotlinx.serialization/issues/1169#issuecomment-2083213759.
 */
private object AuthorListSerializer : JsonTransformingSerializer<List<Author>>(serializer<List<Author>>()) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        JsonArray(
            element.jsonArray.map { item ->
                item.takeIf { it is JsonObject } ?: JsonObject(mapOf("name" to item))
            }
        )
}
