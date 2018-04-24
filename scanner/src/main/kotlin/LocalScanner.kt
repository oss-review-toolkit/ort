/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

import com.here.ort.downloader.DownloadException
import com.here.ort.downloader.Main
import com.here.ort.model.EMPTY_JSON_NODE
import com.here.ort.model.Package
import com.here.ort.model.Provenance
import com.here.ort.model.ScanResult
import com.here.ort.model.ScanSummary
import com.here.ort.model.ScannerDetails
import com.here.ort.model.mapper
import com.here.ort.utils.collectMessages
import com.here.ort.utils.getPathFromEnvironment
import com.here.ort.utils.safeMkdirs
import com.here.ort.utils.showStackTrace

import java.io.File
import java.time.Instant

/**
 * Implementation of [Scanner] for scanners that operate locally. Packages passed to [scan] are processed in serial
 * order. Scan results can be cached in a [ScanResultsCache].
 */
abstract class LocalScanner : Scanner() {
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
            println("Bootstrapping scanner '$this' as version $scannerVersion was not found in PATH.")
            bootstrap()
        }
    }

    /**
     * The scanner's executable file name.
     */
    protected abstract val scannerExe: String

    /**
     * The expected version of the scanner. This is also the version that would get bootstrapped.
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
    private fun getDetails() = ScannerDetails(getName(), getVersion(), getConfiguration())

    /**
     * Return the actual version of the scanner, or an empty string in case of failure.
     */
    abstract fun getVersion(dir: File = scannerDir): String

    override fun scan(packages: List<Package>, outputDirectory: File, downloadDirectory: File?) =
            packages.associate { pkg ->
                val result = try {
                    scan(pkg, outputDirectory, downloadDirectory)
                } catch (e: ScanException) {
                    listOf(ScanResult(
                            provenance = Provenance(Instant.now()),
                            scanner = getDetails(),
                            summary = ScanSummary(
                                    startTime = Instant.now(),
                                    endTime = Instant.now(),
                                    fileCount = 0,
                                    licenses = sortedSetOf(),
                                    errors = e.collectMessages().toSortedSet()
                            ),
                            rawResult = EMPTY_JSON_NODE)
                    )
                }

                Pair(pkg, result)
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
    fun scan(pkg: Package, outputDirectory: File, downloadDirectory: File? = null): List<ScanResult> {
        val details = getDetails()

        val scanResultsDirectory = File(outputDirectory, "scanResults").apply { safeMkdirs() }
        val scanResultsForPackageDirectory = File(scanResultsDirectory, pkg.id.toPath()).apply { safeMkdirs() }
        val resultsFile = File(scanResultsForPackageDirectory, "scan-results_${details.name}.$resultFileExt")

        val cachedResults = ScanResultsCache.read(pkg, details)

        if (cachedResults.results.isNotEmpty()) {
            // Some external tools rely on the raw results filer to be written to the scan results directory, so write
            // the first cached result to resultsFile. This feature will be removed when the reporter tool becomes
            // available.
            resultsFile.mapper().writeValue(resultsFile, cachedResults.results.first().rawResult)
            return cachedResults.results
        }

        val downloadResult = try {
            Main.download(pkg, downloadDirectory ?: File(outputDirectory, "downloads"))
        } catch (e: DownloadException) {
            e.showStackTrace()

            val scanResult = ScanResult(
                    Provenance(Instant.now()),
                    details,
                    ScanSummary(
                            startTime = Instant.now(),
                            endTime = Instant.now(),
                            fileCount = 0,
                            licenses = sortedSetOf(),
                            errors = sortedSetOf("Package '${pkg.id}' could not be scanned because no source code " +
                                    "could be downloaded: ${e.message}")
                    ),
                    EMPTY_JSON_NODE
            )
            return listOf(scanResult)
        }

        println("Running $this version ${getVersion()} on directory " +
                "'${downloadResult.downloadDirectory.canonicalPath}'.")

        val provenance = Provenance(downloadResult.dateTime, downloadResult.sourceArtifact, downloadResult.vcsInfo)
        val scanResult = scanPath(downloadResult.downloadDirectory, resultsFile, provenance, details)

        ScanResultsCache.add(pkg.id, scanResult)

        return listOf(scanResult)
    }

    /**
     * Scan the provided [path] for license information, writing results to [outputDirectory]. Note that no caching will
     * be used in this mode.
     *
     * @param path The directory or file to scan.
     * @param outputDirectory The base directory to store scan results in.
     *
     * @return The set of found licenses.
     *
     * @throws ScanException In case the package could not be scanned.
     */
    fun scan(path: File, outputDirectory: File): ScanResult {
        val scanResultsDirectory = File(outputDirectory, "scanResults").apply { safeMkdirs() }
        val scannerName = toString().toLowerCase()
        val resultsFile = File(scanResultsDirectory,
                "${path.nameWithoutExtension}_$scannerName.$resultFileExt")

        println("Running $this version ${getVersion()} on path '${path.canonicalPath}'.")

        return scanPath(path, resultsFile, Provenance(downloadTime = Instant.now()), getDetails())
                .also { println("Stored $this results in '${resultsFile.absolutePath}'.") }
    }

    /**
     * Scan the provided [path] for license information, writing results to [resultsFile].
     */
    protected abstract fun scanPath(path: File, resultsFile: File, provenance: Provenance,
                                    scannerDetails: ScannerDetails): ScanResult

    internal abstract fun getResult(resultsFile: File): Result
}
