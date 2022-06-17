/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
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

package org.ossreviewtoolkit.scanner

import java.lang.IllegalArgumentException
import java.time.Instant
import java.util.ServiceLoader

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

import org.ossreviewtoolkit.downloader.consolidateProjectPackagesByVcs
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ScanRecord
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.Options
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.utils.filterByProject
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.ort.log

const val TOOL_NAME = "scanner"

private fun removeConcludedPackages(packages: Set<Package>, scanner: Scanner): Set<Package> =
    packages.takeUnless { scanner.scannerConfig.skipConcluded }
        // Remove all packages that have a concluded license and authors set.
        ?: packages.partition { it.concludedLicense != null && it.authors.isNotEmpty() }.let { (skip, keep) ->
            if (skip.isNotEmpty()) {
                scanner.log.debug { "Not scanning the following packages with concluded licenses: $skip" }
            }

            keep.toSet()
        }

/**
 * Use the [scanner] to scan the [Project]s and [Package]s specified in the [ortResult].  If [skipExcluded] is true,
 * packages for which excludes are defined are not scanned. Return scan results as an [OrtResult].
 */
@JvmOverloads
fun scanOrtResult(
    scanner: Scanner,
    ortResult: OrtResult,
    skipExcluded: Boolean = false
) = scanOrtResult(scanner, scanner, ortResult, skipExcluded)

/**
 * Use the [packageScanner] and / or [projectScanner] to scan the [Package]s and [Project]s specified in the
 * [ortResult]. If specified, scanners are expected to refer to the same global scanner configuration. If a scanner is
 * null, scanning of the respective entities is skipped. If [skipExcluded] is true, packages for which excludes are
 * defined are not scanned. Return scan results as an [OrtResult].
 */
