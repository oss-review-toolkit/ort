/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import kotlin.time.measureTimedValue

import kotlinx.coroutines.runBlocking

import org.ossreviewtoolkit.downloader.consolidateProjectPackagesByVcs
import org.ossreviewtoolkit.model.Environment
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ScanRecord
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanResultContainer
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.spdx.SpdxLicense
import org.ossreviewtoolkit.utils.formatSizeInMib
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.perf

const val TOOL_NAME = "scanner"

/**
 * The class to run license / copyright scanners. The signatures of public functions in this class define the library
 * API.
 */
abstract class Scanner(val scannerName: String, protected val config: ScannerConfiguration) {
    companion object {
        private val LOADER = ServiceLoader.load(ScannerFactory::class.java)!!

        /**
         * The list of all available scanners in the classpath.
         */
        val ALL by lazy { LOADER.iterator().asSequence().toList() }
    }

    /**
     * Scan the list of [packages] and store the scan results in [outputDirectory]. The [downloadDirectory] is used to
     * download the source code to for scanning. [ScanResult]s are returned associated by the [Package]. The map may
     * contain multiple results for the same [Package] if the storage contains more than one result for the
     * specification of this scanner.
     */
    protected abstract suspend fun scanPackages(
        packages: List<Package>,
        outputDirectory: File,
        downloadDirectory: File
    ): Map<Package, List<ScanResult>>

    /**
     * Return the scanner-specific SPDX idstring for the given [license].
     */
    fun getSpdxLicenseIdString(license: String) =
        SpdxLicense.forId(license)?.id ?: "LicenseRef-$scannerName-$license"

    /**
     * Scan the [Project]s and [Package]s specified in [ortResultFile] and store the scan results in [outputDirectory].
     * The [downloadDirectory] is used to download the source code to for scanning. Return scan results as an
     * [OrtResult].
     */
    fun scanOrtResult(
        ortResultFile: File,
        outputDirectory: File,
        downloadDirectory: File,
        skipExcluded: Boolean = false
    ): OrtResult {
        require(ortResultFile.isFile) {
            "The provided ORT result file '${ortResultFile.canonicalPath}' does not exist."
        }

        val startTime = Instant.now()

        val (ortResult, duration) = measureTimedValue { ortResultFile.readValue<OrtResult>() }

        log.perf {
            "Read ORT result from '${ortResultFile.name}' (${ortResultFile.formatSizeInMib}) in " +
                    "${duration.inMilliseconds}ms."
        }

        requireNotNull(ortResult.analyzer) {
            "The provided ORT result file '${ortResultFile.canonicalPath}' does not contain an analyzer result."
        }

        // Add the projects as packages to scan.
        val consolidatedProjects = consolidateProjectPackagesByVcs(ortResult.getProjects(skipExcluded))
        val consolidatedReferencePackages = consolidatedProjects.keys.map { it.toCuratedPackage() }

        val packagesToScan = (consolidatedReferencePackages + ortResult.getPackages(skipExcluded)).map { it.pkg }
        val results = runBlocking { scanPackages(packagesToScan, outputDirectory, downloadDirectory) }
        val resultContainers = results.map { (pkg, results) ->
            ScanResultContainer(pkg.id, results)
        }.toSortedSet()

        // Add scan results from de-duplicated project packages to result.
        consolidatedProjects.forEach { (referencePackage, deduplicatedPackages) ->
            resultContainers.find { it.id == referencePackage.id }?.let { resultContainer ->
                deduplicatedPackages.forEach { deduplicatedPackage ->
                    ortResult.getProject(deduplicatedPackage.id)?.let { project ->
                        resultContainers += resultContainer.filterByProject(project)
                    } ?: throw IllegalArgumentException(
                        "Could not find project '${deduplicatedPackage.id.toCoordinates()}'."
                    )
                }

                ortResult.getProject(referencePackage.id)?.let { project ->
                    resultContainers.remove(resultContainer)
                    resultContainers += resultContainer.filterByProject(project)
                } ?: throw IllegalArgumentException("Could not find project '${referencePackage.id.toCoordinates()}'.")
            }
        }

        val scanRecord = ScanRecord(resultContainers, ScanResultsStorage.storage.stats)

        val endTime = Instant.now()

        val scannerRun = ScannerRun(startTime, endTime, Environment(), config, scanRecord)

        // Note: This overwrites any existing ScannerRun from the input file.
        return ortResult.copy(scanner = scannerRun)
    }
}
