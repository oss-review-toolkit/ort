/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2020 Bosch.IO GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

@file:Suppress("TooManyFunctions")

package org.ossreviewtoolkit.scanner.scanners.scancode

import com.fasterxml.jackson.databind.JsonNode

import java.io.File
import java.time.Instant
import java.util.regex.Pattern

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.EMPTY_JSON_NODE
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.spdx.SpdxConstants
import org.ossreviewtoolkit.spdx.SpdxConstants.LICENSE_REF_PREFIX
import org.ossreviewtoolkit.spdx.calculatePackageVerificationCode
import org.ossreviewtoolkit.utils.textValueOrEmpty

private data class LicenseExpression(
    val expression: String,
    val startLine: Int,
    val endLine: Int
)

private data class LicenseKeyReplacement(
    val scanCodeLicenseKey: String,
    val spdxExpression: String
)

private val LICENSE_REF_PREFIX_SCAN_CODE = "$LICENSE_REF_PREFIX${ScanCode.SCANNER_NAME.toLowerCase()}-"

// Note: The "(File: ...)" part in the patterns below is actually added by our own getRawResult() function.
private val UNKNOWN_ERROR_REGEX = Pattern.compile(
    "(ERROR: for scanner: (?<scanner>\\w+):\n)?" +
            "ERROR: Unknown error:\n.+\n(?<error>\\w+Error)(:|\n)(?<message>.*) \\(File: (?<file>.+)\\)",
    Pattern.DOTALL
)

private val TIMEOUT_ERROR_REGEX = Pattern.compile(
    "(ERROR: for scanner: (?<scanner>\\w+):\n)?" +
            "ERROR: Processing interrupted: timeout after (?<timeout>\\d+) seconds. \\(File: (?<file>.+)\\)"
)

private val UNKNOWN_LICENSE_KEYS = listOf(
    "free-unknown",
    "unknown",
    "unknown-license-reference"
)

/**
 * Parse a [resultsFile] from ScanCode to a JSON node, which can then be further processed.
 */
internal fun parseResultsFile(resultsFile: File): JsonNode =
    if (resultsFile.isFile && resultsFile.length() > 0L) {
        jsonMapper.readTree(resultsFile)
    } else {
        EMPTY_JSON_NODE
    }

/**
 * Generate a summary from the given raw ScanCode [result], using [startTime] and [endTime] metadata.
 * From the [scanPath] the package verification code is generated. If [parseExpressions] is true, license findings are
 * preferably parsed as license expressions.
 */
internal fun generateSummary(
    startTime: Instant,
    endTime: Instant,
    scanPath: File,
    result: JsonNode,
    parseExpressions: Boolean = true
) =
    generateSummary(
        startTime,
        endTime,
        calculatePackageVerificationCode(scanPath),
        result,
        parseExpressions
    )

/**
 * Generate a summary from the given raw ScanCode [result], using [startTime], [endTime], and [verificationCode]
 * metadata. This variant can be used if the result is not read from a local file. If [parseExpressions] is true,
 * license findings are preferably parsed as license expressions.
 */
internal fun generateSummary(
    startTime: Instant,
    endTime: Instant,
    verificationCode: String,
    result: JsonNode,
    parseExpressions: Boolean = true
) =
    ScanSummary(
        startTime = startTime,
        endTime = endTime,
        fileCount = getFileCount(result),
        packageVerificationCode = verificationCode,
        licenseFindings = getLicenseFindings(result, parseExpressions).toSortedSet(),
        copyrightFindings = getCopyrightFindings(result).toSortedSet(),
        issues = getIssues(result)
    )

/**
 * Generates an object with details about the ScanCode scanner that produced the given [result]. The
 * corresponding metadata from the result is evaluated.
 */
internal fun generateScannerDetails(result: JsonNode) =
    result["headers"]?.let { headers ->
        generateScannerDetailsFromNode(headers.first(), "options", "tool_version")
    } ?: generateScannerDetailsFromNode(result, "scancode_options", "scancode_version")

