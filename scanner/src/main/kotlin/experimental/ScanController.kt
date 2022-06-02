/*
 * Copyright (C) 2022 Bosch.IO GmbH
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

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.config.ScannerConfiguration

/**
 * A controller for the data related to a run of the [ExperimentalScanner].
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
     * A map of package [Identifier]s to a list of [OrtIssue]s that occurred during provenance resolution for the
     * respective package.
     */
    private val provenanceResolutionIssues = mutableMapOf<Identifier, MutableList<OrtIssue>>()

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

    fun addProvenanceResolutionIssue(id: Identifier, issue: OrtIssue) {
        provenanceResolutionIssues.getOrPut(id) { mutableListOf() } += issue
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
     * Get all provenances for which no scan result for the provided [scanner] is available.
     */
    fun getMissingProvenanceScans(scanner: ScannerWrapper, nestedProvenance: NestedProvenance) =
        nestedProvenance.getProvenances().filter { hasScanResult(scanner, it) }

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
    fun getNestedScanResult(id: Identifier): NestedProvenanceScanResult =
        buildNestedProvenanceScanResult(packageProvenancesWithoutVcsPath.getValue(id))

    /**
     * Get the [NestedProvenanceScanResult] for each [Package], filtered by the VCS path for each package and the
     * configured [ignore patterns][ScannerConfiguration.ignorePatterns].
     */
    fun getNestedScanResultsByPackage(): Map<Package, NestedProvenanceScanResult> =
        // TODO: Return map containing all packages with issues for packages that could not be completely scanned.
        packageProvenancesWithoutVcsPath.entries.associate { (id, provenance) ->
            packages.first { it.id == id } to buildNestedProvenanceScanResult(provenance)
        }.filterByVcsPath().filterByIgnorePatterns()

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
     */
    fun getPackagesConsolidatedByProvenance(): Map<KnownProvenance, List<Package>> {
        val packagesByProvenance = mutableMapOf<KnownProvenance, MutableList<Package>>()

        packages.forEach { pkg ->
            val consolidatedProvenance = when (val provenance = packageProvenances[pkg.id]) {
                null -> return@forEach
                is RepositoryProvenance -> provenance.copy(vcsInfo = provenance.vcsInfo.copy(path = ""))
                else -> provenance
            }

            packagesByProvenance.getOrPut(consolidatedProvenance) { mutableListOf() } += pkg
        }

        return packagesByProvenance
    }

    /**
     * Return all package [Identifier]s for the provided [provenance].
     */
    fun getPackagesForProvenanceWithoutVcsPath(provenance: KnownProvenance): Set<Identifier> =
        packageProvenancesWithoutVcsPath.filter { (_, packageProvenance) -> packageProvenance == provenance }.keys

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
    fun getResultsForProvenanceResolutionIssues(): Map<Identifier, List<ScanResult>> =
        provenanceResolutionIssues.mapValues { (_, issues) ->
            scanners.map { scanner ->
                ScanResult(
                    provenance = UnknownProvenance,
                    scanner = scanner.details,
                    summary = ScanSummary(
                        startTime = Instant.now(),
                        endTime = Instant.now(),
                        packageVerificationCode = "",
                        licenseFindings = sortedSetOf(),
                        copyrightFindings = sortedSetOf(),
                        issues = issues
                    )
                )
            }
        }

    /**
     * Get all [ScanResult]s for the provided [provenance].
     */
    fun getScanResults(provenance: KnownProvenance): List<ScanResult> =
        scanResults.values.flatMap { scanResultsByProvenance -> scanResultsByProvenance[provenance].orEmpty() }

    /**
     * Get all [ScanResult]s for the provided [scanner].
     */
    fun getScanResults(scanner: ScannerWrapper): Map<KnownProvenance, List<ScanResult>> = scanResults[scanner].orEmpty()

    /**
     * Get all [ScanResult]s for the provided [scanner] and [provenance].
     */
    fun getScanResults(scanner: ScannerWrapper, provenance: KnownProvenance): List<ScanResult> =
        scanResults[scanner]?.get(provenance).orEmpty()

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

    private fun buildNestedProvenanceScanResult(root: KnownProvenance): NestedProvenanceScanResult {
        val nestedProvenance = nestedProvenances.getValue(root)
        val scanResults = nestedProvenance.getProvenances().associateWith { provenance ->
            getScanResults(provenance)
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
