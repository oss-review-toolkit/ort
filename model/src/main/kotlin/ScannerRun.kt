/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.annotation.JsonSerialize

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.utils.FileListSortedSetConverter
import org.ossreviewtoolkit.model.utils.ProvenanceResolutionResultSortedSetConverter
import org.ossreviewtoolkit.model.utils.ScanResultSortedSetConverter
import org.ossreviewtoolkit.model.utils.getKnownProvenancesWithoutVcsPath
import org.ossreviewtoolkit.model.utils.mergeScanResultsByScanner
import org.ossreviewtoolkit.model.utils.prependPath
import org.ossreviewtoolkit.model.utils.vcsPath
import org.ossreviewtoolkit.utils.common.getDuplicates
import org.ossreviewtoolkit.utils.ort.Environment

/**
 * The summary of a single run of the scanner.
 */
data class ScannerRun(
    /**
     * The [Instant] the scanner was started.
     */
    val startTime: Instant,

    /**
     * The [Instant] the scanner has finished.
     */
    val endTime: Instant,

    /**
     * The [Environment] in which the scanner was executed.
     */
    val environment: Environment,

    /**
     * The [ScannerConfiguration] used for this run.
     */
    val config: ScannerConfiguration,

    /**
     * The results of the provenance resolution for all projects and packages.
     */
    @JsonSerialize(converter = ProvenanceResolutionResultSortedSetConverter::class)
    val provenances: Set<ProvenanceResolutionResult>,

    /**
     * The scan results for each resolved provenance.
     */
    @JsonSerialize(converter = ScanResultSortedSetConverter::class)
    val scanResults: Set<ScanResult>,

    /**
     * The list of files for each resolved provenance.
     */
    @JsonSerialize(converter = FileListSortedSetConverter::class)
    val files: Set<FileList>
) {
    companion object {
        /**
         * A constant for a [ScannerRun] where all properties are empty.
         */
        @JvmField
        val EMPTY = ScannerRun(
            startTime = Instant.EPOCH,
            endTime = Instant.EPOCH,
            environment = Environment(),
            config = ScannerConfiguration(),
            provenances = emptySet(),
            scanResults = emptySet(),
            files = emptySet()
        )
    }

    init {
        scanResults.forEach { scanResult ->
            require(scanResult.provenance is KnownProvenance) {
                "Found a scan result with an unknown provenance, which is not allowed."
            }

            (scanResult.provenance as? RepositoryProvenance)?.let { repositoryProvenance ->
                require(repositoryProvenance.vcsInfo.path.isEmpty()) {
                    "Found a scan result with a non-empty VCS path, which is not allowed."
                }

                require(repositoryProvenance.vcsInfo.revision == repositoryProvenance.resolvedRevision) {
                    "The revision and resolved revision of a scan result are not equal, which is not allowed."
                }
            }
        }

        provenances.getDuplicates { it.id }.keys.let { idsForDuplicateProvenanceResolutionResults ->
            require(idsForDuplicateProvenanceResolutionResults.isEmpty()) {
                "Found multiple provenance resolution results for the following ids: " +
                        "${idsForDuplicateProvenanceResolutionResults.joinToString { it.toCoordinates() }}."
            }
        }

        val scannedProvenances = scanResults.mapTo(mutableSetOf()) { it.provenance }
        val resolvedProvenances = provenances.flatMapTo(mutableSetOf()) {
            it.getKnownProvenancesWithoutVcsPath().values
        }

        (scannedProvenances - resolvedProvenances).let {
            require(it.isEmpty()) {
                "Found scan results which do not correspond to any resolved provenances, which is not allowed: \n" +
                        it.toYaml()
            }
        }

        val fileListProvenances = files.mapTo(mutableSetOf()) { it.provenance }
        (fileListProvenances - resolvedProvenances).let {
            require(it.isEmpty()) {
                "Found a file lists which do not correspond to any resolved provenances, which is not allowed: \n" +
                        it.toYaml()
            }
        }

        files.forEach { fileList ->
            (fileList.provenance as? RepositoryProvenance)?.let {
                require(it.vcsInfo.path.isEmpty()) {
                    "Found a file list with a non-empty VCS path, which is not allowed."
                }

                require(it.vcsInfo.revision == it.resolvedRevision) {
                    "The revision and resolved revision of a file list are not equal, which is not allowed."
                }
            }
        }
    }

    private val provenancesById: Map<Identifier, ProvenanceResolutionResult> by lazy {
        provenances.associateBy { it.id }
    }

    private val scanResultsByProvenance: Map<KnownProvenance, List<ScanResult>> by lazy {
        scanResults.groupBy { it.provenance as KnownProvenance }
    }

    private val scanResultsById: Map<Identifier, List<ScanResult>> by lazy {
        provenances.map { it.id }.associateWith { id -> getMergedResultsForId(id) }
    }

    private val fileListByProvenance: Map<KnownProvenance, FileList> by lazy {
        files.associateBy { it.provenance }
    }

    private val fileListById: Map<Identifier, FileList> by lazy {
        provenances.mapNotNull {
            getMergedFileListForId(it.id)?.let { fileList ->
                it.id to fileList
            }
        }.toMap()
    }

    /**
     * Return all scan results related to [id] with the internal sub-repository scan results merged into the root
     * repository scan results. ScanResults for different scanners are not merged, so that the output contains exactly
     * one scan result per scanner.
     */
    private fun getMergedResultsForId(id: Identifier): List<ScanResult> {
        // Algorithm:
        // 1. If package provenance could not be resolved, create a scan result with the resolution issue.
        // 2. If nested provenance could not be resolved, take the scan results for the package provenance and each
        //    scanner and add the resolution issue.
        // 3. Else, merge the scan results for each scanner based on the nested provenance of the package.
        val resolutionResult = provenancesById.getValue(id)

        resolutionResult.packageProvenanceResolutionIssue?.let {
            return listOf(scanResultForProvenanceResolutionIssue(resolutionResult.packageProvenance, it))
        }

        val packageProvenance = resolutionResult.packageProvenance!!

        val scanResultsByPath = resolutionResult.getKnownProvenancesWithoutVcsPath().mapValues { (_, provenance) ->
            scanResultsByProvenance[provenance].orEmpty()
        }

        val scanResults = mergeScanResultsByScanner(scanResultsByPath).map { scanResult ->
            scanResult.filterByPath(packageProvenance.vcsPath).filterByIgnorePatterns(config.ignorePatterns)
        }.map { scanResult ->
            // The VCS revision of scan result is equal to the resolved revision. So, use the package provenance
            // to re-align the VCS revision with the package's metadata.
            scanResult.copy(
                provenance = packageProvenance,
                summary = scanResult.summary.addIssue(resolutionResult.nestedProvenanceResolutionIssue)
            )
        }

        return scanResults.takeIf { it.isNotEmpty() }
            ?: resolutionResult.nestedProvenanceResolutionIssue?.let { issue ->
                listOf(scanResultForProvenanceResolutionIssue(packageProvenance, issue))
            }.orEmpty()
    }

    private fun getMergedFileListForId(id: Identifier): FileList? {
        val resolutionResult = provenancesById[id]?.takeIf {
            it.packageProvenanceResolutionIssue == null && it.nestedProvenanceResolutionIssue == null
        } ?: return null

        val packageProvenance = resolutionResult.packageProvenance!!

        val fileListsByPath = resolutionResult.getKnownProvenancesWithoutVcsPath().mapValues { (_, provenance) ->
            // If there was an issue creating at least one file list, then return null instead of an incomplete file
            // list.
            fileListByProvenance[provenance] ?: return null
        }

        return mergeFileLists(fileListsByPath)
            .filterByVcsPath(packageProvenance.vcsPath)
            .copy(provenance = packageProvenance)
    }

    @JsonIgnore
    fun getAllScanResults(): Map<Identifier, List<ScanResult>> = scanResultsById

    fun getScanResults(id: Identifier): List<ScanResult> = scanResultsById[id].orEmpty()

    @JsonIgnore
    fun getAllFileLists(): Map<Identifier, FileList> = fileListById

    fun getFileList(id: Identifier): FileList? = fileListById[id]

    @JsonIgnore
    fun getIssues(): Map<Identifier, Set<Issue>> =
        scanResultsById.mapValues { (_, scanResults) ->
            scanResults.flatMapTo(mutableSetOf()) { it.summary.issues }
        }
}

