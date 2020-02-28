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

package com.here.ort.scanner

import com.here.ort.downloader.Downloader
import com.here.ort.model.Environment
import com.here.ort.model.OrtResult
import com.here.ort.model.Package
import com.here.ort.model.Project
import com.here.ort.model.ScanRecord
import com.here.ort.model.ScanResult
import com.here.ort.model.ScanResultContainer
import com.here.ort.model.ScannerRun
import com.here.ort.model.config.ScannerConfiguration
import com.here.ort.model.readValue
import com.here.ort.spdx.SpdxLicense

import java.io.File
import java.lang.IllegalArgumentException
import java.time.Instant
import java.util.ServiceLoader

import kotlinx.coroutines.runBlocking

const val TOOL_NAME = "scanner"
const val HTTP_CACHE_PATH = "$TOOL_NAME/cache/http"

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
    protected fun getSpdxLicenseIdString(license: String) =
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
            "The provided ORT result file '${ortResultFile.canonicalPath}' does not exit."
        }

        val startTime = Instant.now()

        val ortResult = ortResultFile.readValue<OrtResult>()

        requireNotNull(ortResult.analyzer) {
            "The provided ORT result file '${ortResultFile.invariantSeparatorsPath}' does not contain an analyzer " +
                    "result."
        }

        // Add the projects as packages to scan.
        val consolidatedProjects = Downloader.consolidateProjectPackagesByVcs(ortResult.getProjects(skipExcluded))
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
                        resultContainers += filterProjectScanResults(project, resultContainer)
                    } ?: throw IllegalArgumentException(
                        "Could not find project '${deduplicatedPackage.id.toCoordinates()}'."
                    )
                }

                ortResult.getProject(referencePackage.id)?.let { project ->
                    resultContainers.remove(resultContainer)
                    resultContainers += filterProjectScanResults(project, resultContainer)
                } ?: throw IllegalArgumentException("Could not find project '${referencePackage.id.toCoordinates()}'.")
            }
        }

        val scanRecord = ScanRecord(resultContainers, ScanResultsStorage.storage.stats)

        val endTime = Instant.now()

        val scannerRun = ScannerRun(startTime, endTime, Environment(), config, scanRecord)

        // Note: This overwrites any existing ScannerRun from the input file.
        return ortResult.copy(scanner = scannerRun).apply {
            data += ortResult.data
        }
    }

    /**
     * Filter the scan results in the [resultContainer] for only license findings that are in the same subdirectory as
     * the [project]s definition file.
     */
    private fun filterProjectScanResults(project: Project, resultContainer: ScanResultContainer): ScanResultContainer {
        var filteredResults = resultContainer.results

        // Do not filter the results if the definition file is in the root of the repository.
        val parentPath = File(project.definitionFilePath).parentFile?.path
        if (parentPath != null) {
            filteredResults = resultContainer.results.map { result ->
                if (result.provenance.sourceArtifact != null) {
                    // Do not filter the result if a source artifact was scanned.
                    result
                } else {
                    result.filterPath(parentPath)
                }
            }
        }

        return ScanResultContainer(project.id, filteredResults)
    }
}
