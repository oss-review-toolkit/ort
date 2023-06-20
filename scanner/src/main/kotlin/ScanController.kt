/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.scanner

import java.time.Instant

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.scanner.provenance.NestedProvenance
import org.ossreviewtoolkit.scanner.provenance.NestedProvenanceScanResult
import org.ossreviewtoolkit.utils.common.PATH_STRING_COMPARATOR

/**
 * A controller for the data related to a run of the [Scanner].
 */
@Suppress("TooManyFunctions")
class ScanController(
    /**
     * The set of [Package]s to be scanned.
     */
    val packages: Set<Package>,

    /**
     * The list of [ScannerWrapper]s used for scanning.
     */
    val scanners: List<ScannerWrapper>,

    /**
     * The [ScannerConfiguration].
     */
    val config: ScannerConfiguration
) {
    /**
     * A map of package [Identifier]s to a list of [Issue]s that occurred during provenance resolution for the
     * respective package.
     */
    private val packageProvenanceResolutionIssues = mutableMapOf<Identifier, Issue>()

    /**
     * A map of package [KnownProvenance]s to a list of [Issue]s that occurred during provenance resolution for the
     * respective package.
     */
    private val nestedProvenanceResolutionIssues = mutableMapOf<KnownProvenance, Issue>()

    /**
     * A map of [Identifier]s associated with a list of [Issue]s that occurred during a scan besides the issues
     * created by the scanners themselves as part of the [ScanSummary].
     */
    private val issues = mutableMapOf<Identifier, MutableList<Issue>>()

    /**
     * A map of [KnownProvenance]s to their resolved [NestedProvenance]s.
     */
    private val nestedProvenances = mutableMapOf<KnownProvenance, NestedProvenance>()

    /**
     * A map of package [Identifier]s to their resolved [KnownProvenance]s. These provenances are used to filter the
     * scan results for a package based on the VCS path.
     */
    private val packageProvenances = mutableMapOf<Identifier, KnownProvenance>()

    /**
     * A map of package [Identifier]s to their resolved [KnownProvenance]s with the VCS path removed. These provenances
     * are used during scanning to make sure that always the full repositories are scanned.
     */
    private val packageProvenancesWithoutVcsPath = mutableMapOf<Identifier, KnownProvenance>()

    /**
     * The [ScanResult]s for each [KnownProvenance] and [ScannerWrapper].
     */
    private val scanResults = mutableMapOf<ScannerWrapper, MutableMap<KnownProvenance, MutableList<ScanResult>>>()

    /**
     * Set the [issue] which failed package provenance resolution for the package denoted by [id].
     */
    fun putPackageProvenanceResolutionIssue(id: Identifier, issue: Issue) {
        packageProvenanceResolutionIssues[id] = issue
    }

    /**
     * Set the [issue] which failed nested provenance resolution for the given [provenance].
     */
    fun putNestedProvenanceResolutionIssue(provenance: KnownProvenance, issue: Issue) {
        nestedProvenanceResolutionIssues[provenance] = issue
    }

    fun addIssue(id: Identifier, issue: Issue) {
        issues.getOrPut(id) { mutableListOf() } += issue
    }

    /**
     * Add an entry to [packageProvenances] and [packageProvenancesWithoutVcsPath], overwriting any existing values.
     */
    fun addPackageProvenance(id: Identifier, provenance: KnownProvenance) {
        packageProvenances[id] = provenance
        packageProvenancesWithoutVcsPath[id] = when (provenance) {
            is RepositoryProvenance -> provenance.copy(vcsInfo = provenance.vcsInfo.copy(path = ""))
            else -> provenance
        }
    }

    /**
     * Add an entry to [nestedProvenances], overwriting any existing values.
     */
    fun addNestedProvenance(root: KnownProvenance, nestedProvenance: NestedProvenance) {
        nestedProvenances[root] = nestedProvenance
    }

    /**
     * Add all scan results contained in the nested [result].
     */
    fun addNestedScanResult(scanner: ScannerWrapper, result: NestedProvenanceScanResult) {
        result.scanResults.forEach { (provenance, results) ->
            addScanResults(scanner, provenance, results)
        }
    }

    /**
     * Add all [results].
     */
    fun addScanResults(scanner: ScannerWrapper, provenance: KnownProvenance, results: List<ScanResult>) {
        scanResults.getOrPut(scanner) { mutableMapOf() }.getOrPut(provenance) { mutableListOf() }.addAll(results)
    }

    /**
     * Find the [NestedProvenance] for the provided [id].
     */
    fun findNestedProvenance(id: Identifier): NestedProvenance? =
        nestedProvenances[packageProvenancesWithoutVcsPath[id]]

    /**
     * Return all [KnownProvenance]s contained in [nestedProvenances].
     */
    fun getAllProvenances(): Set<KnownProvenance> =
        nestedProvenances.values.flatMapTo(mutableSetOf()) { it.getProvenances() }

    /**
     * Return all scan results.
     */
    fun getAllScanResults(): List<ScanResult> =
        scanResults.values.flatMap { scanResultsByProvenance -> scanResultsByProvenance.values.flatten() }

    /**
     * Return all provenances including sub-repositories associated with the identifiers of the packages they belong to.
     */
    fun getIdsByProvenance(): Map<KnownProvenance, Set<Identifier>> =
        buildMap<_, MutableSet<Identifier>> {
            getNestedProvenancesByPackage().forEach { (pkg, nestedProvenance) ->
                nestedProvenance.getProvenances().forEach { provenance ->
                    getOrPut(provenance) { mutableSetOf() } += pkg.id
                }
            }
        }

    /**
     * Get all provenances for which no scan result for the provided [scanner] is available.
     */
    private fun getMissingProvenanceScans(scanner: ScannerWrapper, nestedProvenance: NestedProvenance) =
        nestedProvenance.getProvenances().filter { hasScanResult(scanner, it) }

    /**
     * Return the nested provenance resolution issues associated with the given [provenance].
     */
    fun getNestedProvenanceResolutionIssue(provenance: KnownProvenance): Issue? =
        nestedProvenanceResolutionIssues[provenance]

    /**
     * Get the [NestedProvenance] for the provided [id], or null if no nested provenance for the [id] is available.
     */
    fun getNestedProvenance(id: Identifier): NestedProvenance? =
        packageProvenancesWithoutVcsPath[id]?.let { nestedProvenances[it] }

    /**
     * Get all [NestedProvenance]s by [Package].
     */
    fun getNestedProvenancesByPackage(): Map<Package, NestedProvenance> =
        buildMap {
            packageProvenancesWithoutVcsPath.forEach { (id, _) ->
                getNestedProvenance(id)?.let { nestedProvenance ->
                    put(packages.first { it.id == id }, nestedProvenance)
                }
            }
        }

    /**
     * Get the [NestedProvenanceScanResult] for the provided [id].
     */
    fun getNestedScanResult(id: Identifier): NestedProvenanceScanResult? =
        buildNestedProvenanceScanResult(packageProvenancesWithoutVcsPath.getValue(id), emptyList())

    /**
     * Get the [NestedProvenanceScanResult] for each [Package], filtered by the VCS path for each package and the
     * configured [ignore patterns][ScannerConfiguration.ignorePatterns].
     */
    fun getNestedScanResultsByPackage(): Map<Package, NestedProvenanceScanResult> =
        packageProvenancesWithoutVcsPath.mapNotNull { (id, provenance) ->
            val issues = issues[id].orEmpty()
            buildNestedProvenanceScanResult(provenance, issues)?.let { scanResult ->
                packages.first { it.id == id } to scanResult
            }
        }.toMap().filterByVcsPath().filterByIgnorePatterns()

    /**
     * Return all [Package]s for which adding a scan result for [scanner] and [provenance] would complete the scan of
     * their [NestedProvenance] by the respective [scanner].
     */
    fun getPackagesCompletedByProvenance(scanner: ScannerWrapper, provenance: KnownProvenance): List<Package> =
        packageProvenancesWithoutVcsPath.entries.filter { (_, packageProvenance) ->
            val nestedProvenance = nestedProvenances[packageProvenance]
            nestedProvenance != null && getMissingProvenanceScans(scanner, nestedProvenance) == listOf(provenance)
        }.map { (id, _) -> packages.first { it.id == id } }

    /**
     * Return a map of [KnownProvenance]s associated with all [packages] with the same provenance, ignoring any VCS
     * path. Packages without a resolved provenance are not included in the result.
     * For a given provenance, the packages are sorted by the ascending amount of path separators '/' in their names,
     * then by their identifiers. For instance, a package with name 'conanfile.txt' will come before one with name
     * 'components/conanfile.txt'. This is because scanner interfaces receive packages as input, and this aims at
     * providing a deterministic ordering when choosing a reference package for packages with the same provenance.
     */
    fun getPackagesConsolidatedByProvenance(): Map<KnownProvenance, List<Package>> {
        val packagesByProvenance = mutableMapOf<KnownProvenance, MutableSet<Package>>()
        val comparator = compareBy<Package, String>(PATH_STRING_COMPARATOR) { it.id.name }.thenBy { it.id }

        packages.forEach { pkg ->
            val consolidatedProvenance = when (val provenance = packageProvenances[pkg.id]) {
                null -> return@forEach
                is RepositoryProvenance -> provenance.copy(vcsInfo = provenance.vcsInfo.copy(path = ""))
                else -> provenance
            }

            packagesByProvenance.getOrPut(consolidatedProvenance) { sortedSetOf(comparator) } += pkg
        }

        return packagesByProvenance.mapValues { it.value.toList() }
    }

    /**
     * Return all package [Identifier]s for the provided [provenance].
     */
    fun getPackagesForProvenanceWithoutVcsPath(provenance: KnownProvenance): Set<Identifier> =
        packageProvenancesWithoutVcsPath.filter { (_, packageProvenance) -> packageProvenance == provenance }.keys

    fun getPackageProvenance(id: Identifier): KnownProvenance? = packageProvenances[id]

    /**
     * Return the package provenanceResolutionIssue associated with the given [id].
     */
    fun getPackageProvenanceResolutionIssue(id: Identifier): Issue? = packageProvenanceResolutionIssues[id]

    /**
     * Return all [KnownProvenance]s for the [packages] with the VCS path removed.
     */
    fun getPackageProvenancesWithoutVcsPath(): Set<KnownProvenance> = packageProvenancesWithoutVcsPath.values.toSet()

    /**
     * Return all [PackageScannerWrapper]s.
     */
    fun getPackageScanners(): List<PackageScannerWrapper> = scanners.filterIsInstance<PackageScannerWrapper>()

    /**
     * Return all [PathScannerWrapper]s.
     */
    fun getPathScanners(): List<PathScannerWrapper> = scanners.filterIsInstance<PathScannerWrapper>()

    /**
     * Return all [ProvenanceScannerWrapper]s.
     */
    fun getProvenanceScanners(): List<ProvenanceScannerWrapper> = scanners.filterIsInstance<ProvenanceScannerWrapper>()

    /**
     * Return scan results for provenance resolution issues.
     */
    fun getResultsForProvenanceResolutionIssues(): Map<Identifier, List<ScanResult>> {
        val idsForProvenance = packageProvenances.entries.groupBy({ (_, provenance) -> provenance }) { (id, _) -> id }

        val issuesForId = buildMap<Identifier, MutableList<Issue>> {
            packageProvenanceResolutionIssues.forEach { (id, issue) ->
                getOrPut(id) { mutableListOf() } += issue
            }

            nestedProvenanceResolutionIssues.forEach { (provenance, issue) ->
                idsForProvenance[provenance].orEmpty().forEach { id ->
                    getOrPut(id) { mutableListOf() } += issue
                }
            }
        }

        return issuesForId.mapValues { (_, issues) ->
            scanners.map { scanner ->
                ScanResult(
                    provenance = UnknownProvenance,
                    scanner = scanner.details,
                    summary = ScanSummary(
                        startTime = Instant.now(),
                        endTime = Instant.now(),
                        packageVerificationCode = "",
                        issues = issues
                    )
                )
            }
        }
    }

    /**
     * Get all [ScanResult]s for the provided [provenance].
     */
    private fun getScanResults(provenance: KnownProvenance): List<ScanResult> =
        scanResults.values.flatMap { scanResultsByProvenance -> scanResultsByProvenance[provenance].orEmpty() }

    /**
     * Get all [ScanResult]s for the provided [scanner].
     */
    fun getScanResults(scanner: ScannerWrapper): Map<KnownProvenance, List<ScanResult>> = scanResults[scanner].orEmpty()

    /**
     * Return true if [ScanResult]s for the complete [NestedProvenance] of the package are available.
     */
    fun hasCompleteScanResult(scanner: ScannerWrapper, pkg: Package): Boolean =
        getNestedProvenance(pkg.id)?.getProvenances()?.all { provenance -> hasScanResult(scanner, provenance) } ?: false

    /**
     * Return true if a [ScanResult] for the provided [scanner] and [provenance] is available.
     */
    fun hasScanResult(scanner: ScannerWrapper, provenance: Provenance) =
        scanResults[scanner]?.get(provenance)?.isNotEmpty() == true

    private fun buildNestedProvenanceScanResult(
        root: KnownProvenance,
        issues: List<Issue>
    ): NestedProvenanceScanResult? {
        val nestedProvenance = nestedProvenances[root] ?: return null

        val scanResults = nestedProvenance.getProvenances().associateWith { provenance ->
            getScanResults(provenance).map { it.copy(summary = it.summary.copy(issues = it.summary.issues + issues)) }
        }

        return NestedProvenanceScanResult(nestedProvenance, scanResults)
    }

    private fun Map<Package, NestedProvenanceScanResult>.filterByIgnorePatterns():
            Map<Package, NestedProvenanceScanResult> =
        mapValues { (_, nestedProvenanceScanResult) ->
            nestedProvenanceScanResult.filterByIgnorePatterns(config.ignorePatterns)
        }

    private fun Map<Package, NestedProvenanceScanResult>.filterByVcsPath(): Map<Package, NestedProvenanceScanResult> =
        mapValues { (pkg, nestedProvenanceScanResult) ->
            val path = (packageProvenances.getValue(pkg.id) as? RepositoryProvenance)?.vcsInfo?.path.orEmpty()
            nestedProvenanceScanResult.filterByVcsPath(path)
        }
}
