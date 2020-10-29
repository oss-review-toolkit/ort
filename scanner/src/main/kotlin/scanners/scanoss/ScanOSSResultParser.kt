/*
 * Copyright (C) 2020 SCANOSS TECNOLOGIAS SL
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

package org.ossreviewtoolkit.scanner.scanners.scanoss

import com.fasterxml.jackson.databind.JsonNode

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.EMPTY_JSON_NODE
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.spdx.SpdxConstants
import org.ossreviewtoolkit.spdx.SpdxException
import org.ossreviewtoolkit.spdx.calculatePackageVerificationCode

/**
 * Generate a summary from the given raw ScanOSS [result], using [startTime] and [endTime] metadata.
 * From the [scanPath] the package verification code is generated.
 */
internal fun generateSummary(startTime: Instant, endTime: Instant, scanPath: File, result: JsonNode) =
    generateSummary(
        startTime,
        endTime,
        calculatePackageVerificationCode(scanPath),
        result
    )

/**
 * Generate a summary from the given raw ScanOSS [result], using [startTime], [endTime], and [verificationCode]
 * metadata. This variant can be used if the result is not read from a local file.
 */
internal fun generateSummary(startTime: Instant, endTime: Instant, verificationCode: String, result: JsonNode) =
    ScanSummary(
        startTime = startTime,
        endTime = endTime,
        fileCount = result.size(),
        packageVerificationCode = verificationCode,
        licenseFindings = getLicenseFindings(result).toSortedSet(),
        copyrightFindings = getCopyrightFindings(result).toSortedSet(),
        issues = emptyList<OrtIssue>()
    )

/**
 * Parses result file and returns a JsonNode
 */
internal fun parseResult(resultsFile: File): JsonNode {
    return if (resultsFile.isFile && resultsFile.length() > 0L) {
        jsonMapper.readTree(resultsFile)
    } else {
        EMPTY_JSON_NODE
    }
}

/**
 * Get the license findings from the given [result].
 */
private fun getLicenseFindings(result: JsonNode): List<LicenseFinding> {
    val licenseFindings = mutableListOf<LicenseFinding>()

    val files = result.fields()
    while (files.hasNext()) {
        val file = files.next()
        val matches = file.value?.asSequence().orEmpty()
        for (match in matches) {
            val licenses = match["licenses"]?.asSequence().orEmpty()
            licenseFindings.addAll(licenses.map {
                try {
                    LicenseFinding(
                        license = it["name"].asText(),
                        location = TextLocation(
                            path = file.key,
                            startLine = 1,
                            endLine = 1
                        )
                    )
                } catch (e: SpdxException) {
                    LicenseFinding(
                        license = SpdxConstants.NOASSERTION,
                        location = TextLocation(
                            path = file.key,
                            startLine = 1,
                            endLine = 1
                        )
                    )
                }
            })
        }
    }

    return licenseFindings
}

/**
 * Get the copyright findings from the given [result].
 */
private fun getCopyrightFindings(result: JsonNode): List<CopyrightFinding> {
    val copyrightFindings = mutableListOf<CopyrightFinding>()

    val files = result.fields()
    while (files.hasNext()) {
        val file = files.next()
        val matches = file.value?.asSequence().orEmpty()
        for (match in matches) {
            val copyrights = match["copyrights"]?.asSequence().orEmpty()
            copyrightFindings.addAll(copyrights.map {
                CopyrightFinding(
                    statement = it["name"].asText(),
                    location = TextLocation(
                        path = file.key,
                        startLine = 1,
                        endLine = 1
                    )
                )
            })
        }
    }

    return copyrightFindings
}
