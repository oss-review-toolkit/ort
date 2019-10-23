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

import com.fasterxml.jackson.databind.JsonNode

import com.here.ort.downloader.DownloadException
import com.here.ort.downloader.Downloader
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.EMPTY_JSON_NODE
import com.here.ort.model.Environment
import com.here.ort.model.Identifier
import com.here.ort.model.OrtIssue
import com.here.ort.model.OrtResult
import com.here.ort.model.Package
import com.here.ort.model.Provenance
import com.here.ort.model.Repository
import com.here.ort.model.ScanRecord
import com.here.ort.model.ScanResult
import com.here.ort.model.ScanResultContainer
import com.here.ort.model.ScanSummary
import com.here.ort.model.ScannerDetails
import com.here.ort.model.ScannerRun
import com.here.ort.model.config.ScannerConfiguration
import com.here.ort.model.mapper
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.NamedThreadFactory
import com.here.ort.utils.Os
import com.here.ort.utils.collectMessagesAsString
import com.here.ort.utils.fileSystemEncode
import com.here.ort.utils.getPathFromEnvironment
import com.here.ort.utils.getUserOrtDirectory
import com.here.ort.utils.log
import com.here.ort.utils.safeMkdirs
import com.here.ort.utils.showStackTrace
import com.here.ort.utils.storage.FileArchiver
import com.here.ort.utils.storage.LocalFileStorage
import com.here.ort.utils.toHexString

import com.vdurmont.semver4j.Requirement

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.Executors

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Implementation of [Scanner] for scanners that operate locally. Packages passed to [scanPackages] are processed in
 * serial order. Scan results can be stored in a [ScanResultsStorage].
 */
abstract class LocalScanner(name: String, config: ScannerConfiguration) : Scanner(name, config), CommandLineTool {
    companion object {
        val DEFAULT_ARCHIVE_DIR by lazy { getUserOrtDirectory().resolve("scanner/archive") }

        val DEFAULT_ARCHIVE_PATTERNS = listOf(
            "license*",
            "licence*",
            "*.license",
            "unlicense",
            "unlicence",
            "copying*",
            "copyright",
            "patents"
        ).flatMap { listOf(it, it.toUpperCase(), it.capitalize()) }
    }

    val archiver by lazy {
        config.archive?.createFileArchiver() ?: FileArchiver(
            DEFAULT_ARCHIVE_PATTERNS,
            LocalFileStorage(DEFAULT_ARCHIVE_DIR)
        )
    }

    /**
     * A property containing the file name extension of the scanner's native output format, without the dot.
     */
    abstract val resultFileExt: String

