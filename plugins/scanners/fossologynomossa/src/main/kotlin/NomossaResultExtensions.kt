/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.fossologynomossa

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

internal fun NomossaResult.toScanSummary(startTime: Instant, endTime: Instant): ScanSummary {
    val licenseFindings = results.flatMap { fileResult ->
        val fileContent = File(fileResult.file).readText()

        fileResult.licenses.map { licenseInfo ->
            val licenseExpression = runCatching { SpdxExpression.parse(licenseInfo.license) }.getOrNull()

            val safeLicense = when {
                licenseExpression == null -> SpdxConstants.NOASSERTION
                licenseExpression.isValid() -> licenseInfo.license
                else -> "LicenseRef-Nomossa-${licenseInfo.license.replace(Regex("[^A-Za-z0-9.+-]"), "-")}"
            }
            val (startLine, endLine) = byteOffsetsToLineNumbers(fileContent, licenseInfo.start, licenseInfo.end)

            Triple(fileResult.file, safeLicense, startLine to endLine)
        }
    }.groupBy { (file, license, _) -> file to license }
        .map { (fileAndLicense, entries) ->
            val (file, license) = fileAndLicense
            val startLine = entries.minOf { it.third.first }
            val endLine = entries.maxOf { it.third.second }

            LicenseFinding(
                license = license,
                location = TextLocation(
                    path = file,
                    startLine = startLine,
                    endLine = endLine
                ),
                score = 100.0f
            )
        }.toSet()

    return ScanSummary(
        startTime = startTime,
        endTime = endTime,
        licenseFindings = licenseFindings,
        issues = emptyList(),
        copyrightFindings = sortedSetOf()
    )
}

internal fun byteOffsetsToLineNumbers(fileContent: String, startOffset: Int, endOffset: Int): Pair<Int, Int> {
    var startLine = 1
    var endLine = 1

    for ((index, char) in fileContent.withIndex()) {
        if (index >= startOffset && index > endOffset) break

        if (char == '\n') {
            if (index < startOffset) startLine++
            if (index < endOffset) endLine++
        }
    }

    return Pair(startLine, endLine)
}
