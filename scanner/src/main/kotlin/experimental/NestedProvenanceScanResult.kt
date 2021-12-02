/*
 * Copyright (C) 2021 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.scanner.experimental

import java.time.Instant
import java.util.SortedSet

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary

/**
 * A class that contains all [ScanResult]s for a [NestedProvenance].
 */
data class NestedProvenanceScanResult(
    /**
     * The [NestedProvenance] which the [scanResults] belong to.
     */
    val nestedProvenance: NestedProvenance,

    /**
     * A map of [KnownProvenance]s from [nestedProvenance] associated with lists of [ScanResult]s.
     */
    val scanResults: Map<KnownProvenance, List<ScanResult>>,
) {
    /**
     * Return a set of all [KnownProvenance]s contained in [nestedProvenance].
     */
    fun getProvenances(): Set<KnownProvenance> = nestedProvenance.getProvenances()

    /**
     * Return true if [scanResults] contains at least one scan result for each of the [KnownProvenance]s contained in
     * [nestedProvenance].
     */
    fun isComplete(): Boolean = getProvenances().all { scanResults[it]?.isNotEmpty() == true }

    /**
     * Filter the contained [ScanResult]s using the [predicate].
     */
    fun filter(predicate: (ScanResult) -> Boolean): NestedProvenanceScanResult =
        NestedProvenanceScanResult(
            nestedProvenance,
            scanResults.mapValues { it.value.filter(predicate) }
        )

    /**
     * Merge the nested [ScanResult]s into one [ScanResult] per used scanner, using the root of the [nestedProvenance]
     * as provenance. This is used to transform this class into the format currently used by [OrtResult].
     * When merging multiple [ScanSummary]s the earliest start time will be used as the new start time, and the latest
     * end time will be used as the end time. Because the [ScanSummary] does not contain the checksums of the individual
     * files, no package verification code can be calculated.
     */
    fun merge(): List<ScanResult> {
        val allScanners = scanResults.values.flatMapTo(mutableSetOf()) { results -> results.map { it.scanner } }

        return allScanners.map { scanner ->
            val scanResultsForScanner = scanResults.mapValues { (_, results) ->
                results.filter { it.scanner == scanner }
            }

            val allScanResults = scanResults.values.flatten()

            val startTime = allScanResults.minByOrNull { it.summary.startTime }?.summary?.startTime ?: Instant.now()
            val endTime = allScanResults.maxByOrNull { it.summary.endTime }?.summary?.endTime ?: startTime
            val issues = allScanResults.flatMap { it.summary.issues }

            val licenseFindings = scanResultsForScanner.mergeLicenseFindings()
            val copyrightFindings = scanResultsForScanner.mergeCopyrightFindings()

            ScanResult(
                provenance = nestedProvenance.root,
                scanner = scanner,
                summary = ScanSummary(
                    startTime = startTime,
                    endTime = endTime,
                    packageVerificationCode = "",
                    licenseFindings = licenseFindings,
                    copyrightFindings = copyrightFindings,
                    issues = issues
                )
            )
        }
    }

    private fun Map<KnownProvenance, List<ScanResult>>.mergeLicenseFindings(): SortedSet<LicenseFinding> {
        val findingsByPath = mapKeys { getPath(it.key) }.mapValues { (_, scanResults) ->
            scanResults.flatMap { it.summary.licenseFindings }
        }

        val findings = findingsByPath.flatMapTo(sortedSetOf()) { (path, findings) ->
            val prefix = if (path.isEmpty()) path else "$path/"
            findings.map { it.copy(location = it.location.copy(path = "$prefix${it.location.path}")) }
        }

        return findings
    }

    private fun Map<KnownProvenance, List<ScanResult>>.mergeCopyrightFindings(): SortedSet<CopyrightFinding> {
        val findingsByPath = mapKeys { getPath(it.key) }.mapValues { (_, scanResults) ->
            scanResults.flatMap { it.summary.copyrightFindings }
        }

        val findings = findingsByPath.flatMapTo(sortedSetOf()) { (path, findings) ->
            val prefix = if (path.isEmpty()) path else "$path/"
            findings.map { it.copy(location = it.location.copy(path = "$prefix${it.location.path}")) }
        }

        return findings
    }

    private fun getPath(provenance: KnownProvenance): String {
        if (provenance == nestedProvenance.root) return ""

        nestedProvenance.subRepositories.forEach { if (provenance == it.value) return it.key }

        throw IllegalArgumentException("Could not find entry for $provenance.")
    }
}
