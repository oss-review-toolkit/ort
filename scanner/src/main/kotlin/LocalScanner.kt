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

import com.fasterxml.jackson.databind.JsonNode

import com.vdurmont.semver4j.Requirement

import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.concurrent.Executors

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

import org.ossreviewtoolkit.downloader.DownloadException
import org.ossreviewtoolkit.downloader.Downloader
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.EMPTY_JSON_NODE
import org.ossreviewtoolkit.model.Environment
import org.ossreviewtoolkit.model.Failure
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.ScanRecord
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanResultContainer
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.Success
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.mapper
import org.ossreviewtoolkit.scanner.storages.PostgresStorage
import org.ossreviewtoolkit.utils.CommandLineTool
import org.ossreviewtoolkit.utils.NamedThreadFactory
import org.ossreviewtoolkit.utils.Os
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.fileSystemEncode
import org.ossreviewtoolkit.utils.getPathFromEnvironment
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.safeMkdirs
import org.ossreviewtoolkit.utils.showStackTrace
import org.ossreviewtoolkit.utils.storage.FileArchiver

/**
 * Implementation of [Scanner] for scanners that operate locally. Packages passed to [scanPackages] are processed in
 * serial order. Scan results can be stored in a [ScanResultsStorage].
 */
abstract class LocalScanner(name: String, config: ScannerConfiguration) : Scanner(name, config), CommandLineTool {
    private val archiver by lazy {
        config.archive?.createFileArchiver() ?: FileArchiver.DEFAULT
    }

    /**
     * A property containing the file name extension of the scanner's native output format, without the dot.
     */
    abstract val resultFileExt: String

    /**
     * The directory the scanner was bootstrapped to, if so.
     */
    private val scannerDir by lazy {
        val scannerExe = command()

        getPathFromEnvironment(scannerExe)?.parentFile?.takeIf {
            getVersion(it) == scannerVersion
        } ?: run {
            if (scannerExe.isNotEmpty()) {
                log.info {
                    "Bootstrapping scanner '$scannerName' as required version $scannerVersion was not found in PATH."
                }

                bootstrap().also {
                    val actualScannerVersion = getVersion(it)
                    if (actualScannerVersion != scannerVersion) {
                        throw IOException(
                            "Bootstrapped scanner version $actualScannerVersion " +
                                    "does not match expected version $scannerVersion."
                        )
                    }
                }
            } else {
                log.info { "Skipping to bootstrap scanner '$scannerName' as it has no executable." }

                File("")
            }
        }
    }

    /**
     * The required version of the scanner. This is also the version that would get bootstrapped.
     */
    protected abstract val scannerVersion: String

    /**
     * The full path to the scanner executable.
     */
    protected val scannerPath by lazy { scannerDir.resolve(command()) }

    override fun getVersionRequirement(): Requirement = Requirement.buildLoose(scannerVersion)

    /**
     * Return the actual version of the scanner, or an empty string in case of failure.
     */
    open fun getVersion() = getVersion(scannerDir)

    /**
     * Bootstrap the scanner to be ready for use, like downloading and / or configuring it.
     *
     * @return The directory the scanner is installed in.
     */
    protected open fun bootstrap(): File = throw NotImplementedError()

    /**
     * Return the configuration of this [LocalScanner].
     */
    abstract fun getConfiguration(): String

    /**
     * Return the [ScannerDetails] of this [LocalScanner].
     */
    fun getDetails() = ScannerDetails(scannerName, getVersion(), getConfiguration())

    override suspend fun scanPackages(packages: List<Package>, outputDirectory: File, downloadDirectory: File):
            Map<Package, List<ScanResult>> {

        val storageDispatcher =
            Executors.newFixedThreadPool(5, NamedThreadFactory(ScanResultsStorage.storage.name)).asCoroutineDispatcher()
        val scanDispatcher = Executors.newSingleThreadExecutor(NamedThreadFactory(scannerName)).asCoroutineDispatcher()

        return try {
            coroutineScope {
                packages.withIndex().map { (index, pkg) ->
                    val packageIndex = "(${index + 1} of ${packages.size})"

                    async {
                        pkg to scanPackage(
                            pkg,
                            packageIndex,
                            downloadDirectory,
                            outputDirectory,
                            storageDispatcher,
                            scanDispatcher
                        )
                    }
                }.associate { it.await() }
            }
        } finally {
            storageDispatcher.close()
            scanDispatcher.close()
        }
    }

