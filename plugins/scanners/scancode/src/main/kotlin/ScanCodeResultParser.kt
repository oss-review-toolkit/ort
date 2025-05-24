/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.scancode

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.modules.SerializersModule

import org.ossreviewtoolkit.plugins.scanners.scancode.model.CopyrightEntry
import org.ossreviewtoolkit.plugins.scanners.scancode.model.FileEntry
import org.ossreviewtoolkit.plugins.scanners.scancode.model.LicenseEntry
import org.ossreviewtoolkit.plugins.scanners.scancode.model.ScanCodeResult

import org.semver4j.Semver

fun parseResult(result: String) = parseResult(Json.parseToJsonElement(result))

private fun parseResult(result: JsonElement): ScanCodeResult {
    // As even the structure of the header itself may change with the output format version, first operate on raw JSON
    // elements to get the version, and then parse the JSON elements into the appropriate data classes.
    val header = result.jsonObject.getValue("headers").jsonArray.single().jsonObject

    val outputFormatVersionPrimitive = requireNotNull(header["output_format_version"] as? JsonPrimitive) {
        "ScanCode results that do not define an 'output_format_version' are not supported anymore."
    }

    val outputFormatVersion = Semver(outputFormatVersionPrimitive.content)

    // Select the correct set of (de-)serializers bundled in a module for parsing the respective format version.
    val module = when (outputFormatVersion.major) {
        1 -> SerializersModule {
            polymorphicDefaultDeserializer(FileEntry::class) { FileEntry.Version1.serializer() }
            polymorphicDefaultDeserializer(LicenseEntry::class) { LicenseEntry.Version1.serializer() }
            polymorphicDefaultDeserializer(CopyrightEntry::class) { CopyrightEntry.Version1.serializer() }
        }

        2 -> SerializersModule {
            polymorphicDefaultDeserializer(FileEntry::class) { FileEntry.Version1.serializer() }
            polymorphicDefaultDeserializer(LicenseEntry::class) { LicenseEntry.Version1.serializer() }
            polymorphicDefaultDeserializer(CopyrightEntry::class) { CopyrightEntry.Version2.serializer() }
        }

        3 -> SerializersModule {
            polymorphicDefaultDeserializer(FileEntry::class) { FileEntry.Version3.serializer() }
            polymorphicDefaultDeserializer(LicenseEntry::class) { LicenseEntry.Version3.serializer() }
            polymorphicDefaultDeserializer(CopyrightEntry::class) { CopyrightEntry.Version2.serializer() }
        }

        else -> SerializersModule {
            polymorphicDefaultDeserializer(FileEntry::class) { FileEntry.Version3.serializer() }
            polymorphicDefaultDeserializer(LicenseEntry::class) { LicenseEntry.Version4.serializer() }
            polymorphicDefaultDeserializer(CopyrightEntry::class) { CopyrightEntry.Version2.serializer() }
        }
    }

    val json = Json {
        ignoreUnknownKeys = true
        namingStrategy = JsonNamingStrategy.SnakeCase
        serializersModule = module
    }

    return json.decodeFromJsonElement(result)
}
