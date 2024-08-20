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

import com.scanoss.dto.ScanFileDetails
import com.scanoss.dto.ScanFileResult

import java.time.Instant

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
            when (details.id) {
                "file" -> {
                    licenseFindings += getLicenseFindings(details)
                    copyrightFindings += getCopyrightFindings(details)
                }

                "snippet" -> {
                    val file = requireNotNull(details.file)
                    val lines = requireNotNull(details.lines)
                    val sourceLocations = convertLines(file, lines)
                    val snippets = getSnippets(details)

                    snippets.forEach { snippet ->
                        sourceLocations.forEach { sourceLocation ->
                            // TODO: Aggregate the snippet by source file location.
                            snippetFindings += SnippetFinding(sourceLocation, setOf(snippet))
                        }
                    }
                }

                "none" -> {
                    // Skip if no details are available.
                }

                else -> throw IllegalArgumentException("Unsupported file details id '${details.id}'.")
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
 * Get the snippet findings from the given [details]. If a snippet returned by ScanOSS contains several Purls,
 * several snippets are created in ORT each containing a single Purl.
 */
private fun getSnippets(details: ScanFileDetails): Set<Snippet> {
    val matched = requireNotNull(details.matched)
    val fileUrl = requireNotNull(details.fileUrl)
    val ossLines = requireNotNull(details.ossLines)
    val url = requireNotNull(details.url)
    val purls = requireNotNull(details.purls)

    val licenses = details.licenseDetails.orEmpty().mapTo(mutableSetOf()) { license ->
        SpdxExpression.parse(license.name)
    }

    val score = matched.substringBeforeLast("%").toFloat()
    val locations = convertLines(fileUrl, ossLines)
    // TODO: No resolved revision is available. Should a ArtifactProvenance be created instead ?
    val vcsInfo = VcsHost.parseUrl(url.takeUnless { it == "none" }.orEmpty())
    val provenance = RepositoryProvenance(vcsInfo, ".")

    return buildSet {
        purls.forEach { purl ->
            locations.forEach { snippetLocation ->
                val license = licenses.reduceOrNull(SpdxExpression::and)?.sorted()
                    ?: SpdxLicenseIdExpression(SpdxConstants.NOASSERTION)

                add(Snippet(score, snippetLocation, provenance, purl, license))
            }
        }
    }
}

/**
 * Split [lineRanges] returned by ScanOSS such as "32-105,117-199" into [TextLocation]s for the given [file].
 */
private fun convertLines(file: String, lineRanges: String): List<TextLocation> =
    lineRanges.split(',').map { lineRange ->
        val splitLines = lineRange.split("-")

        when (splitLines.size) {
            1 -> TextLocation(file, splitLines.first().toInt())
            2 -> TextLocation(file, splitLines.first().toInt(), splitLines.last().toInt())
            else -> throw IllegalArgumentException("Unsupported line range '$lineRange'.")
        }
    }
