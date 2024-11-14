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

import java.io.File

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseWithExceptionExpression
import org.ossreviewtoolkit.utils.spdx.toSpdx
import org.ossreviewtoolkit.utils.spdx.toSpdxId

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
    val outputFormatVersion: String? = null // This might be missing in JSON.
) {
    fun getInput(): File {
        val inputPath = when (val input = options.getValue("input")) {
            is JsonPrimitive -> input.content
            is JsonArray -> input.first().jsonPrimitive.content
            else -> throw SerializationException("Unknown input elememt type.")
        }

        return File(inputPath)
    }

    fun getPrimitiveOptions(): List<Pair<String, String>> =
        options.mapNotNull { entry ->
            (entry.value as? JsonPrimitive)?.let { entry.key to it.content }
        }
}

sealed interface FileEntry {
    val path: String
    val type: String
    val licenses: List<LicenseEntry>
    val copyrights: List<CopyrightEntry>
    val scanErrors: List<String>

    // A map of ScanCode license keys associated with their corresponding SPDX license ID.
    val scanCodeKeyToSpdxIdMappings: List<Pair<String, String>>

    @Serializable
    data class Version1(
        override val path: String,
        override val type: String,
        override val licenses: List<LicenseEntry.Version1>,
        override val copyrights: List<CopyrightEntry>,
        override val scanErrors: List<String>
    ) : FileEntry {
        companion object {
            private val LICENSE_REF_PREFIX_SCAN_CODE = SpdxConstants.LICENSE_REF_PREFIX +
                "${ScanCode.SCANNER_NAME.lowercase()}-"

            private fun getSpdxId(spdxLicenseKey: String?, key: String): String {
                // There is a bug in ScanCode 3.0.2 that returns an empty string instead of null for licenses unknown to
                // SPDX.
                val spdxId = spdxLicenseKey.orEmpty().toSpdxId(allowPlusSuffix = true)

                if (spdxId.isNotEmpty()) return spdxId

                // Fall back to building an ID based on the ScanCode-specific "key".
                return "$LICENSE_REF_PREFIX_SCAN_CODE${key.toSpdxId(allowPlusSuffix = true)}"
            }
        }

        override val scanCodeKeyToSpdxIdMappings by lazy {
            licenses.map { license ->
                license.key to getSpdxId(license.spdxLicenseKey, license.key)
            }
        }
    }

    @Serializable
    data class Version3(
        override val path: String,
        override val type: String,
        val detectedLicenseExpression: String? = null, // This might be explicitly set to null in JSON.
        val detectedLicenseExpressionSpdx: String? = null, // This might be explicitly set to null in JSON.
        val licenseDetections: List<LicenseDetection>,
        override val copyrights: List<CopyrightEntry>,
        override val scanErrors: List<String>
    ) : FileEntry {
        companion object {
            private fun SpdxExpression?.licensesAndExceptions(): List<String> =
                this?.decompose().orEmpty().flatMap {
                    when (it) {
                        is SpdxLicenseWithExceptionExpression -> listOf(it.license.toString(), it.exception)
                        else -> listOf(it.toString())
                    }
                }
        }

        override val licenses = licenseDetections.flatMap { it.matches }

        override val scanCodeKeyToSpdxIdMappings by lazy {
            val keyNames = detectedLicenseExpression?.toSpdx().licensesAndExceptions()
            val spdxNames = detectedLicenseExpressionSpdx?.toSpdx().licensesAndExceptions()

            check(keyNames.size == spdxNames.size)

            keyNames.zip(spdxNames)
        }
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
    val matchedText: String?

    @Serializable
    data class Version1(
        val key: String,
        override val score: Float,
        val spdxLicenseKey: String? = null, // This might be explicitly set to null in JSON.
        override val startLine: Int,
        override val endLine: Int,
        val matchedRule: LicenseRule,
        override val matchedText: String? = null
    ) : LicenseEntry {
        override val licenseExpression = matchedRule.licenseExpression
    }

    @Serializable
    data class Version3(
        override val score: Float,
        override val startLine: Int,
        override val endLine: Int,
        override val licenseExpression: String,
        val spdxLicenseExpression: String? = null, // This might be missing in JSON.
        val fromFile: String? = null, // This might be missing in JSON.
        override val matchedText: String? = null
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
