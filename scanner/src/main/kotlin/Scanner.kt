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
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel

import kotlinx.coroutines.runBlocking

import org.ossreviewtoolkit.downloader.consolidateProjectPackagesByVcs
import org.ossreviewtoolkit.model.Environment
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanResultContainer
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
     * download the source code to for scanning. The scan operation can be executed in parallel to increase
     * performance. [ScanResult]s are returned associated by the [Package]. They are passed to the [channel] provided
     * when they become available. There may be multiple results for the same [Package] if the storage contains more
     * than one result for the specification of this scanner.
     */
    protected abstract suspend fun scanPackages(
        channel: Channel<Pair<Package, List<ScanResult>>>,
        packages: List<Package>,
        outputDirectory: File,
        downloadDirectory: File
    )

    /**
     * Return the scanner-specific SPDX idstring for the given [license].
     */
    fun getSpdxLicenseIdString(license: String) =
        SpdxLicense.forId(license)?.id ?: "LicenseRef-$scannerName-$license"

    /**
     * Scan the [Project]s and [Package]s specified in [ortResultFile] and store the scan results in [outputDirectory].
     * The [downloadDirectory] is used to download the source code to for scanning. Use the [builder] provided to
     * construct an [OrtResult] with the results of this scan operation.
     */
    fun scanOrtResult(
        builder: ScannerResultBuilder,
        ortResultFile: File,
        outputDirectory: File,
        downloadDirectory: File,
        skipExcluded: Boolean = false,
        labels: Map<String, String> = emptyMap()
    ) = runBlocking {
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
            "The provided ORT result file '${ortResultFile.invariantSeparatorsPath}' does not contain an analyzer " +
                    "result."
        }

        builder.initFromAnalyzerResult(ortResult)
        // Add the projects as packages to scan.
        val consolidatedProjects = consolidateProjectPackagesByVcs(ortResult.getProjects(skipExcluded))
        val consolidatedReferencePackages = consolidatedProjects.keys.map { it.toCuratedPackage() }
        val packagesToScan = (consolidatedReferencePackages + ortResult.getPackages(skipExcluded)).map { it.pkg }

        val channel = Channel<Pair<Package, List<ScanResult>>>()
        async { scanPackages(channel, packagesToScan, outputDirectory, downloadDirectory) }

        for ((pkg, results) in channel) {
            passResultsToBuilder(builder, ortResult, consolidatedProjects, pkg, results)
        }

        val endTime = Instant.now()
        builder.complete(startTime, endTime, Environment(), config, ScanResultsStorage.storage.stats, labels)
    }

    /**
     * Pass the [results] for the given [package][pkg] to the [builder]. If the result is for one of the projects
     * in [consolidatedProjects], corresponding filtered results are produced for all sub packages making use of
     * the given [ortResult].
     */
    private fun passResultsToBuilder(
        builder: ScannerResultBuilder,
        ortResult: OrtResult,
        consolidatedProjects: Map<Package, List<Package>>,
        pkg: Package,
        results: List<ScanResult>
    ) {
        log.debug { "Received scan result for ${pkg.id.toCoordinates()}." }
        val resultContainer = ScanResultContainer(pkg.id, results)
        val deduplicatedPackages = consolidatedProjects[pkg]

        if (deduplicatedPackages != null) {
            deduplicatedPackages.forEach { deduplicatedPackage ->
                builder.addScanResult(filterPackageScanResults(ortResult, deduplicatedPackage, resultContainer))
            }

            builder.addScanResult(filterPackageScanResults(ortResult, pkg, resultContainer))
        } else {
            builder.addScanResult(resultContainer)
        }
    }

    /**
     * Filter the scan results in the [resultContainer] for only license findings that are in a specific subdirectory
     * identified by the [pkg] provided. The project of the package is looked up in the given [ortResult]. If it
     * cannot be resolved, throw an [IllegalArgumentException].
     */
    private fun filterPackageScanResults(
        ortResult: OrtResult,
        pkg: Package,
        resultContainer: ScanResultContainer
    ): ScanResultContainer =
        ortResult.getProject(pkg.id)?.let { project ->
            filterProjectScanResults(project, resultContainer)
        } ?: throw IllegalArgumentException("Could not find project '${pkg.id.toCoordinates()}'.")

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
                    result
                } else {
                    result.filterPath(parentPath)
                }
            }
        }

        return ScanResultContainer(project.id, filteredResults)
    }
}
