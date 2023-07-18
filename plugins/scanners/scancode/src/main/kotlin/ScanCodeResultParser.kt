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

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.mapLicense
import org.ossreviewtoolkit.model.utils.associateLicensesWithExceptions
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.toSpdxId

import org.semver4j.Semver

const val MAX_SUPPORTED_OUTPUT_FORMAT_MAJOR_VERSION = 2

private val LICENSE_REF_PREFIX_SCAN_CODE = "${SpdxConstants.LICENSE_REF_PREFIX}${ScanCode.SCANNER_NAME.lowercase()}-"
private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss.n").withZone(ZoneId.of("UTC"))

fun parseResult(result: File) = parseResult(result.readText())

fun parseResult(result: String) = parseResult(Json.parseToJsonElement(result))

fun parseResult(result: JsonElement): ScanCodeResult {
    // As even the structure of the header itself may change with the output format version, first operate on raw JSON
    // elements to get the version, and then parse the JSON elements into the appropriate data classes.
    val header = result.jsonObject.getValue("headers").jsonArray.single().jsonObject
    val outputFormatVersion = header["output_format_version"]?.let { Semver(it.jsonPrimitive.content) }

    // Select the correct set of (de-)serializers bundled in a module for parsing the respective format version.
    val module = when (outputFormatVersion?.major) {
        null, 1 -> SerializersModule {
            polymorphicDefaultDeserializer(CopyrightEntry::class) { CopyrightEntry.Version1.serializer() }
        }

        else -> SerializersModule {
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

private data class LicenseMatch(
    val expression: String,
    val startLine: Int,
    val endLine: Int,
    val score: Float
)

private fun LicenseEntry.getSpdxId(): String {
    // There is a bug in ScanCode 3.0.2 that returns an empty string instead of null for licenses unknown to SPDX.
    val spdxId = spdxLicenseKey.orEmpty().toSpdxId(allowPlusSuffix = true)

    if (spdxId.isNotEmpty()) return spdxId

    // Fall back to building an ID based on the ScanCode-specific "key".
    return "$LICENSE_REF_PREFIX_SCAN_CODE${key.toSpdxId(allowPlusSuffix = true)}"
}

fun ScanCodeResult.toScanSummary(): ScanSummary {
    val licenseFindings = mutableSetOf<LicenseFinding>()
    val copyrightFindings = mutableSetOf<CopyrightFinding>()
    val issues = mutableListOf<Issue>()

    val header = headers.single()

    val outputFormatVersion = header.outputFormatVersion?.let { Semver(it) }
    if (outputFormatVersion != null && outputFormatVersion.major > MAX_SUPPORTED_OUTPUT_FORMAT_MAJOR_VERSION) {
        issues += ScanCode.createAndLogIssue(
            source = ScanCode.SCANNER_NAME,
            message = "The output format version $outputFormatVersion exceeds the supported major version " +
                    "$MAX_SUPPORTED_OUTPUT_FORMAT_MAJOR_VERSION. Results may be incomplete or incorrect.",
            severity = Severity.WARNING
        )
    }

    val filesOfTypeFile = files.filter { it.type == "file" }

    // Build a map of all ScanCode license keys in the result associated with their corresponding SPDX ID.
    val scanCodeKeyToSpdxIdMappings = mutableMapOf<String, String>()

    filesOfTypeFile.forEach { file ->
        file.licenses.forEach { license ->
            scanCodeKeyToSpdxIdMappings[license.key] = license.getSpdxId()
        }
    }

    filesOfTypeFile.forEach { file ->
        // ScanCode creates separate license entries for each license in an expression. Deduplicate these by grouping by
        // the same expression.
        val licenses = file.licenses.groupBy {
                LicenseMatch(it.matchedRule.licenseExpression, it.startLine, it.endLine, it.score)
            }.map {
                // Arbitrarily take the first of the duplicate license entries.
                it.value.first()
            }

        licenses.mapTo(licenseFindings) { license ->
            // ScanCode uses its own license keys as identifiers in license expressions.
            val spdxLicenseExpression = license.matchedRule.licenseExpression.mapLicense(scanCodeKeyToSpdxIdMappings)

            LicenseFinding(
                license = spdxLicenseExpression,
                location = TextLocation(
                    path = file.path,
                    startLine = license.startLine,
                    endLine = license.endLine
                ),
                score = license.score
            )
        }

        file.copyrights.mapTo(copyrightFindings) { copyright ->
            CopyrightFinding(
                statement = copyright.statement,
                location = TextLocation(
                    path = file.path,
                    startLine = copyright.startLine,
                    endLine = copyright.endLine
                )
            )
        }
    }

    return ScanSummary(
        startTime = TIMESTAMP_FORMATTER.parse(header.startTimestamp).query(Instant::from),
        endTime = TIMESTAMP_FORMATTER.parse(header.endTimestamp).query(Instant::from),
        licenseFindings = associateLicensesWithExceptions(licenseFindings),
        copyrightFindings = copyrightFindings,
        issues = issues + mapScanErrors(this)
    )
}
