/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.fossid

import java.lang.invoke.MethodHandles

import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.clients.fossid.model.identification.identifiedFiles.IdentifiedFile
import org.ossreviewtoolkit.clients.fossid.model.identification.ignored.IgnoredFile
import org.ossreviewtoolkit.clients.fossid.model.identification.markedAsIdentified.MarkedAsIdentifiedFile
import org.ossreviewtoolkit.clients.fossid.model.result.MatchType
import org.ossreviewtoolkit.clients.fossid.model.result.MatchedLines
import org.ossreviewtoolkit.clients.fossid.model.result.Snippet
import org.ossreviewtoolkit.clients.fossid.model.summary.Summarizable
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.PackageProvider
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.Snippet as OrtSnippet
import org.ossreviewtoolkit.model.SnippetFinding
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.config.snippet.SnippetChoice
import org.ossreviewtoolkit.model.config.snippet.SnippetChoiceReason
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.mapLicense
import org.ossreviewtoolkit.model.utils.PurlType
import org.ossreviewtoolkit.utils.common.alsoIfNull
import org.ossreviewtoolkit.utils.common.collapseToRanges
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.prettyPrintRanges
import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.toSpdx

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

/**
 * A data class to hold FossID raw results.
 */
internal data class RawResults(
    val identifiedFiles: List<IdentifiedFile>,
    val markedAsIdentifiedFiles: List<MarkedAsIdentifiedFile>,
    val listIgnoredFiles: List<IgnoredFile>,
    val listPendingFiles: List<String>,
    val listSnippets: Map<String, Set<Snippet>>,
    val snippetMatchedLines: Map<Int, MatchedLines> = emptyMap()
)

/**
 * A data class to hold FossID mapped results.
 */
internal data class FindingsContainer(
    val licenseFindings: MutableSet<LicenseFinding>,
    val copyrightFindings: MutableSet<CopyrightFinding>
)

/**
 * Map a FossID raw result to sections that can be included in a [org.ossreviewtoolkit.model.ScanSummary].
 */
internal fun <T : Summarizable> List<T>.mapSummary(
    ignoredFiles: Map<String, IgnoredFile>,
    issues: MutableList<Issue>,
    detectedLicenseMapping: Map<String, String>
): FindingsContainer {
    val licenseFindings = mutableSetOf<LicenseFinding>()
    val copyrightFindings = mutableSetOf<CopyrightFinding>()

    val files = filterNot { it.getFileName() in ignoredFiles }
    files.forEach { summarizable ->
        val summary = summarizable.toSummary()
        val defaultLocation = TextLocation(summary.path, TextLocation.UNKNOWN_LINE, TextLocation.UNKNOWN_LINE)

        summary.licences.forEach { licenseAddedInTheUI ->
            mapLicense(licenseAddedInTheUI.identifier, defaultLocation, issues, detectedLicenseMapping)?.let {
                licenseFindings += it
            }
        }

        summarizable.getCopyright().let {
            if (it.isNotEmpty()) {
                copyrightFindings += CopyrightFinding(it, defaultLocation)
            }
        }
    }

    return FindingsContainer(
        licenseFindings = licenseFindings,
        copyrightFindings = copyrightFindings
    )
}

/**
 * Convert a [license] at [location] from FossID to a valid [LicenseFinding]. If the license cannot be mapped, null is
 * returned and an issue is added to [issues].
 */
private fun mapLicense(
    license: String,
    location: TextLocation,
    issues: MutableList<Issue>,
    detectedLicenseMapping: Map<String, String>
): LicenseFinding? {
    return runCatching {
        // TODO: The detected license mapping must be applied here, because FossID can return license strings
        //       which cannot be parsed to an SpdxExpression. A better solution could be to automatically
        //       convert the strings into a form that can be parsed, then the mapping could be applied globally.
        LicenseFinding(license.mapLicense(detectedLicenseMapping), location)
    }.onSuccess { licenseFinding ->
        licenseFinding.copy(license = licenseFinding.license.normalize())
    }.onFailure { spdxException ->
        issues += FossId.createAndLogIssue(
            source = "FossId",
            message = "Failed to parse license '$license' as an SPDX expression: ${spdxException.collectMessages()}"
        )
    }.getOrNull()
}

