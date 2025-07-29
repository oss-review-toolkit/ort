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
import org.ossreviewtoolkit.plugins.scanners.scancode.model.ScanCodeResult
import org.ossreviewtoolkit.utils.common.div

import org.semver4j.Semver

const val MAX_SUPPORTED_OUTPUT_FORMAT_MAJOR_VERSION = 4

private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss.n").withZone(ZoneId.of("UTC"))

// Note: The "(File: ...)" part in the patterns below is actually added by ORT's own getRawResult() function.
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

fun ScanCodeResult.toScanSummary(preferFileLicense: Boolean = false): ScanSummary {
    val licenseFindings = mutableSetOf<LicenseFinding>()
    val copyrightFindings = mutableSetOf<CopyrightFinding>()
    val issues = mutableListOf<Issue>()

    val header = headers.single()
    val inputPath = header.getInput()

    val outputFormatVersion = Semver(header.outputFormatVersion)

    if (outputFormatVersion.major > MAX_SUPPORTED_OUTPUT_FORMAT_MAJOR_VERSION) {
        issues += createAndLogIssue(
            source = ScanCodeFactory.descriptor.displayName,
            message = "The output format version $outputFormatVersion exceeds the supported major version " +
                "$MAX_SUPPORTED_OUTPUT_FORMAT_MAJOR_VERSION. Results may be incomplete or incorrect.",
            severity = Severity.WARNING
        )
    }

    val filesOfTypeFile = files.filter { it.type == "file" }

    // Build a map of all ScanCode license keys in the result associated with their corresponding SPDX ID.
    val scanCodeKeyToSpdxIdMappings = licenseReferences?.associate { it.key to it.spdxLicenseKey }
        ?: files.flatMap { it.scanCodeKeyToSpdxIdMappings }.toMap()

    filesOfTypeFile.forEach { file ->
        val licensesWithoutReferences = file.licenses.filter {
            val fromFile = it.fromFile
            fromFile == null
                // Note that "fromFile" contains the name of the input directory, see
                // https://github.com/aboutcode-org/scancode-toolkit/issues/3712.
                || inputPath.resolveSibling(fromFile) == inputPath / file.path
                || (inputPath.path == "." && fromFile.substringAfter('/') == file.path)
                // Check if input is a single file.
                || fromFile == inputPath.name
        }

        // ScanCode creates separate license entries for each license in an expression. Deduplicate these by grouping by
        // the same expression.
        val licenses = licensesWithoutReferences.groupBy {
            LicenseMatch(it.licenseExpression, it.startLine, it.endLine, it.score)
        }.map {
            // Arbitrarily take the first of the duplicate license entries.
            it.value.first()
        }

        val fileLicense = file.detectedLicenseExpressionSpdx
        if (preferFileLicense && fileLicense != null) {
            licenseFindings += LicenseFinding(
                license = fileLicense,
                location = TextLocation(
                    path = file.path,
                    startLine = licenses.minOf { it.startLine },
                    endLine = licenses.maxOf { it.endLine }
                ),
                score = licenses.map { it.score }.average().toFloat()
            )
        } else {
            licenses.mapTo(licenseFindings) { license ->
                val licenseExpression = license.licenseExpressionSpdx
                    ?: license.licenseExpression.mapLicense(scanCodeKeyToSpdxIdMappings)

                LicenseFinding(
                    license = licenseExpression,
                    location = TextLocation(
                        path = file.path,
                        startLine = license.startLine,
                        endLine = license.endLine
                    ),
                    score = license.score
                )
            }
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

    issues += mapScanErrors(this)

    mapUnknownErrors(issues)
    mapTimeoutErrors(issues)

    return ScanSummary(
        startTime = TIMESTAMP_FORMATTER.parse(header.startTimestamp).query(Instant::from),
        endTime = TIMESTAMP_FORMATTER.parse(header.endTimestamp).query(Instant::from),
        licenseFindings = associateLicensesWithExceptions(licenseFindings),
        copyrightFindings = copyrightFindings,
        issues = issues
    )
}

/**
 * Map scan errors for all files using messages that contain the relative file path.
 */
private fun mapScanErrors(result: ScanCodeResult): List<Issue> =
    result.files.flatMap { file ->
        file.scanErrors.map { error ->
            Issue(
                source = ScanCodeFactory.descriptor.displayName,
                message = "$error (File: ${file.path})"
            )
        }
    }

/**
 * Map messages about timeout errors to a more compact form. Return true if solely timeout errors occurred, return false
 * otherwise.
 */
private fun mapTimeoutErrors(issues: MutableList<Issue>): Boolean {
    if (issues.isEmpty()) return false

    var onlyTimeoutErrors = true

    @Suppress("UnsafeCallOnNullableType")
    val mappedIssues = issues.map { fullError ->
        val match = TIMEOUT_ERROR_REGEX.matchEntire(fullError.message)
        if (match != null) {
            val file = match.groups["file"]!!.value
            val timeout = match.groups["timeout"]!!.value

            fullError.copy(
                message = "ERROR: Timeout after $timeout seconds while scanning file '$file'.",
                affectedPath = file
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
private fun mapUnknownErrors(issues: MutableList<Issue>): Boolean {
    if (issues.isEmpty()) return false

    var onlyMemoryErrors = true

    @Suppress("UnsafeCallOnNullableType")
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
