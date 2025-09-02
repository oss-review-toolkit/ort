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
import java.time.Instant

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.measureTime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.downloader.DownloadException
import org.ossreviewtoolkit.model.FileList
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.ProvenanceResolutionResult
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.config.createFileArchiver
import org.ossreviewtoolkit.model.config.createStorage
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.mapLicense
import org.ossreviewtoolkit.model.utils.FileArchiver
import org.ossreviewtoolkit.model.utils.ProvenanceFileStorage
import org.ossreviewtoolkit.model.utils.vcsPath
import org.ossreviewtoolkit.scanner.provenance.NestedProvenance
import org.ossreviewtoolkit.scanner.provenance.NestedProvenanceResolver
import org.ossreviewtoolkit.scanner.provenance.NestedProvenanceScanResult
import org.ossreviewtoolkit.scanner.provenance.PackageProvenanceResolver
import org.ossreviewtoolkit.scanner.provenance.ProvenanceDownloader
import org.ossreviewtoolkit.scanner.utils.FileListResolver
import org.ossreviewtoolkit.scanner.utils.alignRevisions
import org.ossreviewtoolkit.scanner.utils.filterScanResultsByVcsPaths
import org.ossreviewtoolkit.scanner.utils.getVcsPathsForProvenances
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.ort.showStackTrace
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.toSpdx