private fun getFileCount(result: JsonNode): Int {
    // ScanCode 2.9.8 and above nest the files count in an extra header.
    result["headers"]?.forEach { header ->
        header["extra_data"]?.get("files_count")?.let {
            return it.intValue()
        }
    }

    // ScanCode 2.9.7 and below contain the files count at the top level.
    return result["files_count"]?.intValue() ?: 0
}

/**
 * Get the license findings from the given [result]. If [parseExpressions] is true and license expressions are contained
 * in the result, these are preferred over separate license findings. Otherwise only separate license findings are
 * parsed.
 */
private fun getLicenseFindings(result: JsonNode, parseExpressions: Boolean): List<LicenseFinding> {
    val licenseFindings = mutableListOf<LicenseFinding>()

    val files = result["files"]?.asSequence().orEmpty()
    files.flatMapTo(licenseFindings) { file ->
        val licenses = file["licenses"]?.asSequence().orEmpty()

        licenses.groupBy(
            keySelector = {
                LicenseExpression(
                    // Older ScanCode versions do not produce the `license_expression` field.
                    // Just use the `key` field in this case.
                    it["matched_rule"]?.get("license_expression")?.textValue().takeIf { parseExpressions }
                        ?: it["key"].textValue(),
                    it["start_line"].intValue(),
                    it["end_line"].intValue()
                )
            },
            valueTransform = {
                LicenseKeyReplacement(it["key"].textValue(), getSpdxLicenseId(it))
            }
        ).map { (licenseExpression, replacements) ->
            val spdxLicenseExpression = replacements.fold(licenseExpression.expression) { expression, replacement ->
                expression.replace("\\b${replacement.scanCodeLicenseKey}\\b".toRegex(), replacement.spdxExpression)
            }

            LicenseFinding(
                license = spdxLicenseExpression,
                location = TextLocation(
                    path = file["path"].textValue(),
                    startLine = licenseExpression.startLine,
                    endLine = licenseExpression.endLine
                )
            )
        }
    }

    return licenseFindings
}

/**
 * Get the SPDX license id (or a fallback) for a license finding.
 */
private fun getSpdxLicenseId(license: JsonNode): String {
    // There is a bug in ScanCode 3.0.2 that returns an empty string instead of null for licenses unknown to SPDX.
    val id = license["spdx_license_key"].textValueOrEmpty()

    // For regular SPDX IDs, return early here.
    if (id.isNotEmpty() && !id.startsWith(LICENSE_REF_PREFIX)) return id

    // Before version 2.9.8, ScanCode used SPDX LicenseRefs that did not include the "scancode" namespace, like
    // "LicenseRef-Proprietary-HERE" instead of now "LicenseRef-scancode-here-proprietary", see
    // https://github.com/nexB/scancode-toolkit/blob/f94f716/src/licensedcode/data/licenses/here-proprietary.yml#L6-L8
    // But if the "scancode" namespace is present, return early here.
    if (id.startsWith(LICENSE_REF_PREFIX_SCAN_CODE)) return id

    // At this point the ID is either empty or a non-"scancode" SPDX LicenseRef that needs to be fixed up.
    val key = license["key"].textValue().replace('_', '-')
    return if (key in UNKNOWN_LICENSE_KEYS) {
        SpdxConstants.NOASSERTION
    } else {
        "$LICENSE_REF_PREFIX_SCAN_CODE$key"
    }
}

private fun getIssues(result: JsonNode): List<OrtIssue> =
    result["files"]?.flatMap { file ->
        val path = file["path"].textValue()
        file["scan_errors"].map {
            OrtIssue(
                source = ScanCode.SCANNER_NAME,
                message = "${it.textValue()} (File: $path)"
            )
        }
    }.orEmpty()

/**
 * Get the copyright findings from the given [result].
 */
