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
import kotlin.time.measureTimedValue

import org.ossreviewtoolkit.downloader.DownloadException
import org.ossreviewtoolkit.model.AccessStatistics
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.ScanRecord
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.config.ScannerOptions
import org.ossreviewtoolkit.model.config.createFileArchiver
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

        val results = projectResults + packageResults

        val endTime = Instant.now()

        val scanResults = results.entries.associateTo(sortedMapOf()) { it.key.id to it.value.merge() }

        val scanRecord = ScanRecord(
            scanResults = scanResults,
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

    suspend fun scan(packages: Set<Package>, context: ScanContext): Map<Package, NestedProvenanceScanResult> {
        val scanners = scannerWrappers[context.packageType].orEmpty()

        if (scanners.isEmpty()) return emptyMap()

        log.info { "Resolving provenance for ${packages.size} packages." }
        val (packageProvenanceResults, packageProvenanceDuration) = measureTimedValue {
            getPackageProvenances(packages)
        }
        log.info { "Resolved provenance for ${packages.size} packages in $packageProvenanceDuration." }

        val packageProvenances = packageProvenanceResults.mapValuesNotNull { it.value.getOrNull() }

        log.info { "Resolving nested provenances for ${packages.size} packages." }
        val (nestedProvenances, nestedProvenanceDuration) =
            measureTimedValue { getNestedProvenances(packageProvenances) }
        log.info {
            "Resolved nested provenances for ${packages.size} packages in $nestedProvenanceDuration."
        }

        val allKnownProvenances = (
                packageProvenances.values +
                        nestedProvenances.values.flatMap { nestedProvenance ->
                            nestedProvenance.subRepositories.values
                        }
                ).toSet()

        val scanResults = mutableMapOf<ScannerWrapper, MutableMap<KnownProvenance, List<ScanResult>>>()

        // Get stored scan results for each ScannerWrapper by provenance.
        log.info {
            "Reading stored scan results for ${packageProvenances.size} packages with ${allKnownProvenances.size} " +
                    "provenances."
        }
        val (storedResults, readDuration) = measureTimedValue {
            getStoredResults(scanners, allKnownProvenances, nestedProvenances, packageProvenances)
        }

        log.info {
            "Read stored scan results in $readDuration:"
        }
        storedResults.entries.forEach { (scanner, results) ->
            log.info { "\t${scanner.name}: Results for ${results.size} of ${allKnownProvenances.size} provenances." }
        }

        scanResults += storedResults

        // Check which packages have incomplete scan results.
        val packagesWithIncompleteScanResult =
            getPackagesWithIncompleteResults(scanners, packages, packageProvenances, nestedProvenances, scanResults)

        log.info { "${packagesWithIncompleteScanResult.size} packages have incomplete scan results." }

        log.info { "Starting scan of missing packages if any package scanners are configured." }

        // Scan packages with incomplete scan results.
        packagesWithIncompleteScanResult.forEach { (pkg, scanners) ->
            // TODO: Move to function.
            // TODO: Verify that there are still missing scan results for the package, previous scan of another package
            //       from the same repository could have fixed that already.
            scanners.filterIsInstance<PackageScannerWrapper>().forEach { scanner ->
                log.info { "Scanning ${pkg.id.toCoordinates()} with package scanner ${scanner.name}." }

                // Scan whole package with package scanner.
                // TODO: Use coroutines to execute scanners in parallel.
                val scanResult = scanner.scanPackage(pkg, context)

                log.info {
                    "Scan of ${pkg.id.toCoordinates()} with package scanner ${scanner.name} finished."
                }

                // Split scan results by provenance and add them to the map of scan results.
                val nestedProvenanceScanResult =
                    scanResult.toNestedProvenanceScanResult(nestedProvenances.getValue(pkg))
                nestedProvenanceScanResult.scanResults.forEach { (provenance, scanResultsForProvenance) ->
                    val scanResultsForScanner = scanResults.getOrPut(scanner) { mutableMapOf() }
                    scanResultsForScanner[provenance] =
                        scanResultsForScanner[provenance].orEmpty() + scanResultsForProvenance

                    if (scanner.criteria != null) {
                        storageWriters.filterIsInstance<ProvenanceBasedScanStorageWriter>().forEach { writer ->
                            scanResultsForProvenance.forEach { scanResult ->
                                // TODO: Only store new scan results, for nested provenance some of them could have a
                                //       stored result already.
                                try {
                                    writer.write(scanResult)
                                } catch (e: ScanStorageException) {
                                    e.showStackTrace()

                                    log.warn {
                                        "Could not write scan result for ${scanResult.provenance} to " +
                                                "${writer.javaClass.simpleName}: ${e.collectMessagesAsString()}"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Check which provenances have incomplete scan results.
        val provenancesWithIncompleteScanResults =
            getProvenancesWithIncompleteScanResults(scanners, allKnownProvenances, scanResults)

        log.info { "${provenancesWithIncompleteScanResults.size} provenances have incomplete scan results." }

        log.info { "Starting scan of missing provenances if any provenance scanners are configured." }

        provenancesWithIncompleteScanResults.forEach { (provenance, scanners) ->
            // Scan provenances with provenance scanners.
            // TODO: Move to function.
            scanners.filterIsInstance<ProvenanceScannerWrapper>().forEach { scanner ->
                log.info { "Scanning $provenance with provenance scanner ${scanner.name}." }

                // TODO: Use coroutines to execute scanners in parallel.
                val scanResult = scanner.scanProvenance(provenance, context)

                log.info {
                    "Scan of $provenance with provenance scanner ${scanner.name} finished."
                }

                val scanResultsForScanner = scanResults.getOrPut(scanner) { mutableMapOf() }
                scanResultsForScanner[provenance] = scanResultsForScanner[provenance].orEmpty() + scanResult

                if (scanner.criteria != null) {
                    storageWriters.filterIsInstance<ProvenanceBasedScanStorageWriter>().forEach { writer ->
                        try {
                            writer.write(scanResult)
                        } catch (e: ScanStorageException) {
                            e.showStackTrace()

                            log.warn {
                                "Could not write scan result for ${scanResult.provenance} to " +
                                        "${writer.javaClass.simpleName}: ${e.collectMessagesAsString()}"
                            }
                        }
                    }
                }
            }

            // Scan provenances with path scanners.
            val pathScanners = scanners.filterIsInstance<PathScannerWrapper>()
            if (pathScanners.isNotEmpty()) {
                val pathScanResults = scanPath(provenance, pathScanners, context)

                pathScanResults.forEach { (scanner, scanResult) ->
                    val scanResultsForScanner = scanResults.getOrPut(scanner) { mutableMapOf() }
                    scanResultsForScanner[provenance] = scanResultsForScanner[provenance].orEmpty() + scanResult

                    if (scanner.criteria != null) {
                        storageWriters.filterIsInstance<ProvenanceBasedScanStorageWriter>().forEach { writer ->
                            try {
                                writer.write(scanResult)
                            } catch (e: ScanStorageException) {
                                e.showStackTrace()

                                log.warn {
                                    "Could not write scan result for ${scanResult.provenance} to " +
                                            "${writer.javaClass.simpleName}: ${e.collectMessagesAsString()}"
                                }
                            }
                        }
                    }
                }
            }
        }

        val nestedProvenanceScanResults = createNestedProvenanceScanResults(packages, nestedProvenances, scanResults)

        packagesWithIncompleteScanResult.forEach { (pkg, _) ->
            storageWriters.filterIsInstance<PackageBasedScanStorageWriter>().forEach { writer ->
                nestedProvenanceScanResults[pkg]?.let { nestedProvenanceScanResult ->
                    val filteredScanResult = nestedProvenanceScanResult.filter { scanResult ->
                        scanners.find { it.name == scanResult.scanner.name }?.criteria != null
                    }

                    if (filteredScanResult.isComplete()) {
                        try {
                            // TODO: Save only results for scanners which did not have a stored result.
                            writer.write(pkg, filteredScanResult)
                        } catch (e: ScanStorageException) {
                            e.showStackTrace()

                            log.warn {
                                "Could not write scan result for ${pkg.id.toCoordinates()} to " +
                                        "${writer.javaClass.simpleName}: ${e.collectMessagesAsString()}"
                            }
                        }
                    }
                }
            }
        }

        createFileArchives(nestedProvenances)

        return nestedProvenanceScanResults
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

    private fun getPackageProvenances(packages: Set<Package>): Map<Package, Result<KnownProvenance>> =
        packages.associateWith { pkg ->
            runCatching { packageProvenanceResolver.resolveProvenance(pkg, downloaderConfig.sourceCodeOrigins) }
        }

    private fun getNestedProvenances(packageProvenances: Map<Package, Provenance>): Map<Package, NestedProvenance> =
        packageProvenances.mapNotNull { (pkg, provenance) ->
            (provenance as? KnownProvenance)?.let { knownProvenance ->
                pkg to nestedProvenanceResolver.resolveNestedProvenance(knownProvenance)
            }
        }.toMap()

    private fun getStoredResults(
        scannerWrappers: List<ScannerWrapper>,
        provenances: Set<KnownProvenance>,
        nestedProvenances: Map<Package, NestedProvenance>,
        packageProvenances: Map<Package, Provenance>
    ): Map<ScannerWrapper, MutableMap<KnownProvenance, List<ScanResult>>> {
        return scannerWrappers.associateWith { scanner ->
            val scannerCriteria = scanner.criteria ?: return@associateWith mutableMapOf()

            val result = mutableMapOf<KnownProvenance, MutableList<ScanResult>>()

            provenances.forEach { provenance ->
                if (result[provenance] != null) return@forEach

                storageReaders.forEach { reader ->
                    @Suppress("NON_EXHAUSTIVE_WHEN_STATEMENT")
                    when (reader) {
                        is PackageBasedScanStorageReader -> {
                            packageProvenances.entries.find { it.value == provenance }?.key?.let { pkg ->
                                val nestedProvenance = nestedProvenances.getValue(pkg)
                                try {
                                    reader.read(pkg, nestedProvenance, scannerCriteria).forEach { scanResult ->
                                        scanResult.scanResults.forEach { (provenance, scanResults) ->
                                            result.getOrPut(provenance) { mutableListOf() } += scanResults
                                        }
                                    }
                                } catch (e: ScanStorageException) {
                                    e.showStackTrace()

                                    log.warn {
                                        "Could not read scan result for ${pkg.id.toCoordinates()} from " +
                                                "${reader.javaClass.simpleName}: ${e.collectMessagesAsString()}"
                                    }
                                }
                            }
                        }

                        is ProvenanceBasedScanStorageReader -> {
                            try {
                                result.getOrPut(provenance) { mutableListOf() } +=
                                    reader.read(provenance, scannerCriteria)
                            } catch (e: ScanStorageException) {
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

            result.mapValuesTo(mutableMapOf()) { it.value.toList() }
        }
    }

    private fun getPackagesWithIncompleteResults(
        scannerWrappers: List<ScannerWrapper>,
        packages: Set<Package>,
        packageProvenances: Map<Package, Provenance>,
        nestedProvenances: Map<Package, NestedProvenance>,
        scanResults: Map<ScannerWrapper, Map<KnownProvenance, List<ScanResult>>>,
    ): Map<Package, List<ScannerWrapper>> =
        packages.associateWith { pkg ->
            scannerWrappers.filter { scanner ->
                val hasPackageProvenanceResult = scanResults.hasResult(scanner, packageProvenances.getValue(pkg))
                val hasAllNestedProvenanceResults = nestedProvenances[pkg]?.let { nestedProvenance ->
                    scanResults.hasResult(scanner, nestedProvenance.root) &&
                            nestedProvenance.subRepositories.all { (_, provenance) ->
                                scanResults.hasResult(scanner, provenance)
                            }
                } != false

                !hasPackageProvenanceResult || !hasAllNestedProvenanceResults
            }
        }.filterValues { it.isNotEmpty() }

    private fun getProvenancesWithIncompleteScanResults(
        scannerWrappers: List<ScannerWrapper>,
        provenances: Set<KnownProvenance>,
        scanResults: Map<ScannerWrapper, Map<KnownProvenance, List<ScanResult>>>
    ): Map<KnownProvenance, List<ScannerWrapper>> =
        provenances.associateWith { provenance ->
            scannerWrappers.filter { scanner -> !scanResults.hasResult(scanner, provenance) }
        }.filterValues { it.isNotEmpty() }

    private fun createNestedProvenanceScanResults(
        packages: Set<Package>,
        nestedProvenances: Map<Package, NestedProvenance>,
        scanResults: Map<ScannerWrapper, Map<KnownProvenance, List<ScanResult>>>
    ): Map<Package, NestedProvenanceScanResult> =
        packages.associateWith { pkg ->
            val nestedProvenance = nestedProvenances.getValue(pkg)
            val provenances = nestedProvenance.getProvenances()
            val scanResultsByProvenance = provenances.associateWith { provenance ->
                scanResults.values.flatMap { it[provenance].orEmpty() }
            }
            NestedProvenanceScanResult(nestedProvenance, scanResultsByProvenance)
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
    val provenanceByPath = nestedProvenance.subRepositories.toList().toMutableList<Pair<String, KnownProvenance>>()
        .also { it += ("" to nestedProvenance.root) }
        .sortedByDescending { it.first.length }

    val copyrightFindingsByProvenance = summary.copyrightFindings.groupBy { copyrightFinding ->
        provenanceByPath.first { copyrightFinding.location.path.startsWith(it.first) }.second
    }

    val licenseFindingsByProvenance = summary.licenseFindings.groupBy { licenseFinding ->
        provenanceByPath.first { licenseFinding.location.path.startsWith(it.first) }.second
    }

    val provenances = nestedProvenance.getProvenances()
    val scanResultsByProvenance = provenances.associateWith { provenance ->
        // TODO: Find a solution for the incorrect packageVerificationCode and for how to associate issues to the
        //       correct scan result.
        listOf(
            copy(
                summary = summary.copy(
                    licenseFindings = licenseFindingsByProvenance[provenance].orEmpty().toSortedSet(),
                    copyrightFindings = copyrightFindingsByProvenance[provenance].orEmpty().toSortedSet()
                )
            )
        )
    }

    return NestedProvenanceScanResult(nestedProvenance, scanResultsByProvenance)
}

private fun Map<ScannerWrapper, Map<KnownProvenance, List<ScanResult>>>.hasResult(
    scanner: ScannerWrapper,
    provenance: Provenance
) = getValue(scanner)[provenance].let { it != null && it.isNotEmpty() }

private fun <K, V, R> Map<K, V>.mapValuesNotNull(transform: (Map.Entry<K, V>) -> R?): Map<K, R> =
    mutableMapOf<K, R>().apply {
        this@mapValuesNotNull.forEach { entry ->
            transform(entry)?.let { put(entry.key, it) }
        }
    }
