/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.cocoapods

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer

@Serializable
internal data class Podspec(
    val name: String = "",
    val version: String = "",
    @Serializable(LicenseSerializer::class)
    val license: String = "",
    val summary: String = "",
    val homepage: String = "",
    val source: Source? = null,
    private val subspecs: List<Podspec> = emptyList()
) {
    @Serializable
    data class Source(
        val git: String? = null,
        val tag: String? = null,
        val http: String? = null
    )

    fun withSubspecs(): List<Podspec> {
        val result = mutableListOf<Podspec>()

        fun add(spec: Podspec, namePrefix: String) {
            val name = "$namePrefix${spec.name}"
            result += copy(name = "$namePrefix${spec.name}")
            spec.subspecs.forEach { add(it, "$name/") }
        }

        add(this, "")

        return result
    }
}

private val JSON = Json { ignoreUnknownKeys = true }

internal fun String.parsePodspec(): Podspec = JSON.decodeFromString<Podspec>(this)

/**
 * Handle deserialization of the following two possible representations:
 *
 * 1. https://github.com/CocoaPods/Specs/blob/f75c24e7e9df1dac6ffa410a6fb30f01e026d4d6/Specs/8/5/e/SocketIOKit/2.0.1/SocketIOKit.podspec.json#L6-L9
 * 2. https://github.com/CocoaPods/Specs/blob/f75c24e7e9df1dac6ffa410a6fb30f01e026d4d6/Specs/8/5/e/FirebaseObjects/0.0.1/FirebaseObjects.podspec.json#L6
 */
private object LicenseSerializer : JsonTransformingSerializer<String>(serializer<String>()) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonObject) {
            element["type"]?.jsonPrimitive ?: JsonPrimitive("")
        } else {
            element
        }
}