class Scanner(
    val scannerConfig: ScannerConfiguration,
    val downloaderConfig: DownloaderConfiguration,
    val provenanceDownloader: ProvenanceDownloader,
    val storageReaders: List<ScanStorageReader>,
    val storageWriters: List<ScanStorageWriter>,
    val packageProvenanceResolver: PackageProvenanceResolver,
    val nestedProvenanceResolver: NestedProvenanceResolver,
    val scannerWrappers: Map<PackageType, List<ScannerWrapper>>,
    private val archiver: FileArchiver? = scannerConfig.archive.createFileArchiver(),
    fileListStorage: ProvenanceFileStorage = scannerConfig.fileListStorage.createStorage()
) {
    init {
        require(scannerWrappers.isNotEmpty() && scannerWrappers.any { it.value.isNotEmpty() }) {
            "At least one ScannerWrapper must be provided."
        }

        scannerWrappers.values.flatten().distinct().forEach { scannerWrapper ->
            scannerWrapper.matcher?.let { matcher ->
                require(matcher.matches(scannerWrapper.details)) {
                    "The scanner details of scanner '${scannerWrapper.details.name}' must satisfy the configured " +
                        "criteria for looking up scan storage entries."
                }
            }
        }
    }

    private val fileListResolver = FileListResolver(
        storage = fileListStorage,
        provenanceDownloader = provenanceDownloader
    )

    suspend fun scan(ortResult: OrtResult, skipExcluded: Boolean, labels: Map<String, String>): OrtResult {
        val projectPackages = ortResult.getProjects(skipExcluded).mapTo(mutableSetOf()) { it.toPackage() }
        val projectResults = scan(
            projectPackages,
            ScanContext(
                ortResult.labels + labels,
                PackageType.PROJECT,
                ortResult.repository.config.excludes,
                ortResult.repository.config.includes,
                scannerConfig.detectedLicenseMapping,
                snippetChoices = ortResult.repository.config.snippetChoices
            )
        )

        val packages = ortResult.getPackages(skipExcluded).map { it.metadata }.filterNotConcluded()
            .filterNotMetadataOnly().toSet()
        val packageResults = scan(
            packages,
            ScanContext(
                ortResult.labels,
                PackageType.PACKAGE,
                ortResult.repository.config.excludes,
                ortResult.repository.config.includes,
                scannerConfig.detectedLicenseMapping,
                snippetChoices = ortResult.repository.config.snippetChoices
            )
        )

        val scannerRun = listOfNotNull(projectResults, packageResults).reduce { a, b -> a + b }

        val paddedScannerRun = scannerRun.takeUnless { scannerConfig.includeFilesWithoutFindings }
            ?: scannerRun.padNoneLicenseFindings()

        return ortResult.copy(scanner = paddedScannerRun)
    }

    internal suspend fun scan(packages: Set<Package>, context: ScanContext): ScannerRun? {
        val scannerWrappers = scannerWrappers[context.packageType]
        if (scannerWrappers.isNullOrEmpty()) {
            logger.info { "Skipping ${context.packageType} scan as no according scanner is configured." }
            return null
        }

        logger.info { "Scanning ${packages.size} ${context.packageType}(s) with ${scannerWrappers.size} scanner(s)." }

        val controller = ScanController(packages, scannerWrappers, scannerConfig)

        val startTime = Instant.now()

        resolvePackageProvenances(controller)
        resolveNestedProvenances(controller)

        readStoredResults(controller)

        runPackageScanners(controller, context)
        runProvenanceScanners(controller, context)
        runPathScanners(controller, context)

        createFileLists(controller)
        createMissingArchives(controller)

        val endTime = Instant.now()

        val provenances = packages.mapTo(mutableSetOf()) { pkg ->
            val packageProvenance = controller.getPackageProvenance(pkg.id)

            ProvenanceResolutionResult(
                id = pkg.id,
                packageProvenance = packageProvenance,
                subRepositories = controller.getSubRepositories(pkg.id),
                packageProvenanceResolutionIssue = controller.getPackageProvenanceResolutionIssue(pkg.id),
                nestedProvenanceResolutionIssue = controller.getNestedProvenanceResolutionIssue(pkg.id)
            ).filterByVcsPath()
        }

        val vcsPathsForProvenances = getVcsPathsForProvenances(provenances)

        val scanResults = filterScanResultsByVcsPaths(controller.getAllScanResults(), vcsPathsForProvenances)

        val files = controller.getAllFileLists().mapTo(mutableSetOf()) { (provenance, fileList) ->
            FileList(
                provenance = provenance.alignRevisions() as KnownProvenance,
                files = fileList.files.mapTo(mutableSetOf()) { (path, sha1) ->
                    FileList.Entry(path, sha1)
                }
            )
        }.mapNotNullTo(mutableSetOf()) { fileList ->
            vcsPathsForProvenances[fileList.provenance]?.let {
                fileList.filterByVcsPaths(it)
            }
        }

        val scannerIds = scannerWrappers.mapTo(mutableSetOf()) { it.descriptor.id }
        val scanners = packages.associateBy({ it.id }) { scannerIds }

        val issues = controller.getIssues()

        return ScannerRun(
            startTime = startTime,
            endTime = endTime,
            environment = Environment(),
            config = scannerConfig,
            provenances = provenances,
            scanResults = scanResults,
            files = files,
            scanners = scanners,
            issues = issues
        )
    }

    private suspend fun resolvePackageProvenances(controller: ScanController) {
        logger.info { "Resolving provenance for ${controller.packages.size} package(s)." }

        val duration = measureTime {
            withContext(Dispatchers.IO.limitedParallelism(20)) {
                controller.packages.map { pkg ->
                    async {
                        pkg to runCatching {
                            packageProvenanceResolver.resolveProvenance(pkg, downloaderConfig.sourceCodeOrigins)
                        }.onFailure {
                            if (it is CancellationException) currentCoroutineContext().ensureActive()
                        }
                    }
                }.awaitAll()
            }.forEach { (pkg, result) ->
                result.onSuccess { provenance ->
                    controller.putPackageProvenance(pkg.id, provenance)
                }.onFailure {
                    controller.putPackageProvenanceResolutionIssue(
                        pkg.id,
                        Issue(source = "Scanner", message = it.collectMessages())
                    )
                }
            }
        }

        logger.info { "Resolved provenance for ${controller.packages.size} package(s) in $duration." }
    }

    private suspend fun resolveNestedProvenances(controller: ScanController) {
        logger.info { "Resolving nested provenances for ${controller.packages.size} package(s)." }

        val duration = measureTime {
            withContext(Dispatchers.IO.limitedParallelism(20)) {
                controller.getPackageProvenancesWithoutVcsPath().map { provenance ->
                    async {
                        provenance to runCatching {
                            nestedProvenanceResolver.resolveNestedProvenance(provenance)
                        }.onFailure {
                            if (it is CancellationException) currentCoroutineContext().ensureActive()
                        }
                    }
                }.awaitAll()
            }.forEach { (provenance, result) ->
                result.onSuccess { nestedProvenance ->
                    controller.putNestedProvenance(provenance, nestedProvenance)
                }.onFailure {
                    controller.getPackagesForProvenanceWithoutVcsPath(provenance).forEach { id ->
                        controller.putNestedProvenanceResolutionIssue(
                            id,
                            Issue(
                                source = "Scanner",
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
                                "'${scanner.descriptor.displayName}' as no nested provenance for the package could " +
                                "be resolved."
                        }
                    }

                    val hasCompleteScanResult = controller.hasCompleteScanResult(scanner, pkg)
                    if (hasCompleteScanResult) {
                        logger.debug {
                            "Skipping scan of '${pkg.id.toCoordinates()}' with package scanner " +
                                "'${scanner.descriptor.displayName}' as stored results are available."
                        }
                    }

                    hasNestedProvenance && !hasCompleteScanResult
                }

                if (packagesWithIncompleteScanResult.isEmpty()) {
                    logger.info {
                        "Skipping scan with package scanner '${scanner.descriptor.displayName}' as all packages have " +
                            "results."
                    }

                    return@scanner
                }

                val referencePackage = packagesWithIncompleteScanResult.first()
                val nestedProvenance = controller.getNestedProvenance(referencePackage.id) ?: return@scanner

                val adjustedContext = context.copy(
                    // Hide includes and excludes from scanners with a scanner matcher.
                    excludes = context.excludes.takeUnless { scanner.matcher != null },
                    includes = context.includes.takeUnless { scanner.matcher != null },
                    // Tell scanners also about the non-reference packages.
                    coveredPackages = packagesWithIncompleteScanResult
                )

                logger.info {
                    val coveredCoordinates = adjustedContext.coveredPackages.joinToString { it.id.toCoordinates() }
                    "Starting scan of ${nestedProvenance.root} with package scanner " +
                        "'${scanner.descriptor.displayName}' which covers the following packages: $coveredCoordinates"
                }

                runCatching {
                    scanner.scanPackage(nestedProvenance, adjustedContext)
                }.onSuccess { scanResult ->
                    logger.info {
                        "Finished scan of ${nestedProvenance.root} with package scanner " +
                            "'${scanner.descriptor.displayName}'."
                    }

                    val provenanceScanResultsToStore = mutableSetOf<Pair<KnownProvenance, ScanResult>>()
                    packagesWithIncompleteScanResult.forEach { pkg ->
                        val nestedProvenanceScanResult = scanResult.toNestedProvenanceScanResult(nestedProvenance)
                        controller.addNestedScanResult(scanner, nestedProvenanceScanResult)

                        // TODO: Run in coroutine.
                        if (scanner.writeToStorage) {
                            storePackageScanResult(pkg, nestedProvenanceScanResult)

                            nestedProvenanceScanResult.scanResults.forEach { (provenance, scanResults) ->
                                scanResults.forEach { scanResult ->
                                    provenanceScanResultsToStore += provenance to scanResult
                                }
                            }
                        }
                    }

                    // Store only deduplicated provenance scan results.
                    provenanceScanResultsToStore.forEach { (provenance, scanResult) ->
                        storeProvenanceScanResult(provenance, scanResult)
                    }
                }.onFailure { e ->
                    val issue = scanner.createAndLogIssue(
                        "Failed to scan $provenance with package scanner '${scanner.descriptor.displayName}': " +
                            e.collectMessages()
                    )

                    controller.getIdsByProvenance().getValue(provenance).forEach { id ->
                        controller.addIssue(
                            id,
                            issue
                        )
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
                            "'${scanner.descriptor.displayName}' as a result is already available."
                    }

                    return@scanner
                }

                logger.info {
                    "Scanning $provenance (${index + 1} of ${provenances.size}) with provenance scanner " +
                        "'${scanner.descriptor.displayName}'."
                }

                // Filter the scan context to hide the includes and excludes from scanner with scan matcher.
                val filteredContext = if (scanner.matcher == null) {
                    context
                } else {
                    context.copy(excludes = null, includes = null)
                }

                runCatching {
                    scanner.scanProvenance(provenance, filteredContext)
                }.onSuccess { scanResult ->
                    val completedPackages = controller.getPackagesCompletedByProvenance(scanner, provenance)

                    controller.addScanResults(scanner, provenance, listOf(scanResult))

                    storeProvenanceScanResult(provenance, scanResult)

                    completedPackages.forEach { pkg ->
                        controller.getNestedScanResult(pkg.id)?.let { storePackageScanResult(pkg, it) }
                    }
                }.onFailure { e ->
                    val issue = scanner.createAndLogIssue(
                        "Failed to scan $provenance with provenance scanner '${scanner.descriptor.displayName}': " +
                            e.collectMessages()
                    )

                    controller.getIdsByProvenance().getValue(provenance).forEach { id ->
                        controller.addIssue(
                            id,
                            issue
                        )
                    }
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

            val scanResults = scanPath(provenance, scannersWithoutResults, context, controller)

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
                "\t${scanner.descriptor.displayName}: Result(s) for ${results.size} of ${allKnownProvenances.size} " +
                    "provenance(s)."
            }
        }
    }

    private fun readStoredPackageResults(controller: ScanController) {
        controller.scanners.forEach { scanner ->
            val scannerMatcher = scanner.matcher ?: return@forEach
            if (!scanner.readFromStorage) return@forEach

            controller.packages.forEach pkg@{ pkg ->
                val nestedProvenance = controller.getNestedProvenance(pkg.id) ?: return@pkg

                storageReaders.filterIsInstance<PackageBasedScanStorageReader>().forEach { reader ->
                    if (controller.hasCompleteScanResult(scanner, pkg)) return@pkg

                    runCatching {
                        reader.read(pkg, nestedProvenance, scannerMatcher)
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
            val scannerMatcher = scanner.matcher ?: return@forEach
            if (!scanner.readFromStorage) return@forEach

            controller.getAllProvenances().forEach provenance@{ provenance ->
                if (controller.hasScanResult(scanner, provenance)) return@provenance

                storageReaders.filterIsInstance<ProvenanceBasedScanStorageReader>().forEach { reader ->
                    runCatching {
                        reader.read(provenance, scannerMatcher)
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
        context: ScanContext,
        controller: ScanController
    ): Map<PathScannerWrapper, ScanResult> {
        val downloadDir = try {
            provenanceDownloader.download(provenance)
        } catch (e: DownloadException) {
            val issue = createAndLogIssue(
                "Downloader", "Could not download provenance $provenance: ${e.collectMessages()}"
            )

            controller.getIdsByProvenance().getValue(provenance).forEach { id ->
                controller.addIssue(
                    id,
                    issue
                )
            }

            return emptyMap()
        }

        val results = scanners.mapNotNull { scanner ->
            logger.info { "Scan of $provenance with path scanner '${scanner.descriptor.displayName}' started." }

            // Filter the scan context to hide the includes and excludes from scanner with scan matcher.
            val filteredContext = if (scanner.matcher == null) {
                context
            } else {
                context.copy(excludes = null, includes = null)
            }

            val summary = runCatching {
                scanner.scanPath(downloadDir, filteredContext)
            }.onFailure { e ->
                val issue = scanner.createAndLogIssue(
                    "Failed to scan $provenance with path scanner '${scanner.descriptor.displayName}': " +
                        e.collectMessages()
                )

                controller.getIdsByProvenance().getValue(provenance).forEach { id ->
                    controller.addIssue(
                        id,
                        issue
                    )
                }
            }.getOrNull()

            logger.info { "Scan of $provenance with path scanner '${scanner.descriptor.displayName}' finished." }

            summary?.let {
                val summaryWithMappedLicenses = summary.copy(
                    licenseFindings = summary.licenseFindings.mapTo(mutableSetOf()) { finding ->
                        val licenseString = finding.license.toString()
                        finding.copy(
                            license = licenseString.mapLicense(scannerConfig.detectedLicenseMapping).toSpdx(),
                            location = finding.location.withRelativePath(downloadDir)
                        )
                    }
                )

                scanner to ScanResult(provenance, scanner.details, summaryWithMappedLicenses)
            }
        }.toMap()

        downloadDir.safeDeleteRecursively()

        return results
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

    private suspend fun createFileLists(controller: ScanController) {
        val idsByProvenance = controller.getIdsByProvenance()
        val provenances = idsByProvenance.keys

        logger.info { "Creating file lists for ${provenances.size} provenances." }

        val duration = measureTime {
            withContext(Dispatchers.IO.limitedParallelism(20)) {
                provenances.mapIndexed { index, provenance ->
                    async {
                        logger.info {
                            "Creating file list for provenance ${index + 1} of ${provenances.size}."
                        }

                        runCatching {
                            val fileList = fileListResolver.resolve(provenance)
                            controller.putFileList(provenance, fileList)
                        }.onFailure {
                            idsByProvenance.getValue(provenance).forEach { id ->
                                controller.addIssue(
                                    id,
                                    Issue(
                                        source = "Downloader",
                                        message = "Could not create file list for " +
                                            "'${id.toCoordinates()}': ${it.collectMessages()}"
                                    )
                                )
                            }
                        }
                    }
                }.awaitAll()
            }
        }

        logger.info { "Created file lists for ${provenances.size} provenances in $duration." }
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
                var dir: File? = null

                // runCatching has a bug with smart-cast, see https://youtrack.jetbrains.com/issue/KT-62938.
                try {
                    dir = provenanceDownloader.downloadRecursively(nestedProvenance)
                    archiver.archive(dir, nestedProvenance.root)
                } catch (e: DownloadException) {
                    controller.addIssue(
                        pkg.id,
                        Issue(
                            source = "Scanner",
                            message = "Could not create file archive for '${pkg.id.toCoordinates()}': "
                                + e.collectMessages()
                        )
                    )
                } finally {
                    dir?.safeDeleteRecursively()
                }
            }
        }

        logger.info { "Created file archives for ${provenancesWithMissingArchives.size} package(s) in $duration." }
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

    val scanResultsByProvenance = nestedProvenance.allProvenances.associateWith { provenance ->
        // TODO: Find a solution for how to associate issues to the correct scan result.
        listOf(
            copy(
                provenance = provenance,
                summary = summary.copy(
                    licenseFindings = licenseFindingsByProvenance[provenance].orEmpty().toSet(),
                    copyrightFindings = copyrightFindingsByProvenance[provenance].orEmpty().toSet()
                )
            )
        )
    }

    return NestedProvenanceScanResult(nestedProvenance, scanResultsByProvenance)
}

private fun ScanController.getSubRepositories(id: Identifier): Map<String, VcsInfo> {
    val nestedProvenance = getNestedProvenance(id) ?: return emptyMap()

    return nestedProvenance.subRepositories.entries.associate { (path, provenance) -> path to provenance.vcsInfo }
}

private fun ProvenanceResolutionResult.filterByVcsPath(): ProvenanceResolutionResult =
    copy(
        subRepositories = subRepositories.filter { (path, _) ->
            File(path).startsWith(packageProvenance.vcsPath)
        }
    )

private fun FileList.filterByVcsPaths(paths: Collection<String>): FileList =
    if (paths.any { it.isBlank() }) {
        this
    } else {
        copy(
            files = files.filterTo(mutableSetOf()) { file ->
                paths.any { filterPath ->
                    file.path.startsWith("$filterPath/")
                }
            }
        )
    }

internal fun ScannerRun.padNoneLicenseFindings(): ScannerRun {
    val fileListByProvenance = files.associateBy { it.provenance }

    val paddedScanResults = scanResults.mapTo(mutableSetOf()) { scanResult ->
        val allPaths = fileListByProvenance[scanResult.provenance]?.files?.mapTo(mutableSetOf()) {
            it.path
        }.orEmpty()

        scanResult.padNoneLicenseFindings(allPaths)
    }

    return copy(scanResults = paddedScanResults)
}

internal fun ScanResult.padNoneLicenseFindings(paths: Set<String>): ScanResult {
    val pathsWithFindings = summary.licenseFindings.mapTo(mutableSetOf()) { it.location.path }
    val pathsWithoutFindings = paths - pathsWithFindings

    val findingsThatAreNone = pathsWithoutFindings.mapTo(mutableSetOf()) {
        LicenseFinding(SpdxConstants.NONE, TextLocation(it, TextLocation.UNKNOWN_LINE))
    }

    return copy(
        summary = summary.copy(
            licenseFindings = summary.licenseFindings + findingsThatAreNone
        )
    )
}
