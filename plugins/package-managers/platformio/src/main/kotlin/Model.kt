/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.platformio

import java.io.StringReader
import java.util.Properties

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

import org.ossreviewtoolkit.analyzer.parseAuthorString

internal val JSON = Json { ignoreUnknownKeys = true }

/**
 * The relevant parts of a PlatformIO library manifest, see
 * https://docs.platformio.org/en/latest/manifests/library-json/index.html.
 *
 * "authors" and "dependencies" are not modeled here, as both can take multiple shapes depending on
 * the manifest format (a "library.json" file, or a legacy Arduino "library.properties" file).
 * They are parsed separately and returned alongside a [LibraryManifest] as part of a [ParsedLibrary].
 */
@Serializable
internal data class LibraryManifest(
    val name: String,
    val version: String? = null,
    val description: String? = null,
    val homepage: String? = null,
    val license: String? = null,
    val repository: Repository? = null
) {
    @Serializable
    internal data class Repository(val type: String? = null, val url: String? = null)
}

/**
 * The result of parsing a library's manifest file, combining the common [manifest] fields with
 * the [authors] and [dependencies] which are parsed separately due to their varying shapes.
 */
internal data class ParsedLibrary(
    val manifest: LibraryManifest,
    val authors: Set<String>,
    val dependencies: List<LibraryDependency>
)

/**
 * A single entry of a library's dependencies, referencing another library by [name] and optionally by [owner].
 */
internal data class LibraryDependency(val owner: String?, val name: String)

/**
 * The relevant parts of a PlatformIO Package Manifest ("*.piopm" file) that PlatformIO writes next to each package it
 * installs, see https://docs.platformio.org/en/latest/core/userguide/pkg/cmd_list.html.
 */
@Serializable
internal data class PackageMetadata(
    val type: String,
    val name: String,
    val version: String,
    val spec: Spec = Spec()
) {
    @Serializable
    internal data class Spec(val owner: String? = null)
}

internal fun parsePackageMetadata(json: String): PackageMetadata = JSON.decodeFromString(json)

/**
 * Parse the given [json] text of a "platform.json" or "package.json" manifest file (as written for platforms,
 * frameworks, toolchains, and tools) into a [LibraryManifest]. These manifests share the same well-known fields as a
 * "library.json" file, but have no "dependencies" field of their own.
 */
internal fun parsePackageManifest(json: String): LibraryManifest = JSON.decodeFromString(json)

/**
 * Parse the given [json] text of a "library.json" manifest file into a [ParsedLibrary].
 */
internal fun parseLibraryManifest(json: String): ParsedLibrary {
    val root = JSON.parseToJsonElement(json).jsonObject
    val manifest = JSON.decodeFromJsonElement<LibraryManifest>(root)

    return ParsedLibrary(
        manifest = manifest,
        authors = parseLibraryAuthors(root["authors"]),
        dependencies = parseLibraryDependencies(root["dependencies"])
    )
}

/**
 * Parse the given [text] of a legacy Arduino "library.properties" manifest file into a [ParsedLibrary].
 *
 * Many libraries in the Arduino ecosystem (usable from PlatformIO) still only ship this without a "library.json" file.
 *
 * See https://arduino.github.io/arduino-cli/latest/library-specification/#libraryproperties-file-format.
 */
internal fun parseLibraryProperties(text: String): ParsedLibrary {
    val properties = Properties().apply { load(StringReader(text)) }

    fun property(key: String) = properties.getProperty(key)?.trim()?.takeUnless { it.isEmpty() }

    // The "url" field is documented to point to the library's homepage, but in practice it almost always points to
    // its source repository, so use it for both.
    val url = property("url")

    val manifest = LibraryManifest(
        name = property("name").orEmpty(),
        version = property("version"),
        description = property("paragraph") ?: property("sentence"),
        homepage = url,
        license = property("license"),
        repository = url?.let { LibraryManifest.Repository(type = "git", url = it) }
    )

    val authors = parseAuthorString(property("author")).mapNotNullTo(mutableSetOf()) { it.name }

    // The "depends" field is a comma-separated list of library names, without any owner or version specification.
    val dependencies = property("depends")
        ?.split(',')
        ?.mapNotNull { it.trim().takeUnless { name -> name.isEmpty() } }
        ?.map { LibraryDependency(owner = null, name = it) }
        .orEmpty()

    return ParsedLibrary(manifest, authors, dependencies)
}

/**
 * Parse the "dependencies" field of a "library.json" manifest, which can either be:
 * - a JSON object mapping a dependency name (optionally prefixed with "owner/") to a version specification
 * - a JSON array of objects with "owner" and "name" fields (and possibly others which are ignored here)
 */
internal fun parseLibraryDependencies(element: JsonElement?): List<LibraryDependency> {
    when (element) {
        null -> return emptyList()

        is JsonObject -> return element.keys.map { it.toLibraryDependency() }

        is JsonArray -> return element.jsonArray.mapNotNull { item ->
            when (item) {
                is JsonObject -> {
                    val name = item["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val owner = item["owner"]?.jsonPrimitive?.contentOrNull
                    LibraryDependency(owner, name)
                }

                else -> item.jsonPrimitive.contentOrNull?.toLibraryDependency()
            }
        }

        else -> return emptyList()
    }
}

private fun String.toLibraryDependency(): LibraryDependency {
    val parts = split("/", limit = 2)
    return if (parts.size == 2) LibraryDependency(owner = parts[0], name = parts[1]) else LibraryDependency(null, this)
}

/**
 * Parse the "authors" field of a "library.json" manifest, which can either be an object or array.
 */
internal fun parseLibraryAuthors(element: JsonElement?): Set<String> {
    if (element == null) return emptySet()

    val items = if (element is JsonArray) element else JsonArray(listOf(element))

    return items.mapNotNullTo(mutableSetOf()) { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
}