/**
 * Map the raw snippets to ORT [SnippetFinding]s. If a snippet license cannot be parsed, an issues is added to [issues].
 * [LicenseFinding]s due to chosen snippets will be added to [snippetLicenseFindings].
 */
internal fun mapSnippetFindings(
    rawResults: RawResults,
    issues: MutableList<Issue>,
    snippetChoices: List<SnippetChoice>,
    snippetLicenseFindings: MutableSet<LicenseFinding>
): Set<SnippetFinding> {
    val remainingSnippetChoices = snippetChoices.toMutableList()

    return rawResults.listSnippets.flatMap { (file, rawSnippets) ->
        val findings = mutableMapOf<TextLocation, MutableSet<OrtSnippet>>()

        rawSnippets.forEach { snippet ->
            val license = snippet.artifactLicense?.let { artifactLicense ->
                DeclaredLicenseProcessor.process(artifactLicense).alsoIfNull {
                    issues += FossId.createAndLogIssue(
                        source = "FossId",
                        message = "Failed to map license '$artifactLicense' as an SPDX expression.",
                        severity = Severity.HINT
                    )
                }
            } ?: SpdxConstants.NOASSERTION.toSpdx()

            // FossID does not return the hash of the remote artifact. Instead, it returns the MD5 hash of the
            // matched file in the remote artifact as part of the "match_file_id" property.
            val url = checkNotNull(snippet.url) {
                "The URL of snippet ${snippet.id} for file '$file' must not be null."
            }
            val snippetProvenance = ArtifactProvenance(RemoteArtifact(url, Hash.NONE))
            val purl = snippet.purl
                ?: "pkg:${urlToPackageType(url)}/${snippet.author}/${snippet.artifact}@${snippet.version}"

            val additionalSnippetData = mutableMapOf(
                FossId.SNIPPET_DATA_ID to snippet.id.toString(),
                FossId.SNIPPET_DATA_MATCH_TYPE to snippet.matchType.toString(),
                FossId.SNIPPET_DATA_RELEASE_DATE to snippet.releaseDate.orEmpty()
            )

            var sourceLocations: Set<TextLocation> = setOf(TextLocation(file, TextLocation.UNKNOWN_LINE))
            var snippetLocation: TextLocation? = null

            if (snippet.matchType == MatchType.PARTIAL) {
                val rawMatchedLines = rawResults.snippetMatchedLines[snippet.id]
                val rawMatchedLinesSourceFile = rawMatchedLines?.localFile.orEmpty().collapseToRanges()
                val rawMatchedLinesSnippetFile = rawMatchedLines?.mirrorFile.orEmpty().collapseToRanges()

                if (rawMatchedLinesSourceFile.isNotEmpty()) {
                    sourceLocations = rawMatchedLinesSourceFile.map { (first, second) ->
                        TextLocation(file, first, second)
                    }.toSet()
                }

                snippetLocation = rawMatchedLinesSnippetFile.firstOrNull()
                    ?.let { (startLine, endLine) -> TextLocation(snippet.file, startLine, endLine) }

                if (rawMatchedLinesSourceFile.isNotEmpty()) {
                    additionalSnippetData[FossId.SNIPPET_DATA_MATCHED_LINE_SOURCE] =
                        rawMatchedLinesSourceFile.prettyPrintRanges()
                }

                if (rawMatchedLinesSnippetFile.isNotEmpty()) {
                    additionalSnippetData[FossId.SNIPPET_DATA_MATCHED_LINE_SNIPPET] =
                        rawMatchedLinesSnippetFile.prettyPrintRanges()
                }
            }

            val ortSnippet = OrtSnippet(
                snippet.score.toFloat(),
                snippetLocation ?: TextLocation(snippet.file, TextLocation.UNKNOWN_LINE),
                snippetProvenance,
                purl,
                license,
                additionalSnippetData
            )

            sourceLocations.forEach { sourceLocation ->
                val isSnippetChoice = when {
                    snippetChoices.any { it.given.sourceLocation == sourceLocation && it.choice.purl == purl } -> {
                        logger.info {
                            "Ignoring snippet $purl for file ${sourceLocation.prettyPrint()}, " +
                                "as this is a chosen snippet."
                        }
                        true
                    }

                    snippetChoices.any { it.given.sourceLocation == sourceLocation } -> {
                        logger.info {
                            "Ignoring snippet $purl for file ${sourceLocation.prettyPrint()}, " +
                                "as there is a snippet choice for this source location."
                        }
                        true
                    }

                    else -> false
                }

                val isLocationWithFalsePositives = remainingSnippetChoices.removeIf {
                    it.given.sourceLocation == sourceLocation &&
                        it.choice.reason == SnippetChoiceReason.NO_RELEVANT_FINDING
                }

                if (isLocationWithFalsePositives) {
                    logger.info {
                        "Ignoring snippet $purl for file ${sourceLocation.prettyPrint()}, " +
                            "as this is a location with only false positives."
                    }
                }

                if (!isSnippetChoice && !isLocationWithFalsePositives) {
                    findings.getOrPut(sourceLocation) { mutableSetOf(ortSnippet) } += ortSnippet
                }

                getLicenseFindingFromSnippetChoice(
                    remainingSnippetChoices,
                    sourceLocation,
                    ortSnippet
                )?.let { finding -> snippetLicenseFindings += finding }
            }
        }

        findings.map { SnippetFinding(it.key, it.value) }
    }.toSet().also {
        remainingSnippetChoices.forEach { snippetChoice ->
            val message = "The configuration contains a snippet choice for the snippet ${snippetChoice.choice.purl} " +
                "at ${snippetChoice.given.sourceLocation.prettyPrint()}, but the FossID result contains no such " +
                "snippet."
            logger.warn(message)
            issues += Issue(
                source = "FossId",
                message = message,
                severity = Severity.WARNING
            )
        }
    }
}

