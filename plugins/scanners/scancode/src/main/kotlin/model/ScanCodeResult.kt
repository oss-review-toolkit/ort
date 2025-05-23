/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.scancode.model

import java.io.File

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class ScanCodeResult(
    val headers: List<HeaderEntry>,
    val files: List<FileEntry>,
    val licenseReferences: List<LicenseReference>? = null // Available only with "--license-references".
)

@Serializable
data class HeaderEntry(
    val toolName: String,
    val toolVersion: String,
    val options: Map<String, JsonElement>,
    val startTimestamp: String,
    val endTimestamp: String,
    val outputFormatVersion: String
) {
    fun getInput(): File {
        val inputPath = when (val input = options.getValue("input")) {
            is JsonPrimitive -> input.content
            is JsonArray -> input.first().jsonPrimitive.content
            else -> throw SerializationException("Unknown input element type.")
        }

        return File(inputPath)
    }

    fun getPrimitiveOptions(): List<Pair<String, String>> =
        options.mapNotNull { entry ->
            (entry.value as? JsonPrimitive)?.let { entry.key to it.content }
        }
}

@Serializable
data class LicenseReference(
    val key: String,
    val spdxLicenseKey: String
)
