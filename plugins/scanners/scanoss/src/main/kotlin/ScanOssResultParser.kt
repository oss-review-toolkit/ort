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
import org.ossreviewtoolkit.utils.spdx.toExpression

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

/**
 * Generate a summary from the given SCANOSS [results], using [startTime], [endTime] as metadata. This variant can be
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
                    val localFile = requireNotNull(result.filePath)
                    licenseFindings += getLicenseFindings(details, localFile)
                    copyrightFindings += getCopyrightFindings(details, localFile)
                }

                MatchType.snippet -> {
                    val localFile = requireNotNull(result.filePath)
                    if (details.status == StatusType.pending) {
                        snippetFindings += createSnippetFindings(details, localFile)
                    } else {
                        logger.info { "File '$localFile' is identified, not including in snippet findings." }
                        licenseFindings += getLicenseFindings(details, result.filePath)
                        copyrightFindings += getCopyrightFindings(details, result.filePath)
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
private fun getLicenseFindings(details: ScanFileDetails, path: String): List<LicenseFinding> {
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
private fun getCopyrightFindings(details: ScanFileDetails, path: String): List<CopyrightFinding> {
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
 * Create snippet findings from given [details] and [localFilePath]. If a snippet returned by ScanOSS contains several
 * PURLs the function extracts the first PURL as the primary identifier while storing the remaining PURLs in
 * additionalData to preserve the complete information.
 */
private fun createSnippetFindings(details: ScanFileDetails, localFilePath: String): Set<SnippetFinding> {
    val matched = requireNotNull(details.matched)
    val ossFile = requireNotNull(details.file)
    val ossLines = requireNotNull(details.ossLines)
    val localLines = requireNotNull(details.lines)
    val url = requireNotNull(details.url)
    val purls = requireNotNull(details.purls).toMutableList()

    val score = matched.substringBeforeLast("%").toFloat()
    val primaryPurl = purls.removeFirstOrNull().orEmpty()

    val license = details.licenseDetails.orEmpty()
        .map { license -> SpdxExpression.parse(license.name) }
        .toExpression()?.sorted() ?: SpdxLicenseIdExpression(SpdxConstants.NOASSERTION)

    // TODO: No resolved revision is available. Should a ArtifactProvenance be created instead ?
    val vcsInfo = VcsHost.parseUrl(url.takeUnless { it == "none" }.orEmpty())
    val provenance = RepositoryProvenance(vcsInfo, ".")

    val additionalData = buildMap {
        put("file_hash", details.fileHash)
        put("file_url", details.fileUrl)
        put("source_hash", details.sourceHash)

        // Purls can be empty if only one entry is provided which is used as the primary purl.
        if (purls.isNotEmpty()) put("related_purls", purls.joinToString(",") { it.trim() })
    }

    // Convert both local and OSS line ranges to source locations.
    val sourceLocations = convertLines(localFilePath, localLines)
    val ossLocations = convertLines(ossFile, ossLines)

    // The number of source locations should match the number of oss locations.
    if (sourceLocations.size != ossLocations.size) {
        logger.warn {
            "Unexpected mismatch in '$localFilePath': " +
                "${sourceLocations.size} source locations vs ${ossLocations.size} oss locations. " +
                "This indicates a potential issue with line range conversion."
        }
    }

    // Directly pair source locations with their corresponding OSS locations and create a SnippetFinding.
    return sourceLocations.zip(ossLocations).mapTo(mutableSetOf()) { (sourceLocation, ossLocation) ->
        SnippetFinding(
            sourceLocation,
            setOf(
                Snippet(score, ossLocation, provenance, primaryPurl, license, additionalData)
            )
        )
    }
}

/**
 * Split [lineRanges] returned by SCANOSS such as "32-105,117-199" into [TextLocation]s for the given [file].
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
