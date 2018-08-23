/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

import ch.frankel.slf4k.*

import com.fasterxml.jackson.databind.JsonNode

import com.here.ort.downloader.DownloadException
import com.here.ort.downloader.Downloader
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.EMPTY_JSON_NODE
import com.here.ort.model.Environment
import com.here.ort.model.Error
import com.here.ort.model.Identifier
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
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.config.ScannerConfiguration
import com.here.ort.model.mapper
import com.here.ort.utils.collectMessages
import com.here.ort.utils.getPathFromEnvironment
import com.here.ort.utils.log
import com.here.ort.utils.safeMkdirs
import com.here.ort.utils.showStackTrace

import java.io.File
import java.io.IOException
import java.time.Instant

/**
 * Implementation of [Scanner] for scanners that operate locally. Packages passed to [scan] are processed in serial
 * order. Scan results can be cached in a [ScanResultsCache].
 */
abstract class LocalScanner(config: ScannerConfiguration) : Scanner(config) {
    /**
     * A property containing the file name extension of the scanner's native output format, without the dot.
     */
    protected abstract val resultFileExt: String

    /**
     * The directory the scanner was bootstrapped to, if so.
     */
    protected val scannerDir by lazy {
        getPathFromEnvironment(scannerExe)?.parentFile?.takeIf {
            getVersion(it) == scannerVersion
        } ?: run {
            if (scannerExe.isNotEmpty()) {
                println("Bootstrapping scanner '$this' as required version $scannerVersion was not found in PATH.")
                bootstrap().also {
                    val actualScannerVersion = getVersion(it)
                    if (actualScannerVersion != scannerVersion) {
                        throw IOException("Bootstrapped scanner version $actualScannerVersion " +
                                "does not match expected version $scannerVersion.")
                    }
                }
            } else {
                println("Skipping to bootstrap scanner '$this' as it has no executable.")
                File("")
            }
        }
    }

    /**
     * The scanner's executable file name.
     */
    protected abstract val scannerExe: String

    /**
     * The required version of the scanner. This is also the version that would get bootstrapped.
     */
    protected abstract val scannerVersion: String

    /**
     * The full path to the scanner executable.
     */
    protected val scannerPath by lazy { File(scannerDir, scannerExe) }

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
     * Return the name of this [LocalScanner].
     */
    fun getName() = toString().toLowerCase()

    /**
     * Return the [ScannerDetails] of this [LocalScanner].
     */
    fun getDetails() = ScannerDetails(getName(), getVersion(), getConfiguration())

    /**
     * Return the actual version of the scanner, or an empty string in case of failure.
     */
    abstract fun getVersion(dir: File = scannerDir): String

    override fun scan(packages: List<Package>, outputDirectory: File, downloadDirectory: File?)
            : Map<Package, List<ScanResult>> {
        val scannerDetails = getDetails()

        return packages.withIndex().associate { (index, pkg) ->
            val result = try {
                log.info { "Starting scan of '${pkg.id}' (${index + 1}/${packages.size})." }

                scanPackage(scannerDetails, pkg, outputDirectory, downloadDirectory)
            } catch (e: ScanException) {
                val now = Instant.now()
                listOf(ScanResult(
                        provenance = Provenance(now),
                        scanner = scannerDetails,
                        summary = ScanSummary(
                                startTime = now,
                                endTime = now,
                                fileCount = 0,
                                licenseFindings = sortedSetOf(),
                                errors = e.collectMessages().map { Error(source = javaClass.simpleName, message = it) }
                        ),
                        rawResult = EMPTY_JSON_NODE)
                )
            }

            Pair(pkg, result)
        }
    }

    /**
     * Scan all files in [inputPath] using this [Scanner] and store the scan results in [outputDirectory].
     */
    fun scanInputPath(inputPath: File, outputDirectory: File): OrtResult {
        require(inputPath.exists()) {
            "Provided path does not exist: ${inputPath.absolutePath}"
        }

        println("Scanning path '${inputPath.absolutePath}'...")

        val result = try {
            scanPath(inputPath, outputDirectory).also {
                println("Detected licenses for path '${inputPath.absolutePath}': ${it.summary.licenses.joinToString()}")
            }
        } catch (e: ScanException) {
            e.showStackTrace()

            log.error { "Could not scan path '${inputPath.absolutePath}': ${e.message}" }

            val now = Instant.now()
            val summary = ScanSummary(now, now, 0, sortedSetOf(),
                    e.collectMessages().map { Error(source = toString(), message = it) })
            ScanResult(Provenance(now), getDetails(), summary)
        }

        val scanResultContainer = ScanResultContainer(Identifier("", "", inputPath.absolutePath, ""), listOf(result))

        val scanRecord = ScanRecord(sortedSetOf(), sortedSetOf(scanResultContainer), ScanResultsCache.stats)

        val scannerRun = ScannerRun(Environment(), config, scanRecord)

        val vcs = VersionControlSystem.getCloneInfo(inputPath)
        val repository = Repository(vcs, vcs.normalize(), RepositoryConfiguration(null))

        return OrtResult(repository, scanner = scannerRun)
    }

