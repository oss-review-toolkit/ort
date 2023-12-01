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

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

const val MAX_SUPPORTED_OUTPUT_FORMAT_MAJOR_VERSION = 3

private val LICENSE_REF_PREFIX_SCAN_CODE = "${SpdxConstants.LICENSE_REF_PREFIX}${ScanCode.SCANNER_NAME.lowercase()}-"

private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss.n").withZone(ZoneId.of("UTC"))

// Note: The "(File: ...)" part in the patterns below is actually added by our own getRawResult() function.
private val UNKNOWN_ERROR_REGEX = Regex(
    "(ERROR: for scanner: (?<scanner>\\w+):\n)?" +
        "ERROR: Unknown error:\n.+\n(?<error>\\w+Error)[:\n](?<message>.*) \\(File: (?<file>.+)\\)",
    RegexOption.DOT_MATCHES_ALL
)

private val TIMEOUT_ERROR_REGEX = Regex(
    "(ERROR: for scanner: (?<scanner>\\w+):\n)?" +
        "ERROR: Processing interrupted: timeout after (?<timeout>\\d+) seconds. \\(File: (?<file>.+)\\)"
)

private data class LicenseMatch(
    val expression: String,
    val startLine: Int,
    val endLine: Int,
    val score: Float
)

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
    val scanCodeKeyToSpdxIdMappings = licenseReferences?.associate { it.key to it.spdxLicenseKey }
        ?: files.flatMap { file ->
            file.licenses.filterIsInstance<LicenseEntry.Version1>().map { license ->
                license.key to getSpdxId(license.spdxLicenseKey, license.key)
            }
        }.toMap()

    filesOfTypeFile.forEach { file ->
        // ScanCode creates separate license entries for each license in an expression. Deduplicate these by grouping by
        // the same expression.
        val licenses = file.licenses.groupBy {
            LicenseMatch(it.licenseExpression, it.startLine, it.endLine, it.score)
        }.map {
            // Arbitrarily take the first of the duplicate license entries.
            it.value.first()
        }

        licenses.mapTo(licenseFindings) { license ->
            // ScanCode uses its own license keys as identifiers in license expressions.
            val spdxLicenseExpression = license.licenseExpression.mapLicense(scanCodeKeyToSpdxIdMappings)

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

private fun getSpdxId(spdxLicenseKey: String?, key: String): String {
    // There is a bug in ScanCode 3.0.2 that returns an empty string instead of null for licenses unknown to SPDX.
    val spdxId = spdxLicenseKey.orEmpty().toSpdxId(allowPlusSuffix = true)

    if (spdxId.isNotEmpty()) return spdxId

    // Fall back to building an ID based on the ScanCode-specific "key".
    return "$LICENSE_REF_PREFIX_SCAN_CODE${key.toSpdxId(allowPlusSuffix = true)}"
}

/**
 * Map scan errors for all files using messages that contain the relative file path.
 */
private fun mapScanErrors(result: ScanCodeResult): List<Issue> {
    val input = result.headers.single().options.input.single()
    return result.files.flatMap { file ->
        val path = file.path.removePrefix(input).removePrefix("/")
        file.scanErrors.map { error ->
            Issue(
                source = ScanCode.SCANNER_NAME,
                message = "$error (File: $path)"
            )
        }
    }
}

/**
 * Map messages about timeout errors to a more compact form. Return true if solely timeout errors occurred, return false
 * otherwise.
 */
internal fun mapTimeoutErrors(issues: MutableList<Issue>): Boolean {
    if (issues.isEmpty()) return false

    var onlyTimeoutErrors = true

    val mappedIssues = issues.map { fullError ->
        val match = TIMEOUT_ERROR_REGEX.matchEntire(fullError.message)
        if (match?.groups?.get("timeout")?.value == ScanCode.TIMEOUT.toString()) {
            val file = match.groups["file"]!!.value
            fullError.copy(
                message = "ERROR: Timeout after ${ScanCode.TIMEOUT} seconds while scanning file '$file'."
            )
        } else {
            onlyTimeoutErrors = false
            fullError
        }
    }

    issues.clear()
    issues += mappedIssues.distinctBy { it.message }

    return onlyTimeoutErrors
}

/**
 * Map messages about unknown errors to a more compact form. Return true if solely memory errors occurred, return false
 * otherwise.
 */
internal fun mapUnknownErrors(issues: MutableList<Issue>): Boolean {
    if (issues.isEmpty()) return false

    var onlyMemoryErrors = true

    val mappedIssues = issues.map { fullError ->
        UNKNOWN_ERROR_REGEX.matchEntire(fullError.message)?.let { match ->
            val file = match.groups["file"]!!.value
            val error = match.groups["error"]!!.value
            if (error == "MemoryError") {
                fullError.copy(message = "ERROR: MemoryError while scanning file '$file'.")
            } else {
                onlyMemoryErrors = false
                val message = match.groups["message"]!!.value.trim()
                fullError.copy(message = "ERROR: $error while scanning file '$file' ($message).")
            }
        } ?: run {
            onlyMemoryErrors = false
            fullError
        }
    }

    issues.clear()
    issues += mappedIssues.distinctBy { it.message }

    return onlyMemoryErrors
}