@JvmOverloads
fun scanOrtResult(
    packageScanner: Scanner?,
    projectScanner: Scanner?,
    ortResult: OrtResult,
    skipExcluded: Boolean = false
): OrtResult {
    require(packageScanner != null || projectScanner != null) {
        "At least one scanner must be specified."
    }

    // Note: Currently, each scanner gets its own reference to the whole scanner configuration, which includes the
    // options for all scanners.
    if (packageScanner != null && projectScanner != null) {
        check(packageScanner.scannerConfig === projectScanner.scannerConfig) {
            "The package and project scanners need to refer to the same global scanner configuration."
        }
    }

    if (ortResult.analyzer == null) {
        Scanner.log.warn {
            "Cannot run the scanner as the provided ORT result does not contain an analyzer result. " +
                    "No result will be added."
        }

        return ortResult
    }

    val startTime = Instant.now()

    // Determine the projects to scan as packages.
    val consolidatedProjects = consolidateProjectPackagesByVcs(ortResult.getProjects(skipExcluded))
    val projectPackages = consolidatedProjects.keys

    val projectPackageIds = projectPackages.map { it.id }
    val packages = ortResult.getPackages(skipExcluded)
        .filter { it.pkg.id !in projectPackageIds }
        .map { it.pkg }

    val scanResults = runBlocking {
        val deferredProjectScan = async {
            if (projectScanner == null) emptyMap()
            else {
                // Scan the projects from the ORT result.
                val filteredProjectPackages = removeConcludedPackages(projectPackages, projectScanner)

                if (filteredProjectPackages.isEmpty()) emptyMap()
                else projectScanner.scanPackages(filteredProjectPackages, ortResult.labels).mapKeys { it.key.id }
            }
        }

        val deferredPackageScan = async {
            if (packageScanner == null) emptyMap()
            else {
                // Scan the packages from the ORT result.
                val filteredPackages = removeConcludedPackages(packages.toSet(), packageScanner)

                if (filteredPackages.isEmpty()) emptyMap()
                else packageScanner.scanPackages(filteredPackages, ortResult.labels).mapKeys { it.key.id }
            }
        }

        val projectResults = deferredProjectScan.await()
        val packageResults = deferredPackageScan.await()

        projectResults + packageResults
    }.toSortedMap()

    // Add scan results from de-duplicated project packages to result.
    consolidatedProjects.forEach { (referencePackage, deduplicatedPackages) ->
        scanResults[referencePackage.id]?.let { results ->
            deduplicatedPackages.forEach { deduplicatedPackage ->
                ortResult.getProject(deduplicatedPackage.id)?.let { project ->
                    scanResults[project.id] = results.filterByProject(project)
                } ?: throw IllegalArgumentException(
                    "Could not find project '${deduplicatedPackage.id.toCoordinates()}'."
                )
            }

            ortResult.getProject(referencePackage.id)?.let { project ->
                scanResults[project.id] = results.filterByProject(project)
            } ?: throw IllegalArgumentException("Could not find project '${referencePackage.id.toCoordinates()}'.")
        }
    }

    val endTime = Instant.now()

    val filteredScannerOptions = mutableMapOf<String, Options>()

    packageScanner?.scannerConfig?.options?.get(packageScanner.scannerName)?.let { packageScannerOptions ->
        val filteredPackageScannerOptions = packageScanner.filterSecretOptions(packageScannerOptions)
        filteredScannerOptions[packageScanner.scannerName] = filteredPackageScannerOptions
    }

    if (projectScanner != packageScanner) {
        projectScanner?.scannerConfig?.options?.get(projectScanner.scannerName)?.let { projectScannerOptions ->
            val filteredProjectScannerOptions = projectScanner.filterSecretOptions(projectScannerOptions)
            filteredScannerOptions[projectScanner.scannerName] = filteredProjectScannerOptions
        }
    }

    // Only include options of used scanners into the scanner run.
    val scannerConfig = packageScanner?.scannerConfig ?: projectScanner?.scannerConfig
    checkNotNull(scannerConfig)

    val configWithFilteredOptions = scannerConfig.copy(
        options = filteredScannerOptions.takeUnless { it.isEmpty() }
    )

    val filteredScanResults = scanResults.mapValues { (_, results) ->
        results.map { it.filterByIgnorePatterns(scannerConfig.ignorePatterns) }
    }.toSortedMap()

    val scanRecord = ScanRecord(filteredScanResults, ScanResultsStorage.storage.stats)

    // Note: This overwrites any existing ScannerRun from the input file.
    val scannerRun = ScannerRun(startTime, endTime, Environment(), configWithFilteredOptions, scanRecord)
    return ortResult.copy(scanner = scannerRun)
}

/**
 * The class to run license / copyright scanners. The signatures of public functions in this class define the library
 * API.
 */
abstract class Scanner(
    val scannerName: String,
    val scannerConfig: ScannerConfiguration,
    protected val downloaderConfig: DownloaderConfiguration
) {
    companion object {
        private val LOADER = ServiceLoader.load(ScannerFactory::class.java)!!

        /**
         * The set of all available [scanner factories][ScannerFactory] in the classpath, sorted by name.
         */
        val ALL: Set<ScannerFactory> by lazy {
            LOADER.iterator().asSequence().toSortedSet(compareBy { it.scannerName })
        }
    }

    /**
     * The version of the scanner, or an empty string if not applicable.
     */
    abstract val version: String

    /**
     * The configuration used by the scanner (this could be anything from command line options to a URL with query
     * parameters), or an empty string if not applicable.
     */
    abstract val configuration: String

    /**
     * Return the [ScannerDetails] of this scanner.
     */
    val details by lazy { ScannerDetails(scannerName, version, configuration) }

    /**
     * Scan the [packages] and return a map of [ScanResult]s associated by their [Package]. The map may contain multiple
     * results for the same [Package] if the storage contains more than one result for the specification of this
     * scanner. [labels] are the labels present in [OrtResult.labels], created by previous invocations of ORT tools.
     * They can be used by scanner implementations to decide if and how packages are scanned.
     */
    abstract suspend fun scanPackages(
        packages: Set<Package>,
        labels: Map<String, String>
    ): Map<Package, List<ScanResult>>

    /**
     * Filter the scanner-specific options to remove / obfuscate any secrets, like credentials.
     */
    open fun filterSecretOptions(options: Options) = options
}
