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
import com.vdurmont.semver4j.Semver

import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.concurrent.Executors

import kotlin.time.measureTime
import kotlin.time.measureTimedValue

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

import org.ossreviewtoolkit.downloader.DownloadException
import org.ossreviewtoolkit.downloader.Downloader
import org.ossreviewtoolkit.downloader.VersionControlSystem
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
import org.ossreviewtoolkit.model.config.createFileArchiver
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.scanner.storages.PostgresStorage
import org.ossreviewtoolkit.utils.CommandLineTool
import org.ossreviewtoolkit.utils.NamedThreadFactory
import org.ossreviewtoolkit.utils.Os
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.fileSystemEncode
import org.ossreviewtoolkit.utils.getPathFromEnvironment
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.perf
import org.ossreviewtoolkit.utils.safeMkdirs
import org.ossreviewtoolkit.utils.showStackTrace

/**
 * Abstraction for a [Scanner] that operates locally. Scan results can be stored in a [ScanResultsStorage].
 */
abstract class LocalScanner(name: String, config: ScannerConfiguration) : Scanner(name, config), CommandLineTool {
    companion object {
        /**
         * The number of threads to use for the storage dispatcher.
         */
        const val NUM_STORAGE_THREADS = 5

        /**
         * The name of the property defining the regular expression for the scanner name as part of [ScannerCriteria].
         */
        const val PROP_CRITERIA_NAME = "regScannerName"

        /**
         * The name of the property defining the minimum version of the scanner as part of [ScannerCriteria].
         */
        const val PROP_CRITERIA_MIN_VERSION = "minVersion"

        /**
         * The name of the property defining the maximum version of the scanner as part of [ScannerCriteria].
         */
        const val PROP_CRITERIA_MAX_VERSION = "maxVersion"
    }

