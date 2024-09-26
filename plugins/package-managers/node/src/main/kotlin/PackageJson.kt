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

package org.ossreviewtoolkit.plugins.packagemanagers.node

import java.io.File

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.decodeToSequence
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer

import org.ossreviewtoolkit.plugins.packagemanagers.node.PackageJson.Author
import org.ossreviewtoolkit.plugins.packagemanagers.node.PackageJson.Repository

private val JSON = Json { ignoreUnknownKeys = true }

internal fun parsePackageJson(file: File): PackageJson = parsePackageJson(file.readText())

internal fun parsePackageJson(json: String): PackageJson = parsePackageJson(JSON.parseToJsonElement(json))

internal fun parsePackageJsons(jsons: String): List<PackageJson> =
    jsons.byteInputStream().use { input ->
        JSON.decodeToSequence<JsonElement>(input).mapTo(mutableListOf()) { parsePackageJson(it) }
    }

internal fun parsePackageJson(element: JsonElement): PackageJson {
    val transformedElement = transformPackageJson(element)
    return JSON.decodeFromJsonElement<PackageJson>(transformedElement)
}

/**
 * Transform the root element of a 'package.json' by reducing various representations of the 'license' and 'licenses'
 * property to a single JSON array of strings. See also See https://docs.npmjs.com/files/package.json#license.
 *
 * Note: This function can be turned as-is into a JsonTransformingSerializer once this works with annotations on the
 * class.
 */
private fun transformPackageJson(element: JsonElement): JsonElement {
    val obj = element.jsonObject

    val licenses = listOfNotNull(
        obj["license"],
        obj["licenses"]
    ).flatMapTo(mutableSetOf()) { it.parseLicenses() }

    val entries = obj.entries.associateTo(mutableMapOf()) { it.toPair() }.apply {
        remove("license")
        put("licenses", JsonArray(licenses.map { JsonPrimitive(it) }))
    }

    return JsonObject(entries)
}

private fun JsonElement.parseLicenses(): Set<String> =
    when (this) {
        is JsonPrimitive -> setOf(jsonPrimitive.content)
        is JsonArray -> jsonArray.flatMapTo(mutableSetOf()) { it.parseLicenses() }
        is JsonObject -> jsonObject.getValue("type").parseLicenses()
        else -> throw SerializationException("Unexpected JSON element.")
    }

@Serializable
data class PackageJson(
    val name: String? = null,
    val version: String? = null,
    val homepage: String? = null,
    val description: String? = null,
    val licenses: List<String> = emptyList(),
    @Serializable(AuthorListSerializer::class)
    @SerialName("author")
    val authors: List<Author> = emptyList(),
    val gitHead: String? = null,
    @Serializable(RepositorySerializer::class)
    val repository: Repository? = null,
    @SerialName("_resolved")
    val resolved: String? = null,
    @SerialName("_from")
    val from: String? = null,
    @SerialName("_integrity")
    val integrity: String? = null,
    val packageManager: String? = null,
    val dependencies: Map<String, String> = emptyMap(),
    val devDependencies: Map<String, String> = emptyMap(),
    /** This property does not belong to package.json but to the JSON returned by 'npm info'. */
    val dist: Distribution? = null
) {
    @Serializable
    data class Author(
        val name: String,
        val email: String? = null,
        val url: String? = null
    )

    @Serializable
    data class Repository(
        val url: String,
        val type: String? = null,
        val directory: String? = null
    )

    @Serializable
    data class Distribution(
        val tarball: String? = null,
        val shasum: String? = null
    )
}

private object AuthorListSerializer : JsonTransformingSerializer<List<Author>>(serializer<List<Author>>()) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        // TODO: The string from the JSON primitive could be parsed into the dedicated author properties.
        when (element) {
            is JsonObject -> JsonArray(listOf(element))
            is JsonPrimitive -> JsonArray(listOf(element.transform()))
            is JsonArray -> JsonArray(element.map { it.transform() })
        }

    private fun JsonElement.transform(): JsonElement =
        takeUnless { this is JsonPrimitive } ?: JsonObject(mapOf("name" to jsonPrimitive))
}

private object RepositorySerializer : JsonTransformingSerializer<Repository>(serializer<Repository>()) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        (element as? JsonObject) ?: JsonObject(mapOf("url" to element.jsonPrimitive))
}
