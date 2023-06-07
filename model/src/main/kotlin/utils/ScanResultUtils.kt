/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model.utils

import java.time.Instant

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.SnippetFinding

/**
 * Merge the nested [ScanResult]s into one [ScanResult] per used scanner. The given [scanResultsByPath] must contain at
 * least one scan result associated with the empty string which defines the package provenance and is used as provenance
 * for the result. All other entries in [scanResultsByPath] hold the scan results for each respective (recursive)
 * sub-repository of the main repository. This maps the given [scanResultsByPath] to the format currently used by
 * [OrtResult]. When merging multiple [ScanSummary]s for a particular scanner the earliest start time and lasted end
 * time will be used as the new values for the respective scanner. Because the [ScanSummary] does not contain the
 * checksums of the individual files, no package verification code can be calculated.
 */
fun mergeScanResultsByScanner(scanResultsByPath: Map<String, List<ScanResult>>): List<ScanResult> {
    val rootProvenance = scanResultsByPath.getValue("").map { it.provenance }.distinct().also {
        require(it.size == 1) { "There must be exactly one unique provenance associated with the empty path." }
    }.first()

    val allScanners = scanResultsByPath.values.flatMapTo(mutableSetOf()) { results -> results.map { it.scanner } }

    return allScanners.map { scanner ->
        val scanResultsForScannerByPath = scanResultsByPath.mapValues { (_, scanResults) ->
            scanResults.filter { it.scanner == scanner }
        }

        val scanResultsForScanner = scanResultsForScannerByPath.values.flatten()

        val startTime = scanResultsForScanner.minByOrNull { it.summary.startTime }?.summary?.startTime ?: Instant.now()
        val endTime = scanResultsForScanner.maxByOrNull { it.summary.endTime }?.summary?.endTime ?: startTime
        val issues = scanResultsForScanner.flatMap { it.summary.issues }.distinct()

        val licenseFindings = scanResultsForScannerByPath.mergeLicenseFindings()
        val copyrightFindings = scanResultsForScannerByPath.mergeCopyrightFindings()
        val snippetFindings = scanResultsForScannerByPath.mergeSnippetFindings()

        ScanResult(
            provenance = rootProvenance,
            scanner = scanner,
            summary = ScanSummary(
                startTime = startTime,
                endTime = endTime,
                packageVerificationCode = "",
                licenseFindings = licenseFindings,
                copyrightFindings = copyrightFindings,
                snippetFindings = snippetFindings,
                issues = issues
            ),
            additionalData = scanResultsForScanner.map { it.additionalData }.reduce { acc, map -> acc + map }
        )
    }
}

private fun Map<String, List<ScanResult>>.mergeLicenseFindings(): Set<LicenseFinding> {
    val findingsByPath = mapValues { (_, scanResults) ->
        scanResults.flatMap { it.summary.licenseFindings }
    }

    val findings = findingsByPath.flatMapTo(mutableSetOf()) { (path, findings) ->
        findings.map { it.copy(location = it.location.prependPath(path)) }
    }

    return findings
}

private fun Map<String, List<ScanResult>>.mergeCopyrightFindings(): Set<CopyrightFinding> {
    val findingsByPath = mapValues { (_, scanResults) ->
        scanResults.flatMap { it.summary.copyrightFindings }
    }

    val findings = findingsByPath.flatMapTo(mutableSetOf()) { (path, findings) ->
        findings.map { it.copy(location = it.location.prependPath(path)) }
    }

    return findings
}

private fun Map<String, List<ScanResult>>.mergeSnippetFindings(): Set<SnippetFinding> {
    val findingsByPath = mapValues { (_, scanResults) ->
        scanResults.flatMap { it.summary.snippetFindings }
    }

    val findings = findingsByPath.flatMapTo(mutableSetOf()) { (path, findings) ->
        findings.map { it.copy(sourceLocation = it.sourceLocation.prependPath(path)) }
    }

    return findings
}
