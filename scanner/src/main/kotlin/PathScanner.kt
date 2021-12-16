/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
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

package org.ossreviewtoolkit.scanner

import com.vdurmont.semver4j.Semver

import java.io.File
import java.time.Instant

import kotlin.time.measureTime
import kotlin.time.measureTimedValue

import org.ossreviewtoolkit.downloader.DownloadException
import org.ossreviewtoolkit.downloader.Downloader
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Failure
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanRecord
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.Success
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.config.createFileArchiver
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.scanner.storages.FileBasedStorage
import org.ossreviewtoolkit.scanner.storages.PostgresStorage
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.collectMessagesAsString
import org.ossreviewtoolkit.utils.common.fileSystemEncode
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.core.Environment
import org.ossreviewtoolkit.utils.core.createOrtTempDir
import org.ossreviewtoolkit.utils.core.log
import org.ossreviewtoolkit.utils.core.perf
import org.ossreviewtoolkit.utils.core.showStackTrace

/**
 * A [Scanner] that operates on a path. For scanning [Package]s, it leverages the [Downloader] to make the source code
 * available locally. Scan results are stored in the configured [ScanResultsStorage].
 */
abstract class PathScanner(
    name: String,
    scannerConfig: ScannerConfiguration,
    downloaderConfig: DownloaderConfiguration
) : Scanner(name, scannerConfig, downloaderConfig) {
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
        scannerConfig.archive.createFileArchiver()
    }

    /**
     * Return a [ScannerCriteria] object to be used when looking up existing scan results from a [ScanResultsStorage].
     * Per default, the properties of this object are initialized to match this scanner implementation. It is,
     * however, possible to override these defaults from the configuration, in the [ScannerConfiguration.options]
     * property: Use properties of the form _scannerName.property_, where _scannerName_ is the name of
     * the scanner the configuration applies to, and _property_ is the name of a property of the [ScannerCriteria]
     * class. For instance, to specify that a specific minimum version of ScanCode is allowed, set this property:
     * `options.ScanCode.minVersion=3.0.2`.
     */
    open fun getScannerCriteria(): ScannerCriteria {
        val options = scannerConfig.options?.get(scannerName).orEmpty()
        val minVersion = parseVersion(options[PROP_CRITERIA_MIN_VERSION]) ?: Semver(normalizeVersion(version))
        val maxVersion = parseVersion(options[PROP_CRITERIA_MAX_VERSION]) ?: minVersion.nextMinor()
        val name = options[PROP_CRITERIA_NAME] ?: scannerName
        return ScannerCriteria(name, minVersion, maxVersion, ScannerCriteria.exactConfigMatcher(configuration))
    }

    override suspend fun scanPackages(
        packages: Set<Package>,
        labels: Map<String, String>
    ): Map<Package, List<ScanResult>> {
        val scannerCriteria = getScannerCriteria()

        log.info { "Searching scan results for ${packages.size} packages." }

        val remainingPackages = packages.filterTo(mutableListOf()) { pkg ->
            !pkg.isMetaDataOnly.also {
                if (it) PathScanner.log.info { "Skipping '${pkg.id.toCoordinates()}' as it is metadata only." }
            }
        }

        val resultsFromStorage = readResultsFromStorage(packages, scannerCriteria)

        log.info { "Found stored scan results for ${resultsFromStorage.size} packages and $scannerCriteria." }

        if (scannerConfig.createMissingArchives) {
            createMissingArchives(resultsFromStorage)
        }

        remainingPackages.removeAll { it in resultsFromStorage.keys }

        log.info { "Scanning ${remainingPackages.size} packages for which no stored scan results were found." }

        val downloadDirectory = createOrtTempDir()

        val resultsFromScanner = try {
            remainingPackages.scan(downloadDirectory)
        } finally {
            downloadDirectory.safeDeleteRecursively(force = true)
        }

        return resultsFromStorage + resultsFromScanner
    }

    private fun readResultsFromStorage(packages: Collection<Package>, scannerCriteria: ScannerCriteria) =
        when (val results = ScanResultsStorage.storage.read(packages, scannerCriteria)) {
            is Success -> results.result
            is Failure -> emptyMap()
        }.filter { it.value.isNotEmpty() }
            .mapKeys { (id, _) -> packages.single { it.id == id } }
            .mapValues { it.value.deduplicateScanResults() }
            .mapValues { (_, scanResults) ->
                // Due to a bug that has been fixed in d839f6e the scan results for packages were not properly filtered
                // by VCS path. Filter them again to fix the problem.
                // TODO: Remove this workaround together with the next change that requires recreating the scan storage.
                scanResults.map { it.filterByVcsPath().filterByIgnorePatterns(scannerConfig.ignorePatterns) }
            }

    private fun Collection<Package>.scan(downloadDirectory: File): Map<Package, List<ScanResult>> {
        var index = 0

        return associateWith { pkg ->
            index++

            val packageIndex = "($index of $size)"

            PathScanner.log.info {
                "Scanning ${pkg.id.toCoordinates()}' in thread '${Thread.currentThread().name}' $packageIndex"
            }

            val scanResult = try {
                scanPackage(details, pkg, downloadDirectory).also {
                    PathScanner.log.info {
                        "Finished scanning ${pkg.id.toCoordinates()} in thread '${Thread.currentThread().name}' " +
                                "$packageIndex."
                    }
                }
            } catch (e: ScanException) {
                e.showStackTrace()
                e.createFailedScanResult(pkg, packageIndex)
            }

            listOf(scanResult)
        }
    }

    private fun ScanException.createFailedScanResult(pkg: Package, packageIndex: String): ScanResult {
        val issue = createAndLogIssue(
            source = scannerName,
            message = "Could not scan '${pkg.id.toCoordinates()}' $packageIndex: ${collectMessagesAsString()}"
        )

        val now = Instant.now()
        return ScanResult(
            provenance = UnknownProvenance,
            scanner = details,
            summary = ScanSummary(
                startTime = now,
                endTime = now,
                packageVerificationCode = "",
                licenseFindings = sortedSetOf(),
                copyrightFindings = sortedSetOf(),
                issues = listOf(issue)
            )
        )
    }

    private fun createMissingArchives(scanResults: Map<Package, List<ScanResult>>) {
        val missingArchives = mutableSetOf<Pair<Package, KnownProvenance>>()

        scanResults.forEach { (pkg, results) ->
            results.forEach { (provenance, _, _) ->
                if (provenance is KnownProvenance && !archiver.hasArchive(provenance)) {
                    missingArchives += Pair(pkg, provenance)
                }
            }
        }

        if (missingArchives.isEmpty()) return

        val tempDirectory = createOrtTempDir()

        try {
            missingArchives.forEach { (pkg, provenance) ->
                val downloadDirectory = tempDirectory.resolve(pkg.id.toPath()).resolve(provenance.javaClass.simpleName)
                val downloadProvenance = Downloader(downloaderConfig).download(pkg, downloadDirectory)

                if (downloadProvenance == provenance) {
                    archiveFiles(downloadDirectory, pkg.id, provenance)
                } else {
                    log.warn { "Mismatching provenance when creating missing archive for $provenance." }
                }
            }
        } finally {
            tempDirectory.safeDeleteRecursively(force = true)
        }
    }

    /**
     * Scan the provided [pkg] for license information and return a [ScanResult]. The package's source code is
     * downloaded to [downloadDirectory] for scanning. If the package could not be scanned a [ScanException] is thrown.
     */
    fun scanPackage(scannerDetails: ScannerDetails, pkg: Package, downloadDirectory: File): ScanResult {
        val pkgDownloadDirectory = downloadDirectory.resolve(pkg.id.toPath())

        val provenance = try {
            Downloader(downloaderConfig).download(pkg, pkgDownloadDirectory)
        } catch (e: DownloadException) {
            e.showStackTrace()

            val now = Instant.now()
            return ScanResult(
                UnknownProvenance,
                scannerDetails,
                ScanSummary(
                    startTime = now,
                    endTime = now,
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
            "Running $scannerDetails on directory '${pkgDownloadDirectory.absolutePath}'."
        }

        if (provenance is KnownProvenance) {
            archiveFiles(pkgDownloadDirectory, pkg.id, provenance)
        }

        val (scanSummary, scanDuration) = measureTimedValue {
            val vcsPath = (provenance as? RepositoryProvenance)?.vcsInfo?.takeUnless {
                it.type == VcsType.GIT_REPO
            }?.path.orEmpty()
            scanPathInternal(pkgDownloadDirectory).filterByPath(vcsPath)
        }

        log.perf {
            "Scanned source code of '${pkg.id.toCoordinates()}' with ${javaClass.simpleName} in $scanDuration."
        }

        val scanResult = ScanResult(provenance, scannerDetails, scanSummary)
        val storageResult = ScanResultsStorage.storage.add(pkg.id, scanResult)
        val filteredResult = scanResult.filterByIgnorePatterns(scannerConfig.ignorePatterns)

        return when (storageResult) {
            is Success -> filteredResult
            is Failure -> {
                val issue = OrtIssue(
                    source = ScanResultsStorage.storage.name,
                    message = storageResult.error,
                    severity = Severity.WARNING
                )
                val issues = scanSummary.issues + issue
                val summary = scanSummary.copy(issues = issues)
                filteredResult.copy(summary = summary)
            }
        }
    }

    private fun archiveFiles(directory: File, id: Identifier, provenance: KnownProvenance) {
        log.info { "Archiving files for ${id.toCoordinates()}." }

        val duration = measureTime { archiver.archive(directory, provenance) }

        log.perf { "Archived files for '${id.toCoordinates()}' in $duration." }
    }

    /**
     * Scan the provided [path] for license information and return a [ScanSummary]. If the path could not be scanned, a
     * [ScanException] is thrown.
     */
    protected abstract fun scanPathInternal(path: File): ScanSummary

    /**
     * Scan the provided [path] for license information and return an [OrtResult]. If the path could not be scanned, a
     * [ScanException] is thrown.
     */
    fun scanPath(path: File): OrtResult {
        val startTime = Instant.now()

        val absoluteInputPath = path.absoluteFile

        require(absoluteInputPath.exists()) {
            "Specified path '$absoluteInputPath' does not exist."
        }

        log.info { "Scanning path '$absoluteInputPath' with $details..." }

        val summary = try {
            scanPathInternal(path).also {
                log.info {
                    "Detected licenses for path '$absoluteInputPath': ${it.licenses.joinToString()}"
                }
            }.filterByIgnorePatterns(scannerConfig.ignorePatterns)
        } catch (e: ScanException) {
            e.showStackTrace()

            val now = Instant.now()
            ScanSummary(
                startTime = now,
                endTime = now,
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
        }

        // There is no package id for arbitrary paths so create a fake one, ensuring that no ":" is contained.
        val id = Identifier(
            Os.name.fileSystemEncode(), absoluteInputPath.parent.fileSystemEncode(),
            path.name.fileSystemEncode(), ""
        )

        val scanResult = ScanResult(UnknownProvenance, details, summary)
        val scanRecord = ScanRecord(sortedMapOf(id to listOf(scanResult)), ScanResultsStorage.storage.stats)

        val endTime = Instant.now()
        val scannerRun = ScannerRun(startTime, endTime, Environment(), scannerConfig, scanRecord)

        val repository = Repository(VersionControlSystem.getCloneInfo(path))
        return OrtResult(repository, scanner = scannerRun)
    }

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

    /**
     * Workaround to prevent that duplicate [ScanResult]s from the [ScanResultsStorage] do get duplicated in the
     * [OrtResult] produced by this scanner.
     *
     * The time interval between a failing read from storage and the resulting scan with the following store operation
     * can be relatively large. Thus, this [PathScanner] is prone to adding duplicate scan results if multiple
     * instances of the scanner run in parallel and the storage backends do not prevent adding duplicates. In particular
     * the [FileBasedStorage] and [PostgresStorage] used to allow adding duplicate tuples (identifier, provenance,
     * scanner details).
     *
     * Note that both storages implementations were updated to prevent adding duplicates, but this function is kept for
     * storages that were created before the fix was implemented.
     */
    private fun List<ScanResult>.deduplicateScanResults(): List<ScanResult> {
        // Use vcsInfo and sourceArtifact instead of provenance in order to ignore the download time and original VCS
        // info.
        data class Key(
            val provenance: Provenance?,
            val scannerDetails: ScannerDetails
        )

        fun ScanResult.key() = Key(provenance, scanner)

        val deduplicatedResults = distinctBy { it.key() }

        val duplicatesCount = size - deduplicatedResults.size
        if (duplicatesCount > 0) {
            PathScanner.log.info { "Removed $duplicatesCount duplicates out of $size scan results." }
        }

        return deduplicatedResults
    }
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
