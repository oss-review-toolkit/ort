/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.scanoss

import java.time.Instant

import org.ossreviewtoolkit.clients.scanoss.FullScanResponse
import org.ossreviewtoolkit.clients.scanoss.model.IdentificationType
import org.ossreviewtoolkit.clients.scanoss.model.ScanResponse
import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.Snippet
import org.ossreviewtoolkit.model.SnippetFinding
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

/**
 * Generate a summary from the given SCANOSS [result], using [startTime], [endTime] as metadata. This variant can be
 * used if the result is not read from a local file.
 */
internal fun generateSummary(
    startTime: Instant,
    endTime: Instant,
    result: FullScanResponse
): ScanSummary {
    val licenseFindings = mutableSetOf<LicenseFinding>()
    val copyrightFindings = mutableSetOf<CopyrightFinding>()
    val snippetFindings = mutableSetOf<SnippetFinding>()

    result.forEach { (_, scanResponses) ->
        scanResponses.forEach { scanResponse ->
            if (scanResponse.id == IdentificationType.FILE) {
                licenseFindings += getLicenseFindings(scanResponse)
                copyrightFindings += getCopyrightFindings(scanResponse)
            }

            if (scanResponse.id == IdentificationType.SNIPPET) {
                val file = requireNotNull(scanResponse.file)
                val lines = requireNotNull(scanResponse.lines)
                val sourceLocation = convertLines(file, lines)
                val snippets = getSnippets(scanResponse)

                snippets.forEach {
                    snippetFindings += SnippetFinding(sourceLocation, it)
                }
            }
        }
    }

    return ScanSummary(
        startTime = startTime,
        endTime = endTime,
        licenseFindings = licenseFindings,
        copyrightFindings = copyrightFindings,
        snippetFindings = snippetFindings
    )
}

/**
 * Get the license findings from the given [scanResponse].
 */
private fun getLicenseFindings(scanResponse: ScanResponse): List<LicenseFinding> {
    val path = scanResponse.file ?: return emptyList()
    val score = scanResponse.matched?.removeSuffix("%")?.toFloatOrNull()

    return scanResponse.licenses.map { license ->
        val licenseExpression = runCatching { SpdxExpression.parse(license.name) }.getOrNull()

        val validatedLicense = when {
            licenseExpression == null -> SpdxConstants.NOASSERTION
            licenseExpression.isValid() -> license.name
            else -> "${SpdxConstants.LICENSE_REF_PREFIX}scanoss-${license.name}"
        }

        LicenseFinding(
            license = validatedLicense,
            location = TextLocation(
                path = path,
                startLine = TextLocation.UNKNOWN_LINE,
                endLine = TextLocation.UNKNOWN_LINE
            ),
            score = score
        )
    }
}

/**
 * Get the copyright findings from the given [scanResponse].
 */
private fun getCopyrightFindings(scanResponse: ScanResponse): List<CopyrightFinding> {
    val path = scanResponse.file ?: return emptyList()

    return scanResponse.copyrights.map { copyright ->
        CopyrightFinding(
            statement = copyright.name,
            location = TextLocation(
                path = path,
                startLine = TextLocation.UNKNOWN_LINE,
                endLine = TextLocation.UNKNOWN_LINE
            )
        )
    }
}

/**
 * Get the snippet findings from the given [scanResponse]. If a snippet returned by ScanOSS contains several Purls,
 * several snippets are created in ORT each containing a single Purl.
 */
private fun getSnippets(scanResponse: ScanResponse): Set<Snippet> {
    val matched = requireNotNull(scanResponse.matched)
    val fileUrl = requireNotNull(scanResponse.fileUrl)
    val ossLines = requireNotNull(scanResponse.ossLines)
    val url = requireNotNull(scanResponse.url)
    val purls = requireNotNull(scanResponse.purl)

    val licenses = scanResponse.licenses.map { license ->
        SpdxExpression.parse(license.name)
    }.toSet()

    val score = matched.substringBeforeLast("%").toFloat()
    val snippetLocation = convertLines(fileUrl, ossLines)
    // TODO: No resolved revision is available. Should a ArtifactProvenance be created instead ?
    val snippetProvenance = RepositoryProvenance(VcsInfo(VcsType.UNKNOWN, url, ""), ".")

    return purls.map {
        Snippet(
            score,
            snippetLocation,
            snippetProvenance,
            it,
            licenses.distinct().reduce(SpdxExpression::and).sorted()
        )
    }.toSet()
}

/**
 * Split a [lineRange] returned by ScanOSS such as 1-321 into a [TextLocation] for the given [file].
 */
private fun convertLines(file: String, lineRange: String): TextLocation {
    val splitLines = lineRange.split("-")
    return if (splitLines.size == 2) {
        TextLocation(file, splitLines.first().toInt(), splitLines.last().toInt())
    } else {
        TextLocation(file, splitLines.first().toInt())
    }
}
