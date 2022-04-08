/*
 * Copyright (C) 2021 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
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

import java.io.File
import java.nio.file.StandardCopyOption
import java.time.Instant

import kotlin.io.path.moveTo
import kotlin.time.measureTime

import org.ossreviewtoolkit.downloader.DownloadException
import org.ossreviewtoolkit.model.AccessStatistics
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.ScanRecord
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.config.ScannerOptions
import org.ossreviewtoolkit.model.config.createFileArchiver
import org.ossreviewtoolkit.scanner.TOOL_NAME
import org.ossreviewtoolkit.utils.common.collectMessagesAsString
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.core.Environment
import org.ossreviewtoolkit.utils.core.log
import org.ossreviewtoolkit.utils.core.showStackTrace

class ExperimentalScanner(
    val scannerConfig: ScannerConfiguration,
    val downloaderConfig: DownloaderConfiguration,
    val provenanceDownloader: ProvenanceDownloader,
    val storageReaders: List<ScanStorageReader>,
    val storageWriters: List<ScanStorageWriter>,
    val packageProvenanceResolver: PackageProvenanceResolver,
    val nestedProvenanceResolver: NestedProvenanceResolver,
    val scannerWrappers: Map<PackageType, List<ScannerWrapper>>
) {
    init {
        require(scannerWrappers.isNotEmpty() && scannerWrappers.any { it.value.isNotEmpty() }) {
            "At least one ScannerWrapper must be provided."
        }
    }

    suspend fun scan(ortResult: OrtResult, skipExcluded: Boolean): OrtResult {
        val startTime = Instant.now()

        val projectScannerWrappers = scannerWrappers[PackageType.PROJECT].orEmpty()
        val packageScannerWrappers = scannerWrappers[PackageType.PACKAGE].orEmpty()

        val projectResults = if (projectScannerWrappers.isNotEmpty()) {
            val packages = ortResult.getProjects(skipExcluded).mapTo(mutableSetOf()) { it.toPackage() }

            log.info { "Scanning ${packages.size} projects with ${projectScannerWrappers.size} scanners." }

            scan(packages, ScanContext(ortResult.labels, PackageType.PROJECT))
        } else {
            log.info { "Skipping scan of projects because no project scanner is configured." }

            emptyMap()
        }

        val packageResults = if (packageScannerWrappers.isNotEmpty()) {
            val packages = ortResult.getPackages(skipExcluded).map { it.pkg }.filterNotConcluded()
                .filterNotMetaDataOnly().toSet()

            log.info { "Scanning ${packages.size} packages with ${packageScannerWrappers.size} scanners." }

            scan(packages, ScanContext(ortResult.labels, PackageType.PACKAGE))
        } else {
            log.info { "Skipping scan of packages because no package scanner is configured." }

            emptyMap()
        }

        val results = (projectResults + packageResults).toSortedMap()

        val endTime = Instant.now()

        val scanRecord = ScanRecord(
            scanResults = results,
            storageStats = AccessStatistics() // TODO: Record access statistics.
        )

        val filteredScannerOptions = mutableMapOf<String, ScannerOptions>()

        projectScannerWrappers.forEach { scannerWrapper ->
            scannerConfig.options?.get(scannerWrapper.name)?.let { options ->
                filteredScannerOptions[scannerWrapper.name] = scannerWrapper.filterSecretOptions(options)
            }
        }

        packageScannerWrappers.forEach { scannerWrapper ->
            scannerConfig.options?.get(scannerWrapper.name)?.let { options ->
                filteredScannerOptions[scannerWrapper.name] = scannerWrapper.filterSecretOptions(options)
            }
        }

        val filteredScannerConfig = scannerConfig.copy(options = filteredScannerOptions)
        val scannerRun = ScannerRun(startTime, endTime, Environment(), filteredScannerConfig, scanRecord)

        return ortResult.copy(scanner = scannerRun)
    }

    suspend fun scan(packages: Set<Package>, context: ScanContext): Map<Identifier, List<ScanResult>> {
        val scanners = scannerWrappers[context.packageType].orEmpty()
        if (scanners.isEmpty()) return emptyMap()

        val controller = ScanController(packages, scanners, scannerConfig)

        resolvePackageProvenances(controller)
        resolveNestedProvenances(controller)

        readStoredResults(controller)

        runPackageScanners(controller, context)
        runProvenanceScanners(controller, context)
        runPathScanners(controller, context)

        createFileArchives(controller.getNestedProvenancesByPackage())

        val results = controller.getNestedScanResultsByPackage().entries.associateTo(sortedMapOf()) {
            it.key.id to it.value.merge()
        }

        val issueResults = controller.getResultsForProvenanceResolutionIssues()

        return results + issueResults
    }

    private fun resolvePackageProvenances(controller: ScanController) {
        log.info { "Resolving provenance for ${controller.packages.size} packages." }

        val duration = measureTime {
            controller.packages.forEach { pkg ->
                runCatching {
                    packageProvenanceResolver.resolveProvenance(pkg, downloaderConfig.sourceCodeOrigins)
                }.onSuccess { provenance ->
                    controller.addPackageProvenance(pkg.id, provenance)
                }.onFailure {
                    controller.addProvenanceResolutionIssue(
                        pkg.id,
                        OrtIssue(
                            source = TOOL_NAME,
                            severity = Severity.ERROR,
                            message = "Could not resolve provenance for package '${pkg.id.toCoordinates()}': " +
                                    it.collectMessagesAsString()
                        )
                    )
                }
            }
        }

        log.info { "Resolved provenance for ${controller.packages.size} packages in $duration." }
    }

    private fun resolveNestedProvenances(controller: ScanController) {
        log.info { "Resolving nested provenances for ${controller.packages.size} packages." }

        val duration = measureTime {
            controller.getPackageProvenancesWithoutVcsPath().forEach { provenance ->
                runCatching {
                    nestedProvenanceResolver.resolveNestedProvenance(provenance)
                }.onSuccess { nestedProvenance ->
                    controller.addNestedProvenance(provenance, nestedProvenance)
                }.onFailure {
                    controller.getPackagesForProvenanceWithoutVcsPath(provenance).forEach { id ->
                        controller.addProvenanceResolutionIssue(
                            id,
                            OrtIssue(
                                source = TOOL_NAME,
                                severity = Severity.ERROR,
                                message = "Could not resolve nested provenance for package " +
                                        "'${id.toCoordinates()}': ${it.collectMessagesAsString()}"
                            )
                        )
                    }
                }
            }
        }

        log.info { "Resolved nested provenance for ${controller.packages.size} packages in $duration." }
    }

    /**
     * Run package scanners for packages with incomplete scan results.
     */
    private fun runPackageScanners(controller: ScanController, context: ScanContext) {
        controller.packages.forEach { pkg ->
            val nestedProvenance = controller.getNestedProvenance(pkg.id) ?: return@forEach

            // TODO: Use coroutines to execute scanners in parallel.
            controller.getPackageScanners().forEach scanner@{ scanner ->
                if (controller.hasCompleteScanResult(scanner, pkg)) {
                    log.debug {
                        "Skipping scan of ${pkg.id.toCoordinates()} with package scanner ${scanner.name} because " +
                                "stored results are available."
                    }

                    return@scanner
                }

                log.info { "Scanning ${pkg.id.toCoordinates()} with package scanner ${scanner.name}." }

                val scanResult = scanner.scanPackage(pkg, context)

                log.info { "Scan of ${pkg.id.toCoordinates()} with package scanner ${scanner.name} finished." }

                val nestedProvenanceScanResult = scanResult.toNestedProvenanceScanResult(nestedProvenance)

                controller.addNestedScanResult(scanner, nestedProvenanceScanResult)

                // TODO: Run in coroutine.
                if (scanner.criteria != null) {
                    storeNestedScanResult(pkg, nestedProvenanceScanResult)
                }
            }
        }
    }

    /**
     * Run provenance scanners for provenances with missing scan results.
     */
    private fun runProvenanceScanners(controller: ScanController, context: ScanContext) {
        controller.getAllProvenances().forEach { provenance ->
            // TODO: Use coroutines to execute scanners in parallel.
            controller.getProvenanceScanners().forEach scanner@{ scanner ->
                if (controller.hasScanResult(scanner, provenance)) {
                    log.debug {
                        "Skipping scan of $provenance with provenance scanner ${scanner.name} because a result is " +
                                "already available."
                    }

                    return@scanner
                }

                log.info { "Scanning $provenance with provenance scanner ${scanner.name}." }

                val scanResult = scanner.scanProvenance(provenance, context)

                val completedPackages = controller.getPackagesCompletedByProvenance(scanner, provenance)

                controller.addScanResults(scanner, provenance, listOf(scanResult))

                storeProvenanceScanResult(provenance, scanResult)

                completedPackages.forEach { pkg ->
                    storePackageScanResult(pkg, controller.getNestedScanResult(pkg.id))
                }
            }
        }
    }

    /**
     * Run path scanners for provenances with missing scan results.
     */
    private fun runPathScanners(controller: ScanController, context: ScanContext) {
        controller.getAllProvenances().forEach { provenance ->
            val scannersForProvenance =
                controller.getPathScanners().filterNot { controller.hasScanResult(it, provenance) }

            if (scannersForProvenance.isEmpty()) return@forEach

            val scanResults = scanPath(provenance, scannersForProvenance, context)

            scanResults.forEach { (scanner, scanResult) ->
                val completedPackages = controller.getPackagesCompletedByProvenance(scanner, provenance)

                controller.addScanResults(scanner, provenance, listOf(scanResult))

                storeProvenanceScanResult(provenance, scanResult)

                completedPackages.forEach { pkg ->
                    storePackageScanResult(pkg, controller.getNestedScanResult(pkg.id))
                }
            }
        }
    }

    private fun Collection<Package>.filterNotConcluded(): Collection<Package> =
        takeUnless { scannerConfig.skipConcluded }
            ?: partition { it.concludedLicense != null && it.authors.isNotEmpty() }.let { (skip, keep) ->
                if (skip.isNotEmpty()) {
                    this@ExperimentalScanner.log.debug {
                        "Not scanning the following packages with concluded licenses: $skip"
                    }
                }

                keep
            }

    private fun Collection<Package>.filterNotMetaDataOnly(): List<Package> =
        partition { it.isMetaDataOnly }.let { (skip, keep) ->
            if (skip.isNotEmpty()) {
                this@ExperimentalScanner.log.debug {
                    "Not scanning the following packages which are metadata only: $skip"
                }
            }

            keep
        }

    private fun readStoredResults(controller: ScanController) {
        log.info {
            "Reading stored scan results for ${controller.getPackageProvenancesWithoutVcsPath().size} packages with " +
                    "${controller.getAllProvenances().size} provenances."
        }

        val readDuration = measureTime {
            readStoredPackageResults(controller)
            readStoredProvenanceResults(controller)
        }

        log.info { "Read stored scan results in $readDuration:" }

        val allKnownProvenances = controller.getAllProvenances()
        controller.scanners.forEach { scanner ->
            val results = controller.getScanResults(scanner)
            log.info { "\t${scanner.name}: Results for ${results.size} of ${allKnownProvenances.size} provenances." }
        }
    }

    private fun readStoredPackageResults(controller: ScanController) {
        controller.scanners.forEach { scanner ->
            val scannerCriteria = scanner.criteria ?: return@forEach

            controller.packages.forEach pkg@{ pkg ->
                val nestedProvenance = controller.findNestedProvenance(pkg.id) ?: return@pkg

                storageReaders.filterIsInstance<PackageBasedScanStorageReader>().forEach { reader ->
                    if (controller.hasCompleteScanResult(scanner, pkg)) return@pkg

                    runCatching {
                        reader.read(pkg, nestedProvenance, scannerCriteria)
                    }.onSuccess { results ->
                        results.forEach { result ->
                            controller.addNestedScanResult(scanner, result)
                        }
                    }.onFailure { e ->
                        e.showStackTrace()

                        log.warn {
                            "Could not read scan result for ${pkg.id.toCoordinates()} from " +
                                    "${reader.javaClass.simpleName}: ${e.collectMessagesAsString()}"
                        }
                    }
                }
            }
        }
    }

    private fun readStoredProvenanceResults(controller: ScanController) {
        controller.scanners.forEach { scanner ->
            val scannerCriteria = scanner.criteria ?: return@forEach

            controller.getAllProvenances().forEach provenance@{ provenance ->
                if (controller.hasScanResult(scanner, provenance)) return@provenance

                storageReaders.filterIsInstance<ProvenanceBasedScanStorageReader>().forEach { reader ->
                    runCatching {
                        reader.read(provenance, scannerCriteria)
                    }.onSuccess { results ->
                        controller.addScanResults(scanner, provenance, results)
                    }.onFailure { e ->
                        e.showStackTrace()

                        log.warn {
                            "Could not read scan result for $provenance from ${reader.javaClass.simpleName}: " +
                                    e.collectMessagesAsString()
                        }
                    }
                }
            }
        }
    }

    private fun scanPath(
        provenance: KnownProvenance,
        scanners: List<PathScannerWrapper>,
        context: ScanContext
    ): Map<PathScannerWrapper, ScanResult> {
        val downloadDir = try {
            provenanceDownloader.download(provenance)
        } catch (e: DownloadException) {
            val message = "Could not download provenance $provenance: ${e.collectMessagesAsString()}"
            log.error { message }

            val summary = ScanSummary(
                startTime = Instant.now(),
                endTime = Instant.now(),
                packageVerificationCode = "",
                licenseFindings = sortedSetOf(),
                copyrightFindings = sortedSetOf(),
                issues = listOf(
                    OrtIssue(
                        source = "Downloader",
                        message = message,
                        severity = Severity.ERROR
                    )
                )
            )

            return scanners.associateWith { scanner ->
                ScanResult(
                    provenance = provenance,
                    scanner = scanner.details,
                    summary = summary
                )
            }
        }

        return try {
            scanners.associateWith { scanner ->
                log.info { "Scanning $provenance with path scanner ${scanner.name}." }

                val summary = scanner.scanPath(downloadDir, context)
                log.info { "Scan of $provenance with path scanner ${scanner.name} finished." }

                ScanResult(provenance, scanner.details, summary)
            }
        } finally {
            downloadDir.safeDeleteRecursively(force = true)
        }
    }

    private fun storeNestedScanResult(pkg: Package, nestedProvenanceScanResult: NestedProvenanceScanResult) {
        storePackageScanResult(pkg, nestedProvenanceScanResult)

        nestedProvenanceScanResult.scanResults.forEach { (provenance, scanResults) ->
            scanResults.forEach { scanResult ->
                storeProvenanceScanResult(provenance, scanResult)
            }
        }
    }

    private fun storeProvenanceScanResult(provenance: KnownProvenance, scanResult: ScanResult) {
        storageWriters.filterIsInstance<ProvenanceBasedScanStorageWriter>().forEach { writer ->
            runCatching {
                writer.write(scanResult)
            }.onFailure { e ->
                e.showStackTrace()

                log.warn {
                    "Could not write scan result for $provenance to ${writer.javaClass.simpleName}: " +
                            e.collectMessagesAsString()
                }
            }
        }
    }

    private fun storePackageScanResult(pkg: Package, nestedProvenanceScanResult: NestedProvenanceScanResult) {
        storageWriters.filterIsInstance<PackageBasedScanStorageWriter>().forEach { writer ->
            runCatching {
                writer.write(pkg, nestedProvenanceScanResult)
            }.onFailure { e ->
                e.showStackTrace()

                log.warn {
                    "Could not write scan result for ${pkg.id.toCoordinates()} to ${writer.javaClass.simpleName}: " +
                            e.collectMessagesAsString()
                }
            }
        }
    }

    private fun createFileArchives(nestedProvenances: Map<Package, NestedProvenance>) {
        // TODO: The archives are currently created in a way compatible with the existing implementation in the
        //       PathScanner. This allows to keep using existing file archives without changing the logic used to
        //       access those archives in the reporter. To achieve this nested provenances are downloaded recursively,
        //       so that the created archives contain also files from nested repositories.
        //       This could be replaced with creating file archives for each provenance separately and building the
        //       final result on demand, to reduce duplication in the file archives.

        val archiver = scannerConfig.archive.createFileArchiver()

        val provenancesWithMissingArchives = nestedProvenances.filterNot { (_, nestedProvenance) ->
            archiver.hasArchive(nestedProvenance.root)
        }

        log.info { "Creating file archives for ${provenancesWithMissingArchives.size} packages." }

        val duration = measureTime {
            provenancesWithMissingArchives.forEach { (_, nestedProvenance) ->
                val dir = downloadRecursively(nestedProvenance)
                archiver.archive(dir, nestedProvenance.root)
                dir.safeDeleteRecursively(force = true)
            }
        }

        log.info { "Created file archives for ${provenancesWithMissingArchives.size} packages in $duration." }
    }

    private fun downloadRecursively(nestedProvenance: NestedProvenance): File {
        // Use the provenanceDownloader to download each provenance from nestedProvenance separately, because they are
        // likely already cached if a path scanner wrapper is used.

        val root = provenanceDownloader.download(nestedProvenance.root)

        nestedProvenance.subRepositories.forEach { (path, provenance) ->
            val tempDir = provenanceDownloader.download(provenance)
            val targetDir = root.resolve(path)
            tempDir.toPath().moveTo(targetDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
        }

        return root
    }
}

