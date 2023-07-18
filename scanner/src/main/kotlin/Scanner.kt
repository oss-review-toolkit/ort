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
import org.ossreviewtoolkit.model.FileList
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.ProvenanceResolutionResult
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.Options
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.config.createFileArchiver
import org.ossreviewtoolkit.model.config.createStorage
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.mapLicense
import org.ossreviewtoolkit.model.utils.FileArchiver
import org.ossreviewtoolkit.model.utils.ProvenanceFileStorage
import org.ossreviewtoolkit.model.utils.getKnownProvenancesWithoutVcsPath
import org.ossreviewtoolkit.model.utils.vcsPath
import org.ossreviewtoolkit.scanner.provenance.NestedProvenance
import org.ossreviewtoolkit.scanner.provenance.NestedProvenanceResolver
import org.ossreviewtoolkit.scanner.provenance.NestedProvenanceScanResult
import org.ossreviewtoolkit.scanner.provenance.PackageProvenanceResolver
import org.ossreviewtoolkit.scanner.provenance.ProvenanceDownloader
import org.ossreviewtoolkit.scanner.utils.FileListResolver
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.ort.showStackTrace
import org.ossreviewtoolkit.utils.spdx.toSpdx

const val TOOL_NAME = "scanner"

@Suppress("TooManyFunctions")
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
    private companion object : Logging

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

    private val fileListResolver = FileListResolver(
        storage = fileListStorage,
        provenanceDownloader = provenanceDownloader
    )

    suspend fun scan(ortResult: OrtResult, skipExcluded: Boolean, labels: Map<String, String>): OrtResult {
        val startTime = Instant.now()

        val projectScannerWrappers = scannerWrappers[PackageType.PROJECT].orEmpty()
        val packageScannerWrappers = scannerWrappers[PackageType.PACKAGE].orEmpty()

        val projectResults = if (projectScannerWrappers.isNotEmpty()) {
            val packages = ortResult.getProjects(skipExcluded).mapTo(mutableSetOf()) { it.toPackage() }

            logger.info { "Scanning ${packages.size} project(s) with ${projectScannerWrappers.size} scanner(s)." }

            scan(
                packages,
                ScanContext(
                    ortResult.labels + labels,
                    PackageType.PROJECT,
                    ortResult.repository.config.excludes
                )
            )
        } else {
            logger.info { "Skipping project scan as no project scanner is configured." }

            ScannerRun.EMPTY
        }

        val packageResults = if (packageScannerWrappers.isNotEmpty()) {
            val packages = ortResult.getPackages(skipExcluded).map { it.metadata }.filterNotConcluded()
                .filterNotMetadataOnly().toSet()

            logger.info { "Scanning ${packages.size} package(s) with ${packageScannerWrappers.size} scanner(s)." }

            scan(packages, ScanContext(ortResult.labels, PackageType.PACKAGE, ortResult.repository.config.excludes))
        } else {
            logger.info { "Skipping package scan as no package scanner is configured." }

            ScannerRun.EMPTY
        }

        val endTime = Instant.now()

        val filteredScannerOptions = mutableMapOf<String, Options>()

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

        val scannerRun = ScannerRun(
            startTime = startTime,
            endTime = endTime,
            environment = Environment(),
            config = filteredScannerConfig,
            provenances = projectResults.provenances + packageResults.provenances,
            scanResults = projectResults.scanResults + packageResults.scanResults,
            files = projectResults.files + packageResults.files,
            scanners = projectResults.scanners + packageResults.scanners
        )

        return ortResult.copy(scanner = scannerRun)
    }

    suspend fun scan(packages: Set<Package>, context: ScanContext): ScannerRun {
        val scannerWrappers = scannerWrappers[context.packageType]
        if (scannerWrappers.isNullOrEmpty()) return ScannerRun.EMPTY

        val controller = ScanController(packages, scannerWrappers, scannerConfig)

        resolvePackageProvenances(controller)
        resolveNestedProvenances(controller)

        readStoredResults(controller)

        runPackageScanners(controller, context)
        runProvenanceScanners(controller, context)
        runPathScanners(controller, context)

        createFileLists(controller)
        createMissingArchives(controller)

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

        val vcsPathsForProvenances = buildMap<KnownProvenance, MutableSet<String>> {
            provenances.forEach { provenance ->
                val packageVcsPath = provenance.packageProvenance?.vcsPath.orEmpty()

                provenance.getKnownProvenancesWithoutVcsPath().forEach { (repositoryPath, provenance) ->
                    getVcsPathForRepositoryOrNull(packageVcsPath, repositoryPath)?.let { vcsPath ->
                        getOrPut(provenance) { mutableSetOf() } += vcsPath
                    }
                }
            }
        }

        val scanResults = controller.getAllScanResults().map { scanResult ->
            scanResult.copy(provenance = scanResult.provenance.alignRevisions())
        }.mapNotNullTo(mutableSetOf()) { scanResult ->
            vcsPathsForProvenances[scanResult.provenance]?.let {
                scanResult.copy(summary = scanResult.summary.filterByPaths(it))
            }
        }

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

        val scannerNames = scannerWrappers.mapTo(mutableSetOf()) { it.name }
        val scanners = packages.associateBy({ it.id }) { scannerNames }

        return ScannerRun.EMPTY.copy(
            config = scannerConfig,
            provenances = provenances,
            scanResults = scanResults,
            files = files,
            scanners = scanners
        )
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
                    controller.putPackageProvenance(pkg.id, provenance)
                }.onFailure {
                    controller.putPackageProvenanceResolutionIssue(
                        pkg.id,
                        Issue(source = TOOL_NAME, message = it.collectMessages())
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
                    controller.putNestedProvenance(provenance, nestedProvenance)
                }.onFailure {
                    controller.getPackagesForProvenanceWithoutVcsPath(provenance).forEach { id ->
                        controller.putNestedProvenanceResolutionIssue(
                            id,
                            Issue(
                                source = TOOL_NAME,
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
                            "Skipping scan of '${pkg.id.toCoordinates()}' with package scanner '${scanner.name}' as " +
                                    "no nested provenance for the package could be resolved."
                        }
                    }

                    val hasCompleteScanResult = controller.hasCompleteScanResult(scanner, pkg)
                    if (hasCompleteScanResult) {
                        logger.debug {
                            "Skipping scan of '${pkg.id.toCoordinates()}' with package scanner '${scanner.name}' as " +
                                    "stored results are available."
                        }
                    }

                    hasNestedProvenance && !hasCompleteScanResult
                }

                if (packagesWithIncompleteScanResult.isEmpty()) {
                    logger.info {
                        "Skipping scan with package scanner '${scanner.name}' as all packages have results."
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
                    "Scan of '${referencePackage.id.toCoordinates()}' with package scanner '${scanner.name} started."
                }

                // Filter the scan context to hide the excludes from scanner with scan criteria.
                val filteredContext = if (scanner.criteria == null) context else context.copy(excludes = null)
                val scanResult = scanner.scanPackage(referencePackage, filteredContext)

                logger.info {
                    "Scan of '${referencePackage.id.toCoordinates()}' with package scanner '${scanner.name}' finished."
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
                                "'${scanner.name}' as a result is already available."
                    }

                    return@scanner
                }

                logger.info {
                    "Scanning $provenance (${index + 1} of ${provenances.size}) with provenance scanner " +
                            "'${scanner.name}'."
                }

                // Filter the scan context to hide the excludes from scanner with scan criteria.
                val filteredContext = if (scanner.criteria == null) context else context.copy(excludes = null)
                val scanResult = scanner.scanProvenance(provenance, filteredContext)

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
                "\t${scanner.name}: Result(s) for ${results.size} of ${allKnownProvenances.size} provenance(s)."
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
                logger.info { "Scan of $provenance with path scanner '${scanner.name}' started." }

                // Filter the scan context to hide the excludes from scanner with scan criteria.
                val filteredContext = if (scanner.criteria == null) context else context.copy(excludes = null)
                val summary = scanner.scanPath(downloadDir, filteredContext)

                logger.info { "Scan of $provenance with path scanner '${scanner.name}' finished." }

                ScanResult(provenance, scanner.details, postProcessScanSummary(summary, downloadDir))
            }
        } finally {
            downloadDir.safeDeleteRecursively(force = true)
        }
    }

    private fun postProcessScanSummary(summary: ScanSummary, downloadDir: File) =
        summary.copy(
            licenseFindings = summary.licenseFindings.mapTo(mutableSetOf()) {
                val licenseString = it.license.toString()
                it.copy(
                    license = licenseString.mapLicense(scannerConfig.detectedLicenseMapping).toSpdx(),
                    location = it.location.withRelativePath(downloadDir)
                )
            }
        )

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

    private suspend fun createFileLists(controller: ScanController) {
        val idsByProvenance = controller.getIdsByProvenance()
        val provenances = idsByProvenance.keys

        logger.info { "Creating file lists for ${provenances.size} provenances." }

        val duration = measureTime {
            withContext(Dispatchers.IO) {
                provenances.mapIndexed { index, provenance ->
                    async {
                        logger.info {
                            "Creating file list for provenance $index of ${provenances.size}."
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
                                    "'${pkg.id.toCoordinates()}': ${it.collectMessages()}"
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

fun <T : Provenance> T.alignRevisions(): Provenance =
    if (this is RepositoryProvenance) {
        copy(vcsInfo = vcsInfo.copy(revision = resolvedRevision))
    } else {
        this
    }

private fun ScanController.getSubRepositories(id: Identifier): Map<String, VcsInfo> {
    val nestedProvenance = getNestedProvenance(id) ?: return emptyMap()

    return nestedProvenance.subRepositories.entries.associate { (path, provenance) -> path to provenance.vcsInfo }
}

private fun ProvenanceResolutionResult.filterByVcsPath(): ProvenanceResolutionResult =
    copy(
        subRepositories = subRepositories.filter { (path, _) ->
            File(path).startsWith(packageProvenance?.vcsPath.orEmpty())
        }
    )

/**
 * Return the VCS path applicable to a (sub-) repository which appears under [repositoryPath] in the source tree of
 * a package residing in [vcsPath], or null if the subtrees for [repositoryPath] and [vcsPath] are disjoint.
 */
private fun getVcsPathForRepositoryOrNull(vcsPath: String, repositoryPath: String): String? {
    val repoPathFile = File(repositoryPath)
    val vcsPathFile = File(vcsPath)

    return if (repoPathFile.startsWith(vcsPathFile)) {
        ""
    } else {
        runCatching { vcsPathFile.toRelativeString(repoPathFile) }.getOrNull()
    }
}

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
