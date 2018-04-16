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

import ch.frankel.slf4k.*

import com.here.ort.downloader.DownloadException
import com.here.ort.downloader.Main
import com.here.ort.model.Package
import com.here.ort.utils.collectMessages
import com.here.ort.utils.encodeOrUnknown
import com.here.ort.utils.getPathFromEnvironment
import com.here.ort.utils.log
import com.here.ort.utils.safeMkdirs
import com.here.ort.utils.showStackTrace

import java.io.File

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
        getPathFromEnvironment(scannerExe)?.parentFile ?: run {
            log.info { "Bootstrapping scanner '$this' as it was not found in PATH." }
            bootstrap()
        }
    }

    /**
     * The scanner's executable file name.
     */
    protected abstract val scannerExe: String

    /**
     * The full path to the scanner executable.
     */
    protected val scannerPath by lazy { File(scannerDir, scannerExe) }

    /**
     * Bootstrap the scanner to be ready for use, like downloading and / or configuring it.
     *
     * @return The directory the scanner is installed in, or null if the scanner was not bootstrapped.
     */
    protected open fun bootstrap(): File? = null

    /**
     * Return the version of the specified scanner [executable], or an empty string in case of failure.
     */
    abstract fun getVersion(executable: String): String

    override fun scan(packages: List<Package>, outputDirectory: File, downloadDirectory: File?): Map<Package, Result> {
        return packages.associate { pkg ->
            val result = try {
                scan(pkg, outputDirectory, downloadDirectory)
            } catch (e: ScanException) {
                e.showStackTrace()

                log.error { "Could not scan package '${pkg.id}': ${e.message}" }

                Result(fileCount = 0, licenses = sortedSetOf(), errors = e.collectMessages().toSortedSet())
            }

            Pair(pkg, result)
        }
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
    fun scan(pkg: Package, outputDirectory: File, downloadDirectory: File? = null): Result {
        val scanResultsDirectory = File(outputDirectory, "scanResults").apply { safeMkdirs() }
        val scannerName = toString().toLowerCase()

        // TODO: Consider implementing this logic in the Package class itself when creating the identifier.
        // Also, think about what to use if we have neither a version nor a hash.
        val pkgRevision = pkg.id.version.takeUnless { it.isBlank() } ?: pkg.vcsProcessed.revision.take(7)

        val resultsFile = File(scanResultsDirectory,
                "${pkg.id.name.encodeOrUnknown()}-${pkgRevision}_$scannerName.$resultFileExt")

        if (ScanResultsCache.read(pkg, resultsFile)) {
            val results = getResult(resultsFile)
            if (results.fileCount > 0) {
                return results
            } else {
                // Ignore empty scan results. It is likely that something went wrong when they were created, and if not,
                // it is cheap to re-create them.

                log.info { "Ignoring cached scan result as it is empty." }
            }
        }

        val downloadResult = try {
            Main.download(pkg, downloadDirectory ?: File(outputDirectory, "downloads"))
        } catch (e: DownloadException) {
            e.showStackTrace()

            throw ScanException("Package '${pkg.id}' could not be scanned.", e)
        }

        val version = getVersion(scannerPath.absolutePath)
        println("Running $this version $version on directory '${downloadResult.downloadDirectory.canonicalPath}'.")

        return scanPath(downloadResult.downloadDirectory, resultsFile).also {
            println("Stored $this results in '${resultsFile.absolutePath}'.")

            val results = getResult(resultsFile)
            if (results.fileCount > 0) {
                ScanResultsCache.write(pkg, resultsFile)
            } else {
                // Ignore empty scan results. It is likely that something went wrong when they were created, and if not,
                // it is cheap to re-create them.

                log.info { "Not writing empty scan result to cache." }
            }
        }
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
    fun scan(path: File, outputDirectory: File): Result {
        val scanResultsDirectory = File(outputDirectory, "scanResults").apply { safeMkdirs() }
        val scannerName = toString().toLowerCase()
        val resultsFile = File(scanResultsDirectory,
                "${path.nameWithoutExtension}_$scannerName.$resultFileExt")

        val version = getVersion(scannerPath.absolutePath)
        println("Running $this version $version on path '${path.canonicalPath}'.")

        return scanPath(path, resultsFile).also {
            println("Stored $this results in '${resultsFile.absolutePath}'.")
        }
    }

    /**
     * Scan the provided [path] for license information, writing results to [resultsFile].
     */
    protected abstract fun scanPath(path: File, resultsFile: File): Result

    /**
     * Convert the scanner's native file format to a [Result].
     */
    internal abstract fun getResult(resultsFile: File): Result
}