    /**
     * Return the [ScanResult]s for a single package.
     */
    private suspend fun scanPackage(
        pkg: Package,
        packageIndex: String,
        downloadDirectory: File,
        outputDirectory: File,
        storageDispatcher: CoroutineDispatcher,
        scanDispatcher: CoroutineDispatcher
    ): List<ScanResult> {
        val scannerDetails = getDetails()

        if (pkg.isMetaDataOnly) {
            log.info { "Skipping '${pkg.id.toCoordinates()}' as it is meta data only." }

            return emptyList()
        }

        return try {
            val storedResults = withContext(storageDispatcher) {
                log.info {
                    "Looking for stored scan results for ${pkg.id.toCoordinates()} and " +
                            "$scannerDetails $packageIndex."
                }

                readFromStorage(scannerDetails, pkg, outputDirectory)
            }

            if (storedResults.isNotEmpty()) {
                log.info {
                    "Found ${storedResults.size} stored scan result(s) for ${pkg.id.toCoordinates()} " +
                            "and $scannerDetails, not scanning the package again $packageIndex."
                }

                storedResults
            } else {
                withContext(scanDispatcher) {
                    log.info {
                        "No stored result found for ${pkg.id.toCoordinates()} and $scannerDetails, " +
                                "scanning package in thread '${Thread.currentThread().name}' " +
                                "$packageIndex."
                    }

                    listOf(
                        scanPackage(scannerDetails, pkg, outputDirectory, downloadDirectory).also {
                            log.info {
                                "Finished scanning ${pkg.id.toCoordinates()} in thread " +
                                        "'${Thread.currentThread().name}' $packageIndex."
                            }
                        }
                    )
                }
            }.map {
                // Remove the now unneeded reference to rawResult here to allow garbage collection to
                // clean it up.
                it.copy(rawResult = null)
            }
        } catch (e: ScanException) {
            e.showStackTrace()

            val issue = createAndLogIssue(
                source = scannerName,
                message = "Could not scan '${pkg.id.toCoordinates()}' $packageIndex: " +
                        e.collectMessagesAsString()
            )

            val now = Instant.now()
            listOf(
                ScanResult(
                    provenance = Provenance(),
                    scanner = scannerDetails,
                    summary = ScanSummary(
                        startTime = now,
                        endTime = now,
                        fileCount = 0,
                        packageVerificationCode = "",
                        licenseFindings = sortedSetOf(),
                        copyrightFindings = sortedSetOf(),
                        issues = listOf(issue)
                    ),
                    rawResult = EMPTY_JSON_NODE
                )
            )
        }
    }

    /**
     * Return the result file inside [outputDirectory]. The name of the file is derived from [pkg] and
     * [scannerDetails].
     */
    private fun getResultsFile(scannerDetails: ScannerDetails, pkg: Package, outputDirectory: File): File {
        val scanResultsForPackageDirectory = outputDirectory.resolve(pkg.id.toPath()).apply { safeMkdirs() }
        return scanResultsForPackageDirectory.resolve("scan-results_${scannerDetails.name}.$resultFileExt")
    }

    /**
     * Return matching [ScanResult]s for this [Package][pkg] from the [ScanResultsStorage]. If no results are found an
     * empty list is returned.
     */
    private fun readFromStorage(scannerDetails: ScannerDetails, pkg: Package, outputDirectory: File): List<ScanResult> {
        val resultsFile = getResultsFile(scannerDetails, pkg, outputDirectory)

        val scanResults = when (val storageResult = ScanResultsStorage.storage.read(pkg, scannerDetails)) {
            is Success -> storageResult.result.deduplicateScanResults().results
            is Failure -> emptyList()
        }

        if (scanResults.isNotEmpty()) {
            // Some external tools rely on the raw results filer to be written to the scan results directory, so write
            // the first stored result to resultsFile. This feature will be removed when the reporter tool becomes
            // available.
            resultsFile.mapper().writeValue(resultsFile, scanResults.first().rawResult)
        }

        return scanResults
    }