private fun getCopyrightFindings(result: JsonNode): List<CopyrightFinding> {
    val copyrightFindings = mutableListOf<CopyrightFinding>()

    val files = result["files"]?.asSequence().orEmpty()
    files.flatMapTo(copyrightFindings) { file ->
        val path = file["path"].textValue()

        val copyrights = file["copyrights"]?.asSequence().orEmpty()
        copyrights.flatMap { copyright ->
            val startLine = copyright["start_line"].intValue()
            val endLine = copyright["end_line"].intValue()

            // While ScanCode 2.9.2 was still using "statements", version 2.9.7 is using "value".
            val statements = (copyright["statements"]?.asSequence() ?: sequenceOf(copyright["value"]))

            statements.map { statement ->
                CopyrightFinding(
                    statement = statement.textValue(),
                    location = TextLocation(
                        // The path is already relative as we run ScanCode with "--strip-root".
                        path = path,
                        startLine = startLine,
                        endLine = endLine
                    )
                )
            }
        }
    }

    return copyrightFindings
}

/**
 * Generate a ScannerDetails object from the given [result] node, which structure depends on the current ScanCode
 * version. The node names to check are specified via [optionsNode], and [versionNode].
 */
private fun generateScannerDetailsFromNode(result: JsonNode, optionsNode: String, versionNode: String):
        ScannerDetails {
    val config = generateScannerOptions(result[optionsNode])
    val version = result[versionNode].textValueOrEmpty()
    return ScannerDetails(ScanCode.SCANNER_NAME, version, config)
}

/**
 * Convert the JSON node with ScanCode [options] to a string that corresponds to the options as they have been
 * passed on the command line.
 */
private fun generateScannerOptions(options: JsonNode?): String {
    fun addValues(list: MutableList<String>, node: JsonNode, key: String) {
        if (node.isEmpty) {
            list += key
            list += node.asText()
        } else {
            node.forEach {
                list += key
                list += it.asText()
            }
        }
    }

    return options?.let {
        val optionList = it.fieldNames().asSequence().fold(mutableListOf<String>()) { list, opt ->
            addValues(list, it[opt], opt)
            list
        }
        optionList.joinToString(separator = " ")
    }.orEmpty()
}

/**
 * Map messages about unknown issues to a more compact form. Return true if solely memory errors occurred,
 * return false otherwise.
 */
internal fun mapUnknownIssues(issues: MutableList<OrtIssue>): Boolean {
    if (issues.isEmpty()) {
        return false
    }

    var onlyMemoryErrors = true

    val mappedIssues = issues.map { fullError ->
        UNKNOWN_ERROR_REGEX.matcher(fullError.message).let { matcher ->
            if (matcher.matches()) {
                val file = matcher.group("file")
                val error = matcher.group("error")
                if (error == "MemoryError") {
                    fullError.copy(message = "ERROR: MemoryError while scanning file '$file'.")
                } else {
                    onlyMemoryErrors = false
                    val message = matcher.group("message").trim()
                    fullError.copy(message = "ERROR: $error while scanning file '$file' ($message).")
                }
            } else {
                onlyMemoryErrors = false
                fullError
            }
        }
    }

    issues.clear()
    issues += mappedIssues.distinctBy { it.message }

    return onlyMemoryErrors
}

/**
 * Map messages about timeout errors to a more compact form. Return true if solely timeout errors occurred,
 * return false otherwise.
 */
internal fun mapTimeoutErrors(issues: MutableList<OrtIssue>): Boolean {
    if (issues.isEmpty()) {
        return false
    }

    var onlyTimeoutErrors = true

    val mappedIssues = issues.map { fullError ->
        TIMEOUT_ERROR_REGEX.matcher(fullError.message).let { matcher ->
            if (matcher.matches() && matcher.group("timeout") == ScanCode.TIMEOUT.toString()) {
                val file = matcher.group("file")
                fullError.copy(
                    message = "ERROR: Timeout after ${ScanCode.TIMEOUT} seconds while scanning file '$file'."
                )
            } else {
                onlyTimeoutErrors = false
                fullError
            }
        }
    }

    issues.clear()
    issues += mappedIssues.distinctBy { it.message }

    return onlyTimeoutErrors
}