/**
 * Split this [ScanResult] into separate results for each [KnownProvenance] contained in the [nestedProvenance] by
 * matching the paths of findings with the paths in the nested provenance.
 */
fun ScanResult.toNestedProvenanceScanResult(nestedProvenance: NestedProvenance): NestedProvenanceScanResult {
    val provenanceByPath =
        nestedProvenance.subRepositories.toList().toMutableList<Pair<String, KnownProvenance>>()
            .also { it += ("" to nestedProvenance.root) }
            .sortedByDescending { it.first.length }

    val copyrightFindingsByProvenance = summary.copyrightFindings.groupBy { copyrightFinding ->
        provenanceByPath.first { copyrightFinding.location.path.startsWith(it.first) }.second
    }.mapValues { (provenance, findings) ->
        val provenancePrefix = "${nestedProvenance.getPath(provenance)}/"
        findings.map { it.copy(location = it.location.copy(path = it.location.path.removePrefix(provenancePrefix))) }
    }

    val licenseFindingsByProvenance = summary.licenseFindings.groupBy { licenseFinding ->
        provenanceByPath.first { licenseFinding.location.path.startsWith(it.first) }.second
    }.mapValues { (provenance, findings) ->
        val provenancePrefix = "${nestedProvenance.getPath(provenance)}/"
        findings.map { it.copy(location = it.location.copy(path = it.location.path.removePrefix(provenancePrefix))) }
    }

    val provenances = nestedProvenance.getProvenances()
    val scanResultsByProvenance = provenances.associateWith { provenance ->
        // TODO: Find a solution for the incorrect packageVerificationCode and for how to associate issues to the
        //       correct scan result.
        listOf(
            copy(
                provenance = provenance,
                summary = summary.copy(
                    licenseFindings = licenseFindingsByProvenance[provenance].orEmpty().toSortedSet(),
                    copyrightFindings = copyrightFindingsByProvenance[provenance].orEmpty().toSortedSet()
                )
            )
        )
    }

    return NestedProvenanceScanResult(nestedProvenance, scanResultsByProvenance)
}
