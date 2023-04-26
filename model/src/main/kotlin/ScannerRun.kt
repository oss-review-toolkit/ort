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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

import java.io.File
import java.time.Instant
import java.util.LinkedList

import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.utils.mergeScanResultsByScanner
import org.ossreviewtoolkit.utils.ort.Environment

/**
 * The summary of a single run of the scanner.
 */
@JsonIgnoreProperties(value = ["has_issues", "storage_stats"], allowGetters = true)
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
     * The resolved provenances for all projects and packages.
     * These may have non-empty VCS paths.
     */
    val provenances: List<ProvenanceResolutionResult>,

    /**
     * The direct sub-repositories corresponding to the resolved provenances.
     */
    val nestedProvenances: List<NestedProvenanceResolutionResult>,

    /**
     * The scan results for each resolved provenance.
     */
    val scanResults: List<ScanResult>
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
            provenances = emptyList(),
            nestedProvenances = emptyList(),
            scanResults = emptyList()
        )
    }

    private val recursiveProvenancesById: Map<Identifier, RecursiveProvenance> by lazy {
        provenances.map { it.id }.associateWith { id ->
            val root = provenances.single { it.id == id }

            if (root.provenance !is RepositoryProvenance || root.issue != null) {
                return@associateWith RecursiveProvenance(
                    root = root.provenance, // TODO: Clear VCS path?
                    subRepositories = emptyMap(),
                    provenanceResolutionIssues = setOfNotNull(root.issue)
                )
            }

            val queue = LinkedList<Pair<String, RepositoryProvenance>>().apply {
                this += "" to root.provenance.clearVcsPath()
            }

            val subRepositories = mutableMapOf<String, RepositoryProvenance>()
            val provenanceResolutionIssues = mutableSetOf<Issue>()

            while (queue.isNotEmpty()) {
                val (parentPath, parentProvenance) = queue.removeFirst()
                val nestedProvenanceResolutionResult = nestedProvenances.single { it.provenance == parentProvenance }

                if (nestedProvenanceResolutionResult.issue != null) {
                    provenanceResolutionIssues += nestedProvenanceResolutionResult.issue
                    continue
                }

                nestedProvenanceResolutionResult.nestedProvenance.forEach { (childPath, childProvenance) ->
                    val entry = "$parentPath/$childPath" to childProvenance

                    subRepositories += entry
                    queue += entry
                }
            }

            RecursiveProvenance(
                root = root.provenance,
                subRepositories = subRepositories.filter { (path, _) ->
                    File(root.provenance.vcsInfo.path).startsWith(File(path))
                },
                provenanceResolutionIssues = provenanceResolutionIssues
            )
        }
    }

    private val scanResultsByProvenance: Map<KnownProvenance, List<ScanResult>> by lazy {
        scanResults.groupBy { it.provenance as KnownProvenance }
    }

    private val scanResultsById: Map<Identifier, List<ScanResult>> by lazy {
        provenances.map { it.id }.associateWith { id ->
            val scannedProvenance = recursiveProvenancesById.getValue(id).takeIf { it.root is KnownProvenance }
                ?: return@associateWith emptyList()

            val scanResultsByPath = scannedProvenance.getKnownProvenancesWithoutVcsPath().mapValues { (_, provenance) ->
                scanResultsByProvenance[provenance].orEmpty()
            }

            // TODO: Pass provenance resolution issues from scannedProvenance.
            val vcsPath = (scannedProvenance.root as? RepositoryProvenance)?.vcsInfo?.path.orEmpty()

            mergeScanResultsByScanner(scanResultsByPath).map {
                it.filterByPath(vcsPath).filterByIgnorePatterns(config.ignorePatterns)
            }
        }
    }

    fun getScanResults(): Map<Identifier, List<ScanResult>> = scanResultsById

    fun getScanResults(id: Identifier): List<ScanResult> = scanResultsById[id].orEmpty()

    fun getIssues(): Map<Identifier, Set<Issue>> =
        scanResultsById.mapValues { (_, scanResults) ->
            scanResults.flatMapTo(mutableSetOf()) { it.summary.issues }
        }
}

private data class RecursiveProvenance(
    val root: Provenance,
    val subRepositories: Map<String, RepositoryProvenance>,
    val provenanceResolutionIssues: Set<Issue>
) {
    fun getKnownProvenancesWithoutVcsPath(): Map<String, KnownProvenance> =
        buildMap {
            when (root) {
                is RepositoryProvenance -> put("", root.clearVcsPath())
                is ArtifactProvenance -> put("", root)
                else -> { }
            }
            putAll(subRepositories)
        }
}

private fun RepositoryProvenance.clearVcsPath() = copy(vcsInfo = vcsInfo.copy(path = ""))