    /**
     * Scan the provided [pkg] for license information and write the results to [outputDirectory] using the scanner's
     * native file format.
     *
     * The package's source code is downloaded to [downloadDirectory] and scanned afterwards.
     *
     * Return the [ScanResult], if the package could not be scanned a [ScanException] is thrown.
     */
    private fun scanPackage(
        scannerDetails: ScannerDetails, pkg: Package, outputDirectory: File,
        downloadDirectory: File
    ): ScanResult {
        val resultsFile = getResultsFile(scannerDetails, pkg, outputDirectory)

        val downloadResult = try {
            Downloader.download(pkg, downloadDirectory.resolve(pkg.id.toPath()))
        } catch (e: DownloadException) {
            e.showStackTrace()

            val now = Instant.now()
            return ScanResult(
                Provenance(),
                scannerDetails,
                ScanSummary(
                    startTime = now,
                    endTime = now,
                    fileCount = 0,
                    packageVerificationCode = "",
                    licenseFindings = sortedSetOf(),
                    copyrightFindings = sortedSetOf(),
                    issues = listOf(
                        createAndLogIssue(
                            source = scannerName,
                            message = "Could not download '${pkg.id.toCoordinates()}': ${e.collectMessagesAsString()}"
                        )
                    )
                ),
                EMPTY_JSON_NODE
            )
        }

        log.info {
            "Running $scannerDetails on directory '${downloadResult.downloadDirectory.absolutePath}'."
        }

        val provenance = Provenance(
            downloadResult.dateTime, downloadResult.sourceArtifact, downloadResult.vcsInfo,
            downloadResult.originalVcsInfo
        )

        archiveFiles(downloadResult.downloadDirectory, pkg.id, provenance)

        val scanResult = scanPathInternal(downloadResult.downloadDirectory, resultsFile).copy(provenance = provenance)

        return when (val storageResult = ScanResultsStorage.storage.add(pkg.id, scanResult)) {
            is Success -> scanResult
            is Failure -> {
                val issue = OrtIssue(
                    source = ScanResultsStorage.storage.name,
                    message = storageResult.error,
                    severity = Severity.WARNING
                )
                val issues = scanResult.summary.issues + issue
                val summary = scanResult.summary.copy(issues = issues)
                scanResult.copy(summary = summary)
            }
        }
    }

    private fun archiveFiles(directory: File, id: Identifier, provenance: Provenance) {
        log.info { "Archiving files for ${id.toCoordinates()}." }

        val path = "${id.toPath()}/${provenance.hash()}"

        archiver.archive(directory, path)
    }

    /**
     * Scan the provided [path] for license information and write the results to [resultsFile] using the scanner's
     * native file format.
     *
     * No scan results storage is used by this function.
     *
     * The return value is a [ScanResult]. If the path could not be scanned, a [ScanException] is thrown.
     */
    protected abstract fun scanPathInternal(path: File, resultsFile: File): ScanResult