    /**
     * Scan the provided [pkg] for license information, writing results to [outputDirectory]. If a scan result is found
     * in the cache, it is used without running the actual scan. If no cached scan result is found, the package's source
     * code is downloaded and scanned afterwards.
     *
     * @param pkg The package to scan.
     * @param outputDirectory The base directory to store scan results in.
     * @param downloadDirectory The directory to download source code to. Defaults to [outputDirectory]/downloads if
     *                          null.
     *
     * @return The set of found licenses.
     *
     * @throws ScanException In case the package could not be scanned.
     */
    private fun scanPackage(scannerDetails: ScannerDetails, pkg: Package, outputDirectory: File,
                    downloadDirectory: File? = null): List<ScanResult> {
        val scanResultsDirectory = File(outputDirectory, "scanResults").apply { safeMkdirs() }
        val scanResultsForPackageDirectory = File(scanResultsDirectory, pkg.id.toPath()).apply { safeMkdirs() }
        val resultsFile = File(scanResultsForPackageDirectory, "scan-results_${scannerDetails.name}.$resultFileExt")

        val cachedResults = ScanResultsCache.read(pkg, scannerDetails)

        if (cachedResults.results.isNotEmpty()) {
            // Some external tools rely on the raw results filer to be written to the scan results directory, so write
            // the first cached result to resultsFile. This feature will be removed when the reporter tool becomes
            // available.
            resultsFile.mapper().writeValue(resultsFile, cachedResults.results.first().rawResult)
            return cachedResults.results
        }

        val downloadResult = try {
            Downloader().download(pkg, downloadDirectory ?: File(outputDirectory, "downloads"))
        } catch (e: DownloadException) {
            e.showStackTrace()

            val now = Instant.now()
            val scanResult = ScanResult(
                    Provenance(now),
                    scannerDetails,
                    ScanSummary(
                            startTime = now,
                            endTime = now,
                            fileCount = 0,
                            licenseFindings = sortedSetOf(),
                            errors = e.collectMessages().map { Error(source = javaClass.simpleName, message = it) }
                    ),
                    EMPTY_JSON_NODE
            )
            return listOf(scanResult)
        }

        println("Running $this version ${scannerDetails.version} on directory " +
                "'${downloadResult.downloadDirectory.absolutePath}'.")

        val provenance = Provenance(downloadResult.dateTime, downloadResult.sourceArtifact, downloadResult.vcsInfo,
                downloadResult.originalVcsInfo)
        val scanResult = scanPath(scannerDetails, downloadResult.downloadDirectory, provenance, resultsFile)

        ScanResultsCache.add(pkg.id, scanResult)

        return listOf(scanResult)
    }

    /**
     * Scan the provided [path] for license information, writing results to [outputDirectory] using the scanner's native
     * output file format. Note that no scan results cache is used by this function.
     *
     * @param path The directory or file to scan.
     * @param outputDirectory The base directory to store scan results in.
     *
     * @return The [ScanResult] with empty [Provenance] except for the download time.
     *
     * @throws ScanException In case the path could not be scanned.
     */
    fun scanPath(path: File, outputDirectory: File): ScanResult {
        val scannerDetails = getDetails()
        val scanResultsDirectory = File(outputDirectory, "scanResults").apply { safeMkdirs() }
        val resultsFile = File(scanResultsDirectory,
                "${path.nameWithoutExtension}_${scannerDetails.name}.$resultFileExt")

        println("Running $this version ${scannerDetails.version} on path '${path.absolutePath}'.")

        return scanPath(scannerDetails, path, Provenance(downloadTime = Instant.now()), resultsFile)
                .also { println("Stored $this results in '${resultsFile.absolutePath}'.") }
    }

    /**
     * Scan the provided [path] for license information, writing results to [resultsFile] using the scanner's native
     * output file format. Note that no scan results cache is used by this function.
     *
     * @param scannerDetails The [ScannerDetails] of the current scanner.
     * @param path The directory or file to scan.
     * @param provenance [Provenance] information about the files in [path].
     * @param resultsFile The file to store scan results in.
     *
     * @return The [ScanResult], containing the provided [provenance] and [scannerDetails].
     *
     * @throws ScanException In case the path could not be scanned.
     */
    protected abstract fun scanPath(scannerDetails: ScannerDetails, path: File, provenance: Provenance,
                                    resultsFile: File): ScanResult

    internal abstract fun getResult(resultsFile: File): JsonNode

    internal abstract fun generateSummary(startTime: Instant, endTime: Instant, result: JsonNode): ScanSummary
}