/**
 * Check if [snippet] is a chosen snippet for the given [sourceLocation]. If it is, remove it from
 * [remainingSnippetChoices] and return a [LicenseFinding]. Otherwise, return null.
 */
private fun getLicenseFindingFromSnippetChoice(
    remainingSnippetChoices: MutableList<SnippetChoice>,
    sourceLocation: TextLocation,
    snippet: OrtSnippet
): LicenseFinding? {
    val isSnippetChoice = remainingSnippetChoices.removeIf { snippetChoice ->
        snippetChoice.given.sourceLocation == sourceLocation && snippetChoice.choice.purl == snippet.purl
    }

    return if (isSnippetChoice) {
        logger.info {
            "Adding snippet choice for ${sourceLocation.prettyPrint()} " +
                "with license ${snippet.licenses} to the license findings."
        }
        LicenseFinding(snippet.licenses, sourceLocation)
    } else {
        null
    }
}

/**
 * Return the [PurlType] as determined from the given [url], or [PurlType.GENERIC] if there is no match.
 */
private fun urlToPackageType(url: String): PurlType =
    when (val provider = PackageProvider.get(url)) {
        PackageProvider.COCOAPODS -> PurlType.COCOAPODS
        PackageProvider.CRATES_IO -> PurlType.CARGO
        PackageProvider.DEBIAN -> PurlType.DEBIAN
        PackageProvider.GITHUB -> PurlType.GITHUB
        PackageProvider.GITLAB -> PurlType.GITLAB
        PackageProvider.GOLANG -> PurlType.GOLANG
        PackageProvider.MAVEN_CENTRAL, PackageProvider.MAVEN_GOOGLE -> PurlType.MAVEN
        PackageProvider.NPM_JS -> PurlType.NPM
        PackageProvider.NUGET -> PurlType.NUGET
        PackageProvider.PACKAGIST -> PurlType.COMPOSER
        PackageProvider.PYPI -> PurlType.PYPI
        PackageProvider.RUBYGEMS -> PurlType.GEM

        else -> {
            PurlType.GENERIC.also {
                logger.warn {
                    "Cannot determine PURL type for URL $url and provider '$provider'. Falling back to '$it'."
                }
            }
        }
    }

internal fun TextLocation.prettyPrint(): String =
    if (startLine == TextLocation.UNKNOWN_LINE && endLine == TextLocation.UNKNOWN_LINE) {
        "$path#FULL"
    } else {
        "$path#$startLine-$endLine"
    }