    /**
     * Scan the provided [inputPath] for license information and write the results to [outputDirectory] using the
     * scanner's native file format. The results file name is derived from [inputPath] and [getDetails].
     *
     * No scan results storage is used by this function.
     *
     * The return value is an [OrtResult]. If the path could not be scanned, a [ScanException] is thrown.
     */
    fun scanPath(inputPath: File, outputDirectory: File): OrtResult {
        val startTime = Instant.now()

        val absoluteInputPath = inputPath.absoluteFile

        require(inputPath.exists()) {
            "Specified path '$absoluteInputPath' does not exist."
        }

        val scannerDetails = getDetails()
        log.info { "Scanning path '$absoluteInputPath' with $scannerDetails..." }

        val result = try {
            val resultsFile = File(
                outputDirectory.apply { safeMkdirs() },
                "${inputPath.nameWithoutExtension}_${scannerDetails.name}.$resultFileExt"
            )
            scanPathInternal(inputPath, resultsFile).also {
                log.info {
                    "Detected licenses for path '$absoluteInputPath': ${it.summary.licenses.joinToString()}"
                }
            }
        } catch (e: ScanException) {
            e.showStackTrace()

            val now = Instant.now()
            val summary = ScanSummary(
                startTime = now,
                endTime = now,
                fileCount = 0,
                packageVerificationCode = "",
                licenseFindings = sortedSetOf(),
                copyrightFindings = sortedSetOf(),
                issues = listOf(
                    createAndLogIssue(
                        source = scannerName,
                        message = "Could not scan path '$absoluteInputPath': ${e.collectMessagesAsString()}"
                    )
                )
            )
            ScanResult(Provenance(), getDetails(), summary)
        }

        // There is no package id for arbitrary paths so create a fake one, ensuring that no ":" is contained.
        val id = Identifier(
            Os.name.fileSystemEncode(), absoluteInputPath.parent.fileSystemEncode(),
            inputPath.name.fileSystemEncode(), ""
        )

        val scanResultContainer = ScanResultContainer(id, listOf(result))
        val scanRecord = ScanRecord(sortedSetOf(scanResultContainer), ScanResultsStorage.storage.stats)

        val endTime = Instant.now()
        val scannerRun = ScannerRun(startTime, endTime, Environment(), config, scanRecord)

        val repository = Repository(VersionControlSystem.getCloneInfo(inputPath))
        return OrtResult(repository, scanner = scannerRun)
    }

    /**
     * Return the scanner's raw result in a JSON representation.
     */
    internal abstract fun getRawResult(resultsFile: File): JsonNode

    /**
     * Return the invariant relative path of the [scanned file][scannedFilename] with respect to the
     * [scanned path][scanPath].
     */
    protected fun relativizePath(scanPath: File, scannedFilename: File): String {
        val relativePathToScannedFile = if (scannedFilename.isAbsolute) {
            if (scanPath.isFile) {
                scannedFilename.relativeTo(scanPath.parentFile)
            } else {
                scannedFilename.relativeTo(scanPath)
            }
        } else {
            scannedFilename
        }

        return relativePathToScannedFile.invariantSeparatorsPath
    }
}

/**
 * Work around to prevent that duplicate [ScanResult]s from the [ScanResultsStorage] do get duplicated in the
 * [OrtResult] produced by this scanner.
 *
 * The time interval between a failing look-up of the cache entry and the resulting scan with the following store
 * operation can be relatively large. Thus this [LocalScanner] is prone to adding duplicate scan results if scans
 * are run in parallel. In particular the [PostgresStorage] allows adding duplicate tuples
 * (identifier, provenance, scanner details) which probably should be made unique.
 *
 * TODO:
 *
 * 1. Minimize the time between the failing look-up and the corresponding store operation mentioned.
 * 2. Make the tuples (identifier, provenance, scanner details) unique (dis-regarding provenance.downloadTime), at
 * least in [PostgresStorage].
 */
private fun ScanResultContainer.deduplicateScanResults(): ScanResultContainer {
    // Use vcsInfo and sourceArtifact instead of provenance in order to ignore the download time and original VCS info.
    data class Key(
        val id: Identifier,
        val vcsInfo: VcsInfo?,
        val sourceArtifact: RemoteArtifact?,
        val scannerDetails: ScannerDetails
    )

    fun ScanResult.key() = Key(id, provenance.vcsInfo, provenance.sourceArtifact, scanner)

    val deduplicatedResults = results.distinctBy { it.key() }

    val duplicates = results.size - deduplicatedResults.size
    if (duplicates > 0) {
        log.info { "Removed $duplicates duplicates out of ${results.size} scan results." }
    }

    return copy(results = deduplicatedResults)
}
