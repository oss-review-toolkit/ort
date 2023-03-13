/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
import java.nio.file.StandardCopyOption
import java.time.Instant

import kotlin.io.path.moveTo
import kotlin.time.measureTime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.downloader.DownloadException
import org.ossreviewtoolkit.model.AccessStatistics
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.Options
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.config.createFileArchiver
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.scanner.provenance.NestedProvenance
import org.ossreviewtoolkit.scanner.provenance.NestedProvenanceResolver
import org.ossreviewtoolkit.scanner.provenance.NestedProvenanceScanResult
import org.ossreviewtoolkit.scanner.provenance.PackageProvenanceResolver
import org.ossreviewtoolkit.scanner.provenance.ProvenanceDownloader
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.ort.showStackTrace

const val TOOL_NAME = "scanner"

class Scanner(
    val scannerConfig: ScannerConfiguration,
    val downloaderConfig: DownloaderConfiguration,
    val provenanceDownloader: ProvenanceDownloader,
    val storageReaders: List<ScanStorageReader>,
    val storageWriters: List<ScanStorageWriter>,
    val packageProvenanceResolver: PackageProvenanceResolver,
    val nestedProvenanceResolver: NestedProvenanceResolver,
    val scannerWrappers: Map<PackageType, List<ScannerWrapper>>
) {
    companion object : Logging

    init {
        require(scannerWrappers.isNotEmpty() && scannerWrappers.any { it.value.isNotEmpty() }) {
            "At least one ScannerWrapper must be provided."
        }

        scannerWrappers.values.flatten().distinct().forEach { scannerWrapper ->
            scannerWrapper.criteria?.let { criteria ->
                require(criteria.matches(scannerWrapper.details)) {
                    "The scanner details of scanner '${scannerWrapper.details.name}' must satisfy the configured " +
                        "criteria for looking up scan storage entries."
                }
            }
        }
    }

    private val archiver = scannerConfig.archive.createFileArchiver()

    suspend fun scan(ortResult: OrtResult, skipExcluded: Boolean, labels: Map<String, String>): OrtResult {
        val startTime = Instant.now()

        val projectScannerWrappers = scannerWrappers[PackageType.PROJECT].orEmpty()
        val packageScannerWrappers = scannerWrappers[PackageType.PACKAGE].orEmpty()

        val projectResults = if (projectScannerWrappers.isNotEmpty()) {
            val packages = ortResult.getProjects(skipExcluded).mapTo(mutableSetOf()) { it.toPackage() }

            logger.info { "Scanning ${packages.size} project(s) with ${projectScannerWrappers.size} scanner(s)." }

            scan(packages, ScanContext(ortResult.labels + labels, PackageType.PROJECT))
        } else {
            logger.info { "Skipping project scan as no project scanner is configured." }

            emptyMap()
        }

        val packageResults = if (packageScannerWrappers.isNotEmpty()) {
            val packages = ortResult.getPackages(skipExcluded).map { it.metadata }.filterNotConcluded()
                .filterNotMetadataOnly().toSet()

            logger.info { "Scanning ${packages.size} package(s) with ${packageScannerWrappers.size} scanner(s)." }

            scan(packages, ScanContext(ortResult.labels, PackageType.PACKAGE))
        } else {
            logger.info { "Skipping package scan as no package scanner is configured." }

            emptyMap()
        }

        val scanResults = (projectResults + packageResults).toSortedMap()
        val storageStats = AccessStatistics() // TODO: Record access statistics.

        val endTime = Instant.now()

        val filteredScannerOptions = mutableMapOf<String, Options>()

        projectScannerWrappers.forEach { scannerWrapper ->
            scannerConfig.options?.get(scannerWrapper.details.name)?.let { options ->
                filteredScannerOptions[scannerWrapper.details.name] = scannerWrapper.filterSecretOptions(options)
            }
        }

        packageScannerWrappers.forEach { scannerWrapper ->
            scannerConfig.options?.get(scannerWrapper.details.name)?.let { options ->
                filteredScannerOptions[scannerWrapper.details.name] = scannerWrapper.filterSecretOptions(options)
            }
        }

        val filteredScannerConfig = scannerConfig.copy(options = filteredScannerOptions)
        val scannerRun = ScannerRun(startTime, endTime, Environment(), filteredScannerConfig, scanResults, storageStats)

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

        createMissingArchives(controller)

        val results = controller.getNestedScanResultsByPackage().entries.associateTo(sortedMapOf()) {
            it.key.id to it.value.merge()
        }

        val issueResults = controller.getResultsForProvenanceResolutionIssues()

        return results + issueResults
    }

    private suspend fun resolvePackageProvenances(controller: ScanController) {
        logger.info { "Resolving provenance for ${controller.packages.size} package(s)." }

        val duration = measureTime {
            withContext(Dispatchers.IO) {
                controller.packages.map { pkg ->
                    async {
                        pkg to runCatching {
                            packageProvenanceResolver.resolveProvenance(pkg, downloaderConfig.sourceCodeOrigins)
                        }
                    }
                }.awaitAll()
            }.forEach { (pkg, result) ->
                result.onSuccess { provenance ->
                    controller.addPackageProvenance(pkg.id, provenance)
                }.onFailure {
                    controller.addProvenanceResolutionIssue(
                        pkg.id,
                        Issue(
                            source = TOOL_NAME,
                            severity = Severity.ERROR,
                            message = "Could not resolve provenance for package '${pkg.id.toCoordinates()}': " +
                                    it.collectMessages()
                        )
                    )
                }
            }
        }

        logger.info { "Resolved provenance for ${controller.packages.size} package(s) in $duration." }
    }

    private suspend fun resolveNestedProvenances(controller: ScanController) {
        logger.info { "Resolving nested provenances for ${controller.packages.size} package(s)." }

        val duration = measureTime {
            withContext(Dispatchers.IO) {
                controller.getPackageProvenancesWithoutVcsPath().map { provenance ->
                    async {
                        provenance to runCatching {
                            nestedProvenanceResolver.resolveNestedProvenance(provenance)
                        }
                    }
                }.awaitAll()
            }.forEach { (provenance, result) ->
                result.onSuccess { nestedProvenance ->
                    controller.addNestedProvenance(provenance, nestedProvenance)
                }.onFailure {
                    controller.getPackagesForProvenanceWithoutVcsPath(provenance).forEach { id ->
                        controller.addProvenanceResolutionIssue(
                            id,
                            Issue(
                                source = TOOL_NAME,
                                severity = Severity.ERROR,
                                message = "Could not resolve nested provenance for package " +
                                        "'${id.toCoordinates()}': ${it.collectMessages()}"
                            )
                        )
                    }
                }
            }
        }

        logger.info { "Resolved nested provenance for ${controller.packages.size} package(s) in $duration." }
    }

    /**
     * Run package scanners for packages with incomplete scan results.
     */
    private fun runPackageScanners(controller: ScanController, context: ScanContext) {
        val packagesByProvenance = controller.getPackagesConsolidatedByProvenance()

        packagesByProvenance.onEachIndexed { index, (provenance, packages) ->
            logger.info { "Scanning $provenance (${index + 1} of ${packagesByProvenance.size})..." }

            controller.getPackageScanners().forEach scanner@{ scanner ->
                val packagesWithIncompleteScanResult = packages.filter { pkg ->
                    val hasNestedProvenance = controller.getNestedProvenance(pkg.id) != null
                    if (!hasNestedProvenance) {
                        logger.debug {
                            "Skipping scan of '${pkg.id.toCoordinates()}' with package scanner " +
                                    "'${scanner.details.name}' as no nested provenance for the package could be " +
                                    "resolved."
                        }
                    }

                    val hasCompleteScanResult = controller.hasCompleteScanResult(scanner, pkg)
                    if (hasCompleteScanResult) {
                        logger.debug {
                            "Skipping scan of '${pkg.id.toCoordinates()}' with package scanner " +
                                    "'${scanner.details.name}' as stored results are available."
                        }
                    }

                    hasNestedProvenance && !hasCompleteScanResult
                }

                if (packagesWithIncompleteScanResult.isEmpty()) {
                    logger.info {
                        "Skipping scan with package scanner '${scanner.details.name}' as all packages have results."
                    }

                    return@scanner
                }

                // Create a reference package with any VCS path removed, to ensure the full repository is scanned.
                val referencePackage = packagesWithIncompleteScanResult.first().let { pkg ->
                    if (provenance is RepositoryProvenance) {
                        pkg.copy(vcsProcessed = pkg.vcsProcessed.copy(path = ""))
                    } else {
                        pkg
                    }
                }

                if (packagesWithIncompleteScanResult.size > 1) {
                    logger.info {
                        val packageIds = packagesWithIncompleteScanResult.drop(1)
                            .joinToString("\n") { "\t${it.id.toCoordinates()}" }
                        "Scanning package '${referencePackage.id.toCoordinates()}' as reference for these packages " +
                                "with the same provenance:\n$packageIds"
                    }
                }

                logger.info {
                    "Scan of '${referencePackage.id.toCoordinates()}' with package scanner '${scanner.details.name} " +
                            "started."
                }

                val scanResult = scanner.scanPackage(referencePackage, context)

                logger.info {
                    "Scan of '${referencePackage.id.toCoordinates()}' with package scanner '${scanner.details.name}' " +
                            "finished."
                }

                packagesWithIncompleteScanResult.forEach processResults@{ pkg ->
                    val nestedProvenance = controller.getNestedProvenance(pkg.id) ?: return@processResults
                    val nestedProvenanceScanResult = scanResult.toNestedProvenanceScanResult(nestedProvenance)
                    controller.addNestedScanResult(scanner, nestedProvenanceScanResult)

                    // TODO: Run in coroutine.
                    if (scanner.criteria != null) {
                        storeNestedScanResult(pkg, nestedProvenanceScanResult)
                    }
                }
            }
        }
    }

    /**
     * Run provenance scanners for provenances with missing scan results.
     */
    private fun runProvenanceScanners(controller: ScanController, context: ScanContext) {
        val provenances = controller.getAllProvenances()

        provenances.forEachIndexed { index, provenance ->
            // TODO: Use coroutines to execute scanners in parallel.
            controller.getProvenanceScanners().forEach scanner@{ scanner ->
                if (controller.hasScanResult(scanner, provenance)) {
                    logger.debug {
                        "Skipping $provenance scan (${index + 1} of ${provenances.size}) with provenance scanner " +
                                "'${scanner.details.name}' as a result is already available."
                    }

                    return@scanner
                }

                logger.info {
                    "Scanning $provenance (${index + 1} of ${provenances.size}) with provenance scanner " +
                            "'${scanner.details.name}'."
                }

                val scanResult = scanner.scanProvenance(provenance, context)

                val completedPackages = controller.getPackagesCompletedByProvenance(scanner, provenance)

                controller.addScanResults(scanner, provenance, listOf(scanResult))

                storeProvenanceScanResult(provenance, scanResult)

                completedPackages.forEach { pkg ->
                    controller.getNestedScanResult(pkg.id)?.let { storePackageScanResult(pkg, it) }
                }
            }
        }
    }

    /**
     * Run path scanners for provenances with missing scan results.
     */
    private fun runPathScanners(controller: ScanController, context: ScanContext) {
        val provenances = controller.getAllProvenances()

        provenances.forEachIndexed { index, provenance ->
            val scannersWithoutResults = controller.getPathScanners().filterNot {
                controller.hasScanResult(it, provenance)
            }

            if (scannersWithoutResults.isEmpty()) {
                logger.info {
                    "Skipping $provenance (${index + 1} of ${provenances.size}) as all scanners have results."
                }

                return@forEachIndexed
            }

            logger.info { "Scanning $provenance (${index + 1} of ${provenances.size})..." }

            val scanResults = scanPath(provenance, scannersWithoutResults, context)

            scanResults.forEach { (scanner, scanResult) ->
                val completedPackages = controller.getPackagesCompletedByProvenance(scanner, provenance)

                controller.addScanResults(scanner, provenance, listOf(scanResult))

                storeProvenanceScanResult(provenance, scanResult)

                completedPackages.forEach { pkg ->
                    controller.getNestedScanResult(pkg.id)?.let { storePackageScanResult(pkg, it) }
                }
            }
        }
    }

    private fun Collection<Package>.filterNotConcluded(): Collection<Package> =
        takeUnless { scannerConfig.skipConcluded }
            ?: partition { it.concludedLicense != null && it.authors.isNotEmpty() }.let { (skip, keep) ->
                if (skip.isNotEmpty()) {
                    logger.debug {
                        "Not scanning the following package(s) with concluded licenses: $skip"
                    }
                }

                keep
            }

    private fun Collection<Package>.filterNotMetadataOnly(): List<Package> =
        partition { it.isMetadataOnly }.let { (skip, keep) ->
            if (skip.isNotEmpty()) {
                logger.debug {
                    "Not scanning the following package(s) which are metadata only: $skip"
                }
            }

            keep
        }

    private fun readStoredResults(controller: ScanController) {
        logger.info {
            "Reading stored scan results for ${controller.getPackageProvenancesWithoutVcsPath().size} package(s) " +
                    "with ${controller.getAllProvenances().size} provenance(s)."
        }

        val readDuration = measureTime {
            readStoredPackageResults(controller)
            readStoredProvenanceResults(controller)
        }

        logger.info { "Read the following stored scan result(s) in $readDuration:" }

        val allKnownProvenances = controller.getAllProvenances()
        controller.scanners.forEach { scanner ->
            val results = controller.getScanResults(scanner)
            logger.info {
                "\t${scanner.details.name}: Result(s) for ${results.size} of ${allKnownProvenances.size} provenance(s)."
            }
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

                        logger.warn {
                            "Could not read scan result for '${pkg.id.toCoordinates()}' from " +
                                    "${reader.javaClass.simpleName}: ${e.collectMessages()}"
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

                        logger.warn {
                            "Could not read scan result for $provenance from ${reader.javaClass.simpleName}: " +
                                    e.collectMessages()
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
            val issue = createAndLogIssue(
                "Downloader", "Could not download provenance $provenance: ${e.collectMessages()}"
            )

            val summary = ScanSummary(
                startTime = Instant.now(),
                endTime = Instant.now(),
                packageVerificationCode = "",
                licenseFindings = sortedSetOf(),
                copyrightFindings = sortedSetOf(),
                issues = listOf(issue)
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
                logger.info { "Scan of $provenance with path scanner '${scanner.details.name}' started." }

                val summary = scanner.scanPath(downloadDir, context)

                logger.info { "Scan of $provenance with path scanner '${scanner.details.name}' finished." }

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

                logger.warn {
                    "Could not write scan result for $provenance to ${writer.javaClass.simpleName}: " +
                            e.collectMessages()
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

                logger.warn {
                    "Could not write scan result for '${pkg.id.toCoordinates()}' to ${writer.javaClass.simpleName}: " +
                            e.collectMessages()
                }
            }
        }
    }

    private fun createMissingArchives(controller: ScanController) {
        // TODO: The archives are currently created in a way compatible with the existing implementation in the
        //       PathScanner. This allows to keep using existing file archives without changing the logic used to
        //       access those archives in the reporter. To achieve this nested provenances are downloaded recursively,
        //       so that the created archives contain also files from nested repositories.
        //       This could be replaced with creating file archives for each provenance separately and building the
        //       final result on demand, to reduce duplication in the file archives.

        if (archiver == null) {
            logger.warn { "Cannot create missing archives as the archiver is disabled." }
            return
        }

        val provenancesWithMissingArchives = controller.getNestedProvenancesByPackage()
            .filterNot { (_, nestedProvenance) -> archiver.hasArchive(nestedProvenance.root) }

        if (provenancesWithMissingArchives.isEmpty()) return

        logger.info { "Creating file archives for ${provenancesWithMissingArchives.size} package(s)." }

        val duration = measureTime {
            provenancesWithMissingArchives.forEach { (pkg, nestedProvenance) ->
                runCatching {
                    downloadRecursively(nestedProvenance)
                }.onSuccess { dir ->
                    archiver.archive(dir, nestedProvenance.root)
                    dir.safeDeleteRecursively(force = true)
                }.onFailure {
                    controller.addIssue(
                        pkg.id,
                        Issue(
                            source = "Downloader",
                            message = "Could not create file archive for " +
                                    "'${pkg.id.toCoordinates()}': ${it.collectMessages()}",
                            severity = Severity.ERROR
                        )
                    )
                }
            }
        }

        logger.info { "Created file archives for ${provenancesWithMissingArchives.size} package(s) in $duration." }
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
