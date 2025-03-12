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
import com.scanoss.dto.LicenseDetails
import com.scanoss.dto.ScanFileDetails
import com.scanoss.dto.ScanFileResult
import com.scanoss.dto.enums.MatchType
import com.scanoss.dto.enums.StatusType

import java.lang.invoke.MethodHandles
import java.time.Instant

import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.Snippet
import org.ossreviewtoolkit.model.SnippetFinding
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseIdExpression

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

/**
 * Generate a summary from the given SCANOSS [result], using [startTime], [endTime] as metadata. This variant can be
 * used if the result is not read from a local file.
 */
internal fun generateSummary(startTime: Instant, endTime: Instant, results: List<ScanFileResult>): ScanSummary {
    val licenseFindings = mutableSetOf<LicenseFinding>()
    val copyrightFindings = mutableSetOf<CopyrightFinding>()
    val snippetFindings = mutableSetOf<SnippetFinding>()

    results.forEach { result ->
        result.fileDetails.forEach { details ->
            when (details.matchType) {
                MatchType.file -> {
                    licenseFindings += getLicenseFindings(details)
                    copyrightFindings += getCopyrightFindings(details)
                }

                MatchType.snippet -> {
                    val file = requireNotNull(result.filePath)
                    if (details.status == StatusType.pending) {
                        val lines = requireNotNull(details.lines)
                        val sourceLocations = convertLines(file, lines)
                        val snippets = getSnippets(details)

                        // The number of snippets should match the number of source locations.
                        if (sourceLocations.size != snippets.size) {
                            logger.warn {
                                "Unexpected mismatch in '$file': " +
                                    "${sourceLocations.size} source locations vs ${snippets.size} snippets. " +
                                    "This indicates a potential issue with line range conversion."
                            }
                        }

                        // Associate each source location with its corresponding snippet.
                        sourceLocations.zip(snippets).forEach { (location, snippet) ->
                            snippetFindings += SnippetFinding(location, setOf(snippet))
                        }
                    } else {
                        logger.warn { "File '$file' is identified, not including on snippet findings" }
                        licenseFindings += getLicenseFindings(details)
                        copyrightFindings += getCopyrightFindings(details)
                    }
                }

                MatchType.none -> {
                    // Skip if no details are available.
                }

                else -> throw IllegalArgumentException("Unsupported file match type '${details.matchType}'.")
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
 * Get the license findings from the given [details].
 */
private fun getLicenseFindings(details: ScanFileDetails): List<LicenseFinding> {
    val path = details.file ?: return emptyList()
    val score = details.matched?.removeSuffix("%")?.toFloatOrNull()

    return details.licenseDetails.orEmpty().map { license ->
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
 * Get the copyright findings from the given [details].
 */
private fun getCopyrightFindings(details: ScanFileDetails): List<CopyrightFinding> {
    val path = details.file ?: return emptyList()

    return details.copyrightDetails.orEmpty().map { copyright ->
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
 * Get the snippet findings from the given [details]. If a snippet returned by SCANOSS contains several Purls,
 * the function uses the first PURL as the primary identifier while storing all PURLs in additionalData
 * to preserve the complete information.
 */
private fun getSnippets(details: ScanFileDetails): List<Snippet> {
    val matched = requireNotNull(details.matched)
    val fileUrl = requireNotNull(details.fileUrl)
    val ossLines = requireNotNull(details.ossLines)
    val url = requireNotNull(details.url)
    val purls = requireNotNull(details.purls)

    val license = getUniqueLicenseExpression(details.licenseDetails.toList())

    val score = matched.substringBeforeLast("%").toFloat()
    val locations = convertLines(fileUrl, ossLines)
    // TODO: No resolved revision is available. Should a ArtifactProvenance be created instead ?
    val vcsInfo = VcsHost.parseUrl(url.takeUnless { it == "none" }.orEmpty())
    val provenance = RepositoryProvenance(vcsInfo, ".")

    // Store all PURLs in additionalData to preserve the complete information.
    val additionalData = mapOf(
        "release_date" to details.releaseDate,
        "all_purls" to purls.joinToString(" ")
    )

    // Create one snippet per location, using the first PURL as the primary identifier.
    return locations.map { snippetLocation ->
        Snippet(score, snippetLocation, provenance, purls.firstOrNull().orEmpty(), license, additionalData)
    }
}

/**
 * Split [lineRanges] returned by ScanOSS such as "32-105,117-199" into [TextLocation]s for the given [file].
 */
private fun convertLines(file: String, lineRanges: String): List<TextLocation> =
    lineRanges.split(',').map { lineRange ->
        val splitLines = lineRange.split('-')

        when (splitLines.size) {
            1 -> TextLocation(file, splitLines.first().toInt())
            2 -> TextLocation(file, splitLines.first().toInt(), splitLines.last().toInt())
            else -> throw IllegalArgumentException("Unsupported line range '$lineRange'.")
        }
    }

fun getUniqueLicenseExpression(licensesDetails: List<LicenseDetails>): SpdxExpression {
    if (licensesDetails.isEmpty()) {
        return SpdxLicenseIdExpression(SpdxConstants.NOASSERTION)
    }

    return licensesDetails
        .map { license -> SpdxExpression.parse(license.name) }
        .reduce { acc, expr -> acc and expr }
        .simplify()
}