    /**
     * The directory the scanner was bootstrapped to, if so.
     */
    protected val scannerDir by lazy {
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
    protected val scannerPath by lazy { File(scannerDir, command()) }

    override fun getVersionRequirement(): Requirement = Requirement.buildLoose(scannerVersion)

    /**
     * Return the actual version of the scanner, or an empty string in case of failure.
     */
    abstract fun getVersion(dir: File = scannerDir): String

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
        val scannerDetails = getDetails()

        val storageDispatcher =
            Executors.newFixedThreadPool(5, NamedThreadFactory(ScanResultsStorage.storage.name)).asCoroutineDispatcher()
        val scanDispatcher = Executors.newSingleThreadExecutor(NamedThreadFactory(scannerName)).asCoroutineDispatcher()

        return try {
            coroutineScope {
                packages.withIndex().map { (index, pkg) ->
                    val packageIndex = "(${index + 1}/${packages.size})"

                    async {
                        val result = try {
                            log.info { "Queueing scan of '${pkg.id.toCoordinates()}' $packageIndex." }

                            val storedResults = withContext(storageDispatcher) {
                                log.info {
                                    "Trying to read stored scan results for ${pkg.id.toCoordinates()} in thread " +
                                            "'${Thread.currentThread().name}' $packageIndex."
                                }

                                readFromStorage(scannerDetails, pkg, outputDirectory)
                            }

                            if (storedResults.isNotEmpty()) {
                                log.info { "Using stored scan result(s) for ${pkg.id.toCoordinates()} $packageIndex." }

                                storedResults
                            } else {
                                withContext(scanDispatcher) {
                                    log.info {
                                        "No stored results found, scanning package ${pkg.id.toCoordinates()} in " +
                                                "thread '${Thread.currentThread().name}' $packageIndex."
                                    }

                                    listOf(
                                        scanPackage(scannerDetails, pkg, outputDirectory, downloadDirectory).also {
                                            log.info {
                                                "Finished scanning ${pkg.id.toCoordinates()} $packageIndex."
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

                            log.error {
                                "Could not scan '${pkg.id.toCoordinates()}' $packageIndex: " +
                                        e.collectMessagesAsString()
                            }

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
                                        errors = listOf(
                                            OrtIssue(
                                                source = scannerName,
                                                message = e.collectMessagesAsString()
                                            )
                                        )
                                    ),
                                    rawResult = EMPTY_JSON_NODE
                                )
                            )
                        }

                        Pair(pkg, result)
                    }
                }.associate { it.await() }
            }
        } finally {
            storageDispatcher.close()
            scanDispatcher.close()
        }
    }

    /**
     * Return the result file inside [outputDirectory]. The name of the file is derived from [pkg] and
     * [scannerDetails].
     */
    private fun getResultsFile(scannerDetails: ScannerDetails, pkg: Package, outputDirectory: File): File {
        val scanResultsForPackageDirectory = File(outputDirectory, pkg.id.toPath()).apply { safeMkdirs() }
        return File(scanResultsForPackageDirectory, "scan-results_${scannerDetails.name}.$resultFileExt")
    }

    /**
     * Return matching [ScanResult]s for this [Package][pkg] from the [ScanResultsStorage]. If no results are found an
     * empty list is returned.
     */
    private fun readFromStorage(scannerDetails: ScannerDetails, pkg: Package, outputDirectory: File): List<ScanResult> {
        val resultsFile = getResultsFile(scannerDetails, pkg, outputDirectory)
        val storedResults = ScanResultsStorage.storage.read(pkg, scannerDetails)

        if (storedResults.results.isNotEmpty()) {
            // Some external tools rely on the raw results filer to be written to the scan results directory, so write
            // the first stored result to resultsFile. This feature will be removed when the reporter tool becomes
            // available.
            resultsFile.mapper().writeValue(resultsFile, storedResults.results.first().rawResult)
        }

        return storedResults.results
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
            Downloader().download(pkg, downloadDirectory)
        } catch (e: DownloadException) {
            e.showStackTrace()

            log.error { "Could not download '${pkg.id.toCoordinates()}': ${e.collectMessagesAsString()}" }

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
                    errors = listOf(OrtIssue(source = scannerName, message = e.collectMessagesAsString()))
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

        val scanResult = scanPath(downloadResult.downloadDirectory, resultsFile).copy(provenance = provenance)

        ScanResultsStorage.storage.add(pkg.id, scanResult)

        return scanResult
    }

    private fun archiveFiles(directory: File, id: Identifier, provenance: Provenance) {
        log.info { "Archiving files for ${id.toCoordinates()}." }

        val provenanceBytes = provenance.toString().toByteArray()
        val provenanceHash = MessageDigest.getInstance("SHA-1").digest(provenanceBytes).toHexString()
        val path = "${id.toPath()}/$provenanceHash"

        archiver.archive(directory, path)
    }

    /**
     * Scan the provided [inputPath] for license information and write the results to [outputDirectory] using the
     * scanner's native file format. The results file name is derived from [inputPath] and [getDetails].
     *
     * No scan results storage is used by this function.
     *
     * The return value is an [OrtResult]. If the path could not be scanned, a [ScanException] is thrown.
     */
    fun scanInputPath(inputPath: File, outputDirectory: File): OrtResult {
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
            scanPath(inputPath, resultsFile).also {
                log.info {
                    "Detected licenses for path '$absoluteInputPath': ${it.summary.licenses.joinToString()}"
                }
            }
        } catch (e: ScanException) {
            e.showStackTrace()

            log.error { "Could not scan path '$absoluteInputPath': ${e.message}" }

            val now = Instant.now()
            val summary = ScanSummary(
                startTime = now,
                endTime = now,
                fileCount = 0,
                packageVerificationCode = "",
                licenseFindings = sortedSetOf(),
                copyrightFindings = sortedSetOf(),
                errors = listOf(OrtIssue(source = scannerName, message = e.collectMessagesAsString()))
            )
            ScanResult(Provenance(), getDetails(), summary)
        }

        // There is no package id for arbitrary paths so create a fake one, ensuring that no ":" is contained.
        val id = Identifier(
            Os.name.fileSystemEncode(), absoluteInputPath.parent.fileSystemEncode(),
            inputPath.name.fileSystemEncode(), ""
        )

        val scanResultContainer = ScanResultContainer(id, listOf(result))
        val scanRecord = ScanRecord(sortedSetOf(), sortedSetOf(scanResultContainer), ScanResultsStorage.storage.stats)

        val endTime = Instant.now()
        val scannerRun = ScannerRun(startTime, endTime, Environment(), config, scanRecord)

        val repository = Repository(VersionControlSystem.getCloneInfo(inputPath))
        return OrtResult(repository, scanner = scannerRun)
    }

    /**
     * Scan the provided [path] for license information and write the results to [resultsFile] using the scanner's
     * native file format.
     *
     * No scan results storage is used by this function.
     *
     * The return value is a [ScanResult]. If the path could not be scanned, a [ScanException] is thrown.
     */
    abstract fun scanPath(path: File, resultsFile: File): ScanResult

    /**
     * Return the scanner's raw result in a JSON representation.
     */
    internal abstract fun getRawResult(resultsFile: File): JsonNode

    /**
     * Return the invariant relative path of the [scanned file][scannedFilename] with respect to the
     * [scanned path][scanPath].
     */
    protected fun relativizePath(scanPath: File, scannedFilename: File): String {
        val relativePathToScannedFile = if (scanPath.isFile) {
            if (scanPath.isAbsolute) {
                // Scanners are called with the current working directory unchanged. To avoid absolute paths, make a
                // path to a file relative to the current working directory in the absence of a scan root directory.
                scanPath.relativeTo(File(".").absoluteFile)
            } else {
                scanPath
            }
        } else {
            scannedFilename.relativeTo(scanPath.absoluteFile)
        }

        return relativePathToScannedFile.invariantSeparatorsPath
    }
}