    private val archiver by lazy {
        config.archive.createFileArchiver()
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
            getVersion(it) == expectedVersion
        } ?: run {
            if (scannerExe.isNotEmpty()) {
                log.info {
                    "Bootstrapping scanner '$scannerName' as expected version $expectedVersion was not found in PATH."
                }

                val (bootstrapDirectory, duration) = measureTimedValue {
                    bootstrap().also {
                        val actualVersion = getVersion(it)
                        if (actualVersion != expectedVersion) {
                            throw IOException(
                                "Bootstrapped scanner version $actualVersion does not match expected version " +
                                        "$expectedVersion."
                            )
                        }
                    }
                }

                log.perf {
                    "Bootstrapped scanner '$scannerName' version $expectedVersion in ${duration.inMilliseconds}ms."
                }

                bootstrapDirectory
            } else {
                log.info { "Skipping to bootstrap scanner '$scannerName' as it has no executable." }

                File("")
            }
        }
    }

    /**
     * The expected version of the scanner. This is also the version that would get bootstrapped.
     */
    protected abstract val expectedVersion: String

    /**
     * The full path to the scanner executable.
     */
    protected val scannerPath by lazy { scannerDir.resolve(command()) }

    /**
     * The actual version of the scanner, or an empty string in case of failure.
     */
    open val version by lazy { getVersion(scannerDir) }

    /**
     * The configuration of this [LocalScanner].
     */
    abstract val configuration: String

    /**
     * The [ScannerDetails] of this [LocalScanner].
     */
    val details by lazy { ScannerDetails(scannerName, version, configuration) }

    override fun getVersionRequirement(): Requirement = Requirement.buildLoose(expectedVersion)

    /**
     * Bootstrap the scanner to be ready for use, like downloading and / or configuring it.
     *
     * @return The directory the scanner is installed in.
     */
    protected open fun bootstrap(): File = throw NotImplementedError()

    /**
     * Return a [ScannerCriteria] object to be used when looking up existing scan results from a [ScanResultsStorage].
     * Per default, the properties of this object are initialized to match this scanner implementation. It is,
     * however, possible to override these defaults from the configuration, in the [ScannerConfiguration.options]
     * property: Use properties of the form _scannerName.criteria.property_, where _scannerName_ is the name of
     * the scanner the configuration applies to, and _property_ is the name of a property of the [ScannerCriteria]
     * class. For instance, to specify that a specific minimum version of ScanCode is allowed, set this property:
     * `options.ScanCode.criteria.minScannerVersion=3.0.2`.
     */
    open fun getScannerCriteria(): ScannerCriteria {
        val options = config.options?.get(scannerName).orEmpty()
        val minVersion = parseVersion(options[PROP_CRITERIA_MIN_VERSION]) ?: Semver(normalizeVersion(expectedVersion))
        val maxVersion = parseVersion(options[PROP_CRITERIA_MAX_VERSION]) ?: minVersion.nextMinor()
        val name = options[PROP_CRITERIA_NAME] ?: scannerName
        return ScannerCriteria(name, minVersion, maxVersion, ScannerCriteria.exactConfigMatcher(configuration))
    }

    override suspend fun scanPackages(
        packages: List<Package>,
        outputDirectory: File,
        downloadDirectory: File
    ): Map<Package, List<ScanResult>> {
        val storageDispatcher = Executors.newFixedThreadPool(
            NUM_STORAGE_THREADS,
            NamedThreadFactory(ScanResultsStorage.storage.name)
        ).asCoroutineDispatcher()

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
        val scannerCriteria = getScannerCriteria()

        if (pkg.isMetaDataOnly) {
            log.info { "Skipping '${pkg.id.toCoordinates()}' as it is meta data only." }

            return emptyList()
        }

        return try {
            val storedResults = withContext(storageDispatcher) {
                LocalScanner.log.info {
                    "Looking for stored scan results for ${pkg.id.toCoordinates()} and " +
                            "$scannerCriteria $packageIndex."
                }

                readFromStorage(scannerCriteria, pkg)
            }

            if (storedResults.isNotEmpty()) {
                log.info {
                    "Found ${storedResults.size} stored scan result(s) for ${pkg.id.toCoordinates()} " +
                            "and $scannerCriteria, not scanning the package again $packageIndex."
                }

                // Due to a temporary bug that has been fixed by now the scan results for packages were not properly
                // filtered by VCS path. Filter them again to fix the problem.
                // TODO: This filtering can be removed after a while.
                storedResults.map { it.filterByVcsPath().filterByIgnorePatterns(config.ignorePatterns) }
            } else {
                withContext(scanDispatcher) {
                    LocalScanner.log.info {
                        "No stored result found for ${pkg.id.toCoordinates()} and $scannerCriteria, " +
                                "scanning package in thread '${Thread.currentThread().name}' " +
                                "$packageIndex."
                    }

                    listOf(
                        scanPackage(details, pkg, outputDirectory, downloadDirectory).also {
                            LocalScanner.log.info {
                                "Finished scanning ${pkg.id.toCoordinates()} in thread " +
                                        "'${Thread.currentThread().name}' $packageIndex."
                            }
                        }
                    )
                }
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
                    scanner = details,
                    summary = ScanSummary(
                        startTime = now,
                        endTime = now,
                        fileCount = 0,
                        packageVerificationCode = "",
                        licenseFindings = sortedSetOf(),
                        copyrightFindings = sortedSetOf(),
                        issues = listOf(issue)
                    )
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
    private fun readFromStorage(scannerCriteria: ScannerCriteria, pkg: Package): List<ScanResult> =
        when (val storageResult = ScanResultsStorage.storage.read(pkg, scannerCriteria)) {
            is Success -> storageResult.result.deduplicateScanResults().results
            is Failure -> emptyList()
        }

    /**
     * Scan the provided [pkg] for license information and write the results to [outputDirectory] using the scanner's
     * native file format.
     *
     * The package's source code is downloaded to [downloadDirectory] and scanned afterwards.
     *
     * Return the [ScanResult], if the package could not be scanned a [ScanException] is thrown.
     */
    fun scanPackage(
        scannerDetails: ScannerDetails,
        pkg: Package,
        outputDirectory: File,
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
                )
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

        val (scanResult, scanDuration) = measureTimedValue {
            scanPathInternal(downloadResult.downloadDirectory, resultsFile)
                .copy(provenance = provenance)
                .filterByVcsPath()
        }

        log.perf {
            "Scanned source code of '${pkg.id.toCoordinates()}' with ${javaClass.simpleName} in " +
                    "${scanDuration.inMilliseconds}ms."
        }

        val storageResult = ScanResultsStorage.storage.add(pkg.id, scanResult)
        val filteredResult = scanResult.filterByIgnorePatterns(config.ignorePatterns)

        return when (storageResult) {
            is Success -> filteredResult
            is Failure -> {
                val issue = OrtIssue(
                    source = ScanResultsStorage.storage.name,
                    message = storageResult.error,
                    severity = Severity.WARNING
                )
                val issues = scanResult.summary.issues + issue
                val summary = scanResult.summary.copy(issues = issues)
                filteredResult.copy(summary = summary)
            }
        }
    }

    private fun archiveFiles(directory: File, id: Identifier, provenance: Provenance) {
        log.info { "Archiving files for ${id.toCoordinates()}." }

        val path = "${id.toPath()}/${provenance.hash()}"

        val duration = measureTime { archiver.archive(directory, path) }

        log.perf { "Archived files for '${id.toCoordinates()}' in ${duration.inMilliseconds}ms." }
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

        log.info { "Scanning path '$absoluteInputPath' with $details..." }

        val result = try {
            val resultsFile = File(
                outputDirectory.apply { safeMkdirs() },
                "${inputPath.nameWithoutExtension}_${details.name}.$resultFileExt"
            )
            scanPathInternal(inputPath, resultsFile).also {
                log.info {
                    "Detected licenses for path '$absoluteInputPath': ${it.summary.licenses.joinToString()}"
                }
            }.filterByIgnorePatterns(config.ignorePatterns)
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

            ScanResult(Provenance(), details, summary)
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

    val duplicatesCount = results.size - deduplicatedResults.size
    if (duplicatesCount > 0) {
        log.info { "Removed $duplicatesCount duplicates out of ${results.size} scan results." }
    }

    return copy(results = deduplicatedResults)
}

/**
 * Parse the given [versionStr] to a [Semver] object, trying to be failure tolerant.
 */
private fun parseVersion(versionStr: String?): Semver? =
    versionStr?.let { Semver(normalizeVersion(it)) }

/**
 * Normalize the given [versionStr] to make sure that it can be parsed to a [Semver]. The [Semver] class
 * requires that all components of a semantic version number are present. This function enables a more lenient
 * style when declaring a version. So for instance, the user can just write "2", and this gets expanded to
 * "2.0.0".
 */
private fun normalizeVersion(versionStr: String): String =
    versionStr.takeIf { v -> v.count { it == '.' } >= 2 } ?: normalizeVersion("$versionStr.0")
