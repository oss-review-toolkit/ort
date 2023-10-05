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

package org.ossreviewtoolkit.scanner.provenance

import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.utils.mergeScanResultsByScanner

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
    val scanResults: Map<KnownProvenance, List<ScanResult>>
) {
    /**
     * Return true if [scanResults] contains at least one scan result for each of the [KnownProvenance]s contained in
     * [nestedProvenance].
     */
    fun isComplete(): Boolean = nestedProvenance.allProvenances.all { scanResults[it]?.isNotEmpty() == true }

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
     * When merging multiple [ScanSummary]s for a particular scanner the earliest start time and lasted end time will
     * be used as the new values for the respective scanner.
     */
    fun merge(): List<ScanResult> {
        val scanResultsByPath = scanResults.mapKeys { (provenance, _) -> getPath(provenance) }

        return mergeScanResultsByScanner(scanResultsByPath, nestedProvenance.root)
    }

    private fun getPath(provenance: KnownProvenance) = nestedProvenance.getPath(provenance)

    /**
     * Remove all scan results for [nestedProvenance]s which are not within the provided [path] and filter all findings
     * within the scan results by [path], taking the sub repository paths into account.
     * This also updates the VCS paths for all [RepositoryProvenance]s accordingly to make clear that they are not
     * results for the whole repository.
     *
     * Throws an [IllegalArgumentException] if any provenance in [nestedProvenance] already has a VCS path set.
     */
    fun filterByVcsPath(path: String): NestedProvenanceScanResult {
        if (path.isEmpty()) return this

        val provenancesWithVcsPath = nestedProvenance.allProvenances.filter {
            it is RepositoryProvenance && it.vcsInfo.path.isNotBlank()
        }

        require(provenancesWithVcsPath.isEmpty()) {
            "Cannot filter scan results by VCS path that have a repository provenance with a non-blank VCS path " +
                "because their partial scan result might not contain the path to filter for. The following " +
                "provenances have a non-blank VCS path: ${provenancesWithVcsPath.joinToString("\n") { "\t$it" }}."
        }

        val pathsWithinProvenances = nestedProvenance.allProvenances.filter {
            val provenancePath = getPath(it)
            // Return true if the provenance is on the same branch as the filter path. Otherwise it can be discarded,
            // because all findings would be filtered anyway.
            provenancePath.isEmpty() || provenancePath == path || provenancePath.startsWith("$path/") ||
                path.startsWith("$provenancePath/")
        }.associateWith { provenance ->
            val provenancePath = getPath(provenance)

            // Get the relative path of the filter path inside the provenance.
            when {
                provenancePath.isEmpty() -> path
                path.startsWith("$provenancePath/") -> path.removePrefix("$provenancePath/")
                else -> ""
            }
        }

        fun KnownProvenance.withVcsPath() =
            when (this) {
                is RepositoryProvenance -> {
                    val pathWithinProvenance = pathsWithinProvenances.getValue(this)
                    copy(vcsInfo = vcsInfo.copy(path = pathWithinProvenance))
                }

                else -> this
            }

        // Set the VCS path of all provenances according to the provided path and filter provenances which are not
        // within path.
        val newNestedProvenance = NestedProvenance(
            root = nestedProvenance.root.withVcsPath(),
            subRepositories = nestedProvenance.subRepositories.filterValues { it in pathsWithinProvenances.keys }
                .mapValues { (_, provenance) -> provenance.withVcsPath() as RepositoryProvenance }
        )

        // Filter findings in scan results according to the provided path, filter scan results for provenances which
        // are not within path, and update the scan result provenances.
        val newScanResults = scanResults.filterKeys { it in pathsWithinProvenances.keys }
            .mapValues { (provenance, scanResults) ->
                val pathWithinProvenance = pathsWithinProvenances.getValue(provenance)
                scanResults.map { scanResult ->
                    when (val scanResultProvenance = scanResult.provenance) {
                        is RepositoryProvenance -> {
                            scanResult.filterByPath(pathWithinProvenance)
                                .copy(provenance = scanResultProvenance.withVcsPath())
                        }

                        else -> scanResult
                    }
                }
            }.mapKeys { (provenance, _) ->
                provenance.withVcsPath()
            }

        return NestedProvenanceScanResult(
            nestedProvenance = newNestedProvenance,
            scanResults = newScanResults
        )
    }
}
