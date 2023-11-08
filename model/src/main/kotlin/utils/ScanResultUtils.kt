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

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.SnippetFinding

/**
 * Merge the nested [ScanResult]s into one [ScanResult] per used scanner. The entry for the empty string in
 * [scanResultsByPath] holds the scan results for the root provenance of the package. All further entries in
 * [scanResultsByPath] hold the scan results corresponding to the path the (sub-)repository appears in the source tree.
 * This maps the given [scanResultsByPath] to the format currently used by [OrtResult]. When merging multiple
 * [ScanSummary]s for a particular scanner the earliest start time and lasted end time will be used as the new values
 * for the respective scanner. The given [packageProvenance] is used as provenance for all returned merged scan results.
 */
fun mergeScanResultsByScanner(
    scanResultsByPath: Map<String, List<ScanResult>>,
    packageProvenance: KnownProvenance
): List<ScanResult> {
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
            provenance = packageProvenance,
            scanner = scanner,
            summary = ScanSummary(
                startTime = startTime,
                endTime = endTime,
                licenseFindings = licenseFindings,
                copyrightFindings = copyrightFindings,
                snippetFindings = snippetFindings,
                issues = issues
            ),
            additionalData = scanResultsForScanner.map { it.additionalData }.mergeAdditionalData()
        )
    }
}

/**
 * Merge a list of [ScanResult.additionalData] by keeping all keys and concatenating values to a string separated by
 * commas.
 */
private fun List<Map<String, String>>.mergeAdditionalData(): Map<String, String> =
    flatMap { it.entries }
        .groupBy({ it.key }) { it.value }
        .mapValues { it.value.joinToString(",") }

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

fun ScanResult.filterByVcsPath(path: String): ScanResult {
    if (provenance !is RepositoryProvenance) return this

    return takeUnless { provenance.vcsInfo.path != path && File(path).startsWith(File(provenance.vcsInfo.path)) }
        ?: filterByPath(path)
}
