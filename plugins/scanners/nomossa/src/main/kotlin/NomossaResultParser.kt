/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.scanners.nomossa

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdxexpression.toSpdxOrNull

internal fun NomossaResult.toScanSummary(startTime: Instant, endTime: Instant): ScanSummary {
    val licenseFindings = results.flatMap { fileResult ->
        fileResult.licenses.map { licenseInfo ->
            val licenseExpression = licenseInfo.license.toSpdxOrNull()

            val safeLicense = when {
                licenseExpression == null -> SpdxConstants.NOASSERTION
                licenseExpression.isValid() -> licenseInfo.license
                else -> "LicenseRef-Nomossa-${licenseInfo.license.replace(Regex("[^A-Za-z0-9.+-]"), "-")}"
            }

            Triple(fileResult.file, safeLicense, licenseInfo.start to licenseInfo.end)
        }
    }.groupBy { (file, license, _) ->
        file to license
    }.mapTo(mutableSetOf()) { (fileAndLicense, entries) ->
        val (file, license) = fileAndLicense

        val minOffset = entries.minOf { it.third.first }
        val maxOffset = entries.maxOf { it.third.second }

        val fileBytes = File(file).inputStream().use { it.readNBytes(maxOffset) }
        val (startLine, endLine) = byteOffsetsToLineNumbers(fileBytes, minOffset, maxOffset)

        LicenseFinding(
            license = license,
            location = TextLocation(
                path = file,
                startLine = startLine,
                endLine = endLine
            )
        )
    }

    return ScanSummary(
        startTime = startTime,
        endTime = endTime,
        licenseFindings = licenseFindings,
        issues = listOf(
            Issue(
                source = "Nomossa",
                message = "This scanner is not capable of detecting copyright statements.",
                severity = Severity.HINT
            )
        )
    )
}

/**
 * Nomossa reports [startOffset] and [endOffset] as byte offsets into the scanned file, not character offsets. So
 * [fileBytes] must be the file's raw bytes, as a decoded [String] would count multi-byte characters incorrectly.
 */
internal fun byteOffsetsToLineNumbers(fileBytes: ByteArray, startOffset: Int, endOffset: Int): Pair<Int, Int> {
    var startLine = 1
    var endLine = 1
    val newline = '\n'.code.toByte()

    for (index in fileBytes.indices) {
        if (index >= startOffset && index > endOffset) break

        if (fileBytes[index] == newline) {
            if (index < startOffset) startLine++
            if (index < endOffset) endLine++
        }
    }

    return Pair(startLine, endLine)
}
