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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer

import org.ossreviewtoolkit.utils.common.textValueOrEmpty

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class Podspec(
    val name: String = "",
    val version: String = "",
    @JsonDeserialize(using = LicenseDeserializer::class)
    val license: String = "",
    val summary: String = "",
    val homepage: String = "",
    val source: Map<String, String> = emptyMap(),
    private val subspecs: List<Podspec> = emptyList()
) {
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

/**
 * Handle deserialization of the following two possible representations:
 *
 * 1. https://github.com/CocoaPods/Specs/blob/f75c24e7e9df1dac6ffa410a6fb30f01e026d4d6/Specs/8/5/e/SocketIOKit/2.0.1/SocketIOKit.podspec.json#L6-L9
 * 2. https://github.com/CocoaPods/Specs/blob/f75c24e7e9df1dac6ffa410a6fb30f01e026d4d6/Specs/8/5/e/FirebaseObjects/0.0.1/FirebaseObjects.podspec.json#L6
 */
private class LicenseDeserializer : StdDeserializer<String>(String::class.java) {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): String {
        val node = parser.codec.readTree<JsonNode>(parser)

        return if (node.isTextual) {
            node.textValue()
        } else {
            node["type"].textValueOrEmpty()
        }
    }
}
