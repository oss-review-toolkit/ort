/*
 * Copyright (c) 2017 HERE Europe B.V.
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
import com.here.ort.model.Package
import com.here.ort.scanner.scanners.*
import com.here.ort.util.safeMkdirs

import java.io.File
import java.util.SortedSet

typealias ScannerResult = SortedSet<String>

abstract class Scanner {

    companion object {
        /**
         * The list of all available scanners. This needs to be initialized lazily to ensure the referred objects,
         * which derive from this class, exist.
         */
        val ALL by lazy {
            listOf(
                    ScanCode
            )
        }
    }

    /**
     * Return the Java class name as a simply way to refer to the scanner.
     */
    override fun toString(): String {
        return javaClass.simpleName
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
    fun scan(pkg: Package, outputDirectory: File, downloadDirectory: File? = null): ScannerResult {
        val scanResultsDirectory = File(outputDirectory, "scanResults").apply { safeMkdirs() }
        val scannerName = toString().toLowerCase()

        // TODO: Consider implementing this logic in the Package class itself when creating the identifier.
        // Also, think about what to use if we have neither a version nor a hash.
        val pkgRevision = pkg.version.let { if (it.isBlank()) pkg.vcsRevision.take(7) else it }

        val resultsFile = File(scanResultsDirectory,
                "${pkg.name}-${pkgRevision}_$scannerName.$resultFileExtension")

        if (ScanResultsCache.read(pkg, resultsFile)) {
            return toScannerResult(resultsFile)
        }

        val sourceDirectory = try {
            Main.download(pkg, downloadDirectory ?: File(outputDirectory, "downloads"))
        } catch (e: DownloadException) {
            if (Main.stacktrace) {
                e.printStackTrace()
            }

            throw ScanException("Package '${pkg.identifier}' could not be scanned.", e)
        }

        return scanPath(sourceDirectory, resultsFile).also { ScanResultsCache.write(pkg, resultsFile) }
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
    fun scan(path: File, outputDirectory: File): ScannerResult {
        val scanResultsDirectory = File(outputDirectory, "scanResults").apply { safeMkdirs() }
        val scannerName = toString().toLowerCase()
        val resultsFile = File(scanResultsDirectory,
                "${path.nameWithoutExtension}_$scannerName.$resultFileExtension")

        return scanPath(path, resultsFile)
    }

    /**
     * A property containing the file name extension of the scanner's native output format, without the dot.
     */
    protected abstract val resultFileExtension: String

    /**
     * Scan the provided [path] for license information, writing results to [resultsFile].
     */
    protected abstract fun scanPath(path: File, resultsFile: File): ScannerResult

    /**
     * Convert the scanner's native file format to a [ScannerResult].
     */
    protected abstract fun toScannerResult(resultsFile: File): ScannerResult

}
