/*
 * Copyright (C) 2020-2021 SCANOSS TECNOLOGIAS SL
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

package org.ossreviewtoolkit.scanner.scanners.scanoss

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.clients.scanoss.FullScanResponse
import org.ossreviewtoolkit.clients.scanoss.model.ScanResponse
import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.calculatePackageVerificationCode

/**
 * Generate a summary from the given SCANOSS [result], using [startTime] and [endTime] metadata. From the [scanPath]
 * the package verification code is generated.
 */
internal fun generateSummary(
    startTime: Instant,
    endTime: Instant,
    scanPath: File,
    result: FullScanResponse,
    detectedLicenseMapping: Map<String, String>,
) =
    generateSummary(
        startTime,
        endTime,
        calculatePackageVerificationCode(scanPath),
        result,
        detectedLicenseMapping
    )

/**
 * Generate a summary from the given SCANOSS [result], using [startTime], [endTime], and [verificationCode]
 * metadata. This variant can be used if the result is not read from a local file.
 */
internal fun generateSummary(
    startTime: Instant,
    endTime: Instant,
    verificationCode: String,
    result: FullScanResponse,
    detectedLicenseMapping: Map<String, String>
): ScanSummary {
    val licenseFindings = mutableListOf<LicenseFinding>()
    val copyrightFindings = mutableListOf<CopyrightFinding>()

    result.forEach { (_, scanResponses) ->
        scanResponses.forEach { scanResponse ->
            licenseFindings += getLicenseFindings(scanResponse, detectedLicenseMapping)
            copyrightFindings += getCopyrightFindings(scanResponse)
        }
    }

    return ScanSummary(
        startTime = startTime,
        endTime = endTime,
        packageVerificationCode = verificationCode,
        licenseFindings = licenseFindings.toSortedSet(),
        copyrightFindings = copyrightFindings.toSortedSet(),
        issues = emptyList()
    )
}

/**
 * Get the license findings from the given [scanResponse].
 */
private fun getLicenseFindings(
    scanResponse: ScanResponse,
    detectedLicenseMappings: Map<String, String>
): List<LicenseFinding> {
    val score = scanResponse.matched.removeSuffix("%").toFloatOrNull()
    return scanResponse.licenses.map { license ->
        val licenseExpression = runCatching { SpdxExpression.parse(license.name) }.getOrNull()

        val validatedLicense = when {
            licenseExpression == null -> SpdxConstants.NOASSERTION
            licenseExpression.isValid() -> license.name
            else -> "${SpdxConstants.LICENSE_REF_PREFIX}scanoss-${license.name}"
        }

        LicenseFinding.createAndMap(
            license = validatedLicense,
            location = TextLocation(
                path = scanResponse.file,
                startLine = TextLocation.UNKNOWN_LINE,
                endLine = TextLocation.UNKNOWN_LINE
            ),
            score = score,
            detectedLicenseMapping = detectedLicenseMappings
        )
    }
}

/**
 * Get the copyright findings from the given [scanResponse].
 */
private fun getCopyrightFindings(scanResponse: ScanResponse): List<CopyrightFinding> =
    scanResponse.copyrights.map { copyright ->
        CopyrightFinding(
            statement = copyright.name,
            location = TextLocation(
                path = scanResponse.file,
                startLine = TextLocation.UNKNOWN_LINE,
                endLine = TextLocation.UNKNOWN_LINE
            )
        )
    }
