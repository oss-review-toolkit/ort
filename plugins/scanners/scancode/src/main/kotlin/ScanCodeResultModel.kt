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

package org.ossreviewtoolkit.plugins.scanners.scancode

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonTransformingSerializer

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
    val options: Options,
    val startTimestamp: String,
    val endTimestamp: String,
    val outputFormatVersion: String? = null // This might be missing in JSON.
)

@Serializable
data class Options(
    @Serializable(InputListSerializer::class)
    val input: List<String>
)

sealed interface FileEntry {
    val path: String
    val type: String
    val licenses: List<LicenseEntry>
    val copyrights: List<CopyrightEntry>
    val scanErrors: List<String>

    @Serializable
    data class Version1(
        override val path: String,
        override val type: String,
        override val licenses: List<LicenseEntry>,
        override val copyrights: List<CopyrightEntry>,
        override val scanErrors: List<String>
    ) : FileEntry

    @Serializable
    data class Version3(
        override val path: String,
        override val type: String,
        val licenseDetections: List<LicenseDetection>,
        override val copyrights: List<CopyrightEntry>,
        override val scanErrors: List<String>
    ) : FileEntry {
        override val licenses = licenseDetections.flatMap { it.matches }
    }
}

@Serializable
data class LicenseDetection(
    val matches: List<LicenseEntry>
)

sealed interface LicenseEntry {
    val licenseExpression: String
    val startLine: Int
    val endLine: Int
    val score: Float

    @Serializable
    data class Version1(
        val key: String,
        override val score: Float,
        val spdxLicenseKey: String? = null, // This might be explicitly set to null in JSON.
        override val startLine: Int,
        override val endLine: Int,
        val matchedRule: LicenseRule
    ) : LicenseEntry {
        override val licenseExpression = matchedRule.licenseExpression
    }

    @Serializable
    data class Version3(
        override val score: Float,
        override val startLine: Int,
        override val endLine: Int,
        override val licenseExpression: String
    ) : LicenseEntry
}

@Serializable
data class LicenseRule(
    val licenseExpression: String
)

sealed interface CopyrightEntry {
    val statement: String
    val startLine: Int
    val endLine: Int

    @Serializable
    data class Version1(
        val value: String,
        override val startLine: Int,
        override val endLine: Int
    ) : CopyrightEntry {
        override val statement = value
    }

    @Serializable
    data class Version2(
        val copyright: String,
        override val startLine: Int,
        override val endLine: Int
    ) : CopyrightEntry {
        override val statement = copyright
    }
}

@Serializable
data class LicenseReference(
    val key: String,
    val spdxLicenseKey: String
)

/**
 * A serializer that wraps an old primitive input option from ScanCode 3 into an array like it is for recent ScanCode
 * versions. Note that the input option format changed before the output format version property was introduced.
 */
private object InputListSerializer : JsonTransformingSerializer<List<String>>(ListSerializer(String.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element !is JsonArray) JsonArray(listOf(element)) else element
}
