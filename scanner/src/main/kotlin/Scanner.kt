/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
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

import java.io.File
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
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.config.ScannerOptions
import org.ossreviewtoolkit.model.utils.filterByProject
import org.ossreviewtoolkit.utils.Environment
import org.ossreviewtoolkit.utils.log

const val TOOL_NAME = "scanner"

/**
 * Use the [scanner] to scan the [Project]s and [Package]s specified in the [ortResult]. Scan results are stored in the
 * [outputDirectory]. If [skipExcluded] is true, packages for which excludes are defined are not scanned. Return scan
 * results as an [OrtResult].
 */
fun scanOrtResult(
    scanner: Scanner,
    ortResult: OrtResult,
    outputDirectory: File,
    skipExcluded: Boolean = false
) = scanOrtResult(scanner, scanner, ortResult, outputDirectory, skipExcluded)

/**
 * Use the [scanner] and [projectScanner] to scan the [Project]s and [Package]s specified in the [ortResult],
 * respectively. Scan results are stored in the [outputDirectory]. If [skipExcluded] is true, packages for which
 * excludes are defined are not scanned. Return scan results as an [OrtResult].
 */
fun scanOrtResult(
    scanner: Scanner,
    projectScanner: Scanner,
    ortResult: OrtResult,
    outputDirectory: File,
    skipExcluded: Boolean = false
): OrtResult {
    val startTime = Instant.now()

    if (ortResult.analyzer == null) {
        scanner.log.warn {
            "Cannot run the scanner as the provided ORT result does not contain an analyzer result. " +
                    "No result will be added."
        }

        return ortResult
    }

    // Determine the projects to scan as packages.
    val consolidatedProjects = consolidateProjectPackagesByVcs(ortResult.getProjects(skipExcluded))
    val projectPackages = consolidatedProjects.keys

    val projectPackageIds = projectPackages.map { it.id }
    val packages = ortResult.getPackages(skipExcluded)
        .filter { it.pkg.id !in projectPackageIds }
        .map { it.pkg }

    fun removeConcludedPackages(packages: Collection<Package>, scanner: Scanner): Collection<Package> =
        packages.takeUnless { scanner.scannerConfig.skipConcluded }
            // Remove all packages that have a concluded license and authors set.
            ?: packages.partition { it.concludedLicense != null && it.authors.isNotEmpty() }.let { (skip, keep) ->
                if (skip.isNotEmpty()) {
                    scanner.log.debug { "Not scanning the following packages with concluded licenses: $skip" }
                }

                keep
            }

    val filteredProjectPackages = removeConcludedPackages(projectPackages, projectScanner)
    val filteredPackages = removeConcludedPackages(packages, scanner)

    val scanResults = runBlocking {
        // Scan the projects from the ORT result.
        val deferredProjectScan = async {
            projectScanner.scanPackages(filteredProjectPackages, outputDirectory).mapKeys { it.key.id }
        }

        // Scan the packages from the ORT result.
        val deferredPackageScan = async {
            scanner.scanPackages(filteredPackages, outputDirectory).mapKeys { it.key.id }
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

    val scanRecord = ScanRecord(scanResults, ScanResultsStorage.storage.stats)

    val endTime = Instant.now()

    val filteredScannerOptions = scanner.scannerConfig.options?.let { options ->
        options[scanner.scannerName]?.let { scannerOptions ->
            val filteredScannerOptions = scanner.filterOptionsForResult(scannerOptions)
            options.toMutableMap().apply { put(scanner.scannerName, filteredScannerOptions) }
        }
    } ?: scanner.scannerConfig.options

    val configWithFilteredOptions = scanner.scannerConfig.copy(options = filteredScannerOptions)
    val scannerRun = ScannerRun(startTime, endTime, Environment(), configWithFilteredOptions, scanRecord)

    // Note: This overwrites any existing ScannerRun from the input file.
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
         * The list of all available scanners in the classpath.
         */
        val ALL by lazy { LOADER.iterator().asSequence().toList() }
    }

    /**
     * Scan the [packages] and store the scan results in [outputDirectory]. [ScanResult]s are returned associated by
     * [Package]. The map may contain multiple results for the same [Package] if the storage contains more than one
     * result for the specification of this scanner.
     */
    internal abstract suspend fun scanPackages(
        packages: Collection<Package>,
        outputDirectory: File
    ): Map<Package, List<ScanResult>>

    /**
     * Filter the options specific to this scanner that will be included into the result, e.g. to perform obfuscation of
     * credentials.
     */
    open fun filterOptionsForResult(options: ScannerOptions) = options
}
