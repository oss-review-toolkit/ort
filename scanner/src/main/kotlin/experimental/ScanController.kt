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

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.ScanResult

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
    val scanners: List<ScannerWrapper>
) {
    /**
     * A map of [KnownProvenance]s to their resolved [NestedProvenance]s.
     */
    private val nestedProvenances = mutableMapOf<KnownProvenance, NestedProvenance>()

    /**
     * A map of package [Identifier]s to their resolved [KnownProvenance]s.
     */
    private val packageProvenances = mutableMapOf<Identifier, KnownProvenance>()

    /**
     * The [ScanResult]s for each [KnownProvenance] and [ScannerWrapper].
     */
    private val scanResults = mutableMapOf<ScannerWrapper, MutableMap<KnownProvenance, MutableList<ScanResult>>>()

    /**
     * Add an entry to [packageProvenances], overwriting any existing values.
     */
    fun addPackageProvenance(id: Identifier, provenance: KnownProvenance) {
        packageProvenances[id] = provenance
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
     * Find the [NestedProvenance] for the provided [provenance].
     */
    fun findNestedProvenance(provenance: KnownProvenance): NestedProvenance? = nestedProvenances[provenance]

    /**
     * Find all packages for [provenance].
     */
    fun findPackages(provenance: KnownProvenance) =
        packageProvenances.entries.filter { it.value == provenance }
            .mapNotNull { (id, _) -> packages.find { it.id == id } }

    /**
     * Return all [KnownProvenance]s contained in [nestedProvenances] and [packageProvenances].
     */
    fun getAllProvenances(): Set<KnownProvenance> =
        (packageProvenances.values + nestedProvenances.values.flatMap { it.getProvenances() }).toSet()

    /**
     * Get all provenances for which no scan result for the provided [scanner] is available.
     */
    fun getMissingProvenanceScans(scanner: ScannerWrapper, nestedProvenance: NestedProvenance) =
        nestedProvenance.getProvenances().filter { hasScanResult(scanner, it) }

    /**
     * Get the [NestedProvenance] for the provided [id].
     */
    fun getNestedProvenance(id: Identifier): NestedProvenance =
        nestedProvenances.getValue(packageProvenances.getValue(id))

    /**
     * Get all [NestedProvenance]s by [Package].
     */
    fun getNestedProvenancesByPackage(): Map<Package, NestedProvenance> =
        packageProvenances.keys.associate { id ->
            packages.first { it.id == id } to getNestedProvenance(id)
        }

    /**
     * Get the [NestedProvenanceScanResult] for the provided [id].
     */
    fun getNestedScanResult(id: Identifier): NestedProvenanceScanResult =
        buildNestedProvenanceScanResult(packageProvenances.getValue(id))

    /**
     * Get the [NestedProvenanceScanResult] for each [Package].
     */
    fun getNestedScanResultsByPackage(): Map<Package, NestedProvenanceScanResult> =
        // TODO: Return map containing all packages with issues for packages that could not be completely scanned.
        packageProvenances.entries.associate { (id, provenance) ->
            packages.first { it.id == id } to buildNestedProvenanceScanResult(provenance)
        }

    /**
     * Return all [Package]s for which adding a scan result for [scanner] and [provenance] would complete the scan of
     * their [NestedProvenance] by the respective [scanner].
     */
    fun getPackagesCompletedByProvenance(scanner: ScannerWrapper, provenance: KnownProvenance): List<Package> =
        packageProvenances.entries.filter { (_, packageProvenance) ->
            val nestedProvenance = nestedProvenances[packageProvenance]
            nestedProvenance != null && getMissingProvenanceScans(scanner, nestedProvenance) == listOf(provenance)
        }.map { (id, _) -> packages.first { it.id == id } }

    /**
     * Return all [KnownProvenance]s for the [packages].
     */
    fun getPackageProvenances(): Set<KnownProvenance> = packageProvenances.values.toSet()

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
        getNestedProvenance(pkg.id).getProvenances().all { provenance -> hasScanResult(scanner, provenance) }

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
}