private fun scanResultForProvenanceResolutionIssue(packageProvenance: KnownProvenance?, issue: Issue): ScanResult =
    ScanResult(
        packageProvenance ?: UnknownProvenance,
        scanner = ScannerDetails(name = "ProvenanceResolver", version = "", configuration = ""),
        summary = ScanSummary.EMPTY.copy(
            issues = listOf(issue)
        )
    )

private fun ScanSummary.addIssue(issue: Issue?): ScanSummary =
    if (issue == null) this else copy(issues = (issues + issue).distinct())

private fun mergeFileLists(fileListByPath: Map<String, FileList>): FileList {
    val provenance = requireNotNull(fileListByPath[""]) {
        "There must be a file list associated with the root path."
    }.provenance

    val files = fileListByPath.flatMapTo(mutableSetOf()) { (path, fileList) ->
        fileList.files.map { fileEntry ->
            fileEntry.copy(path = fileEntry.path.prependPath(path))
        }
    }

    return FileList(provenance, files)
}

private fun FileList.filterByVcsPath(path: String): FileList {
    if (path.isBlank()) return this

    require(provenance is RepositoryProvenance) {
        "Expected a repository provenance but got a ${provenance.javaClass.simpleName}."
    }

    val provenance = provenance.copy(vcsInfo = provenance.vcsInfo.copy(path = path))

    // Do not keep files outside the VCS path in contrast to ScanSummary.filterByVcsPath().
    val files = files.filterTo(mutableSetOf()) { fileEntry ->
        File(fileEntry.path).startsWith(path)
    }

    return FileList(provenance, files)
}
