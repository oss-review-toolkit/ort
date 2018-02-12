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
import com.here.ort.model.Package
import com.here.ort.scanner.scanners.*
import com.here.ort.utils.getPathFromEnvironment
import com.here.ort.utils.safeMkdirs

import java.io.File
import java.util.SortedSet

abstract class Scanner {
    /**
     * The directory the scanner was bootstrapped to, if so.
     */
    protected val scannerDir by lazy {
        getPathFromEnvironment(scannerExe)?.parentFile ?: bootstrap()?.also { it.deleteOnExit() }
    }

    /**
     * The scanner's executable file name.
     */
    protected abstract val scannerExe: String

    /**
     * A property containing the file name extension of the scanner's native output format, without the dot.
     */
    protected abstract val resultFileExt: String

    companion object {
        /**
         * The list of all available scanners. This needs to be initialized lazily to ensure the referred objects,
         * which derive from this class, exist.
         */
        val ALL by lazy {
            listOf(
                    BoyterLc,
                    Licensee,
                    ScanCode
            )
        }
    }

    data class Result(val licenses: SortedSet<String>, val errors: SortedSet<String>)

    /**
     * Return the Java class name as a simply way to refer to the scanner.
     */
    override fun toString(): String = javaClass.simpleName

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
        val pkgRevision = pkg.id.version.let { if (it.isBlank()) pkg.vcs.revision.take(7) else it }

        val resultsFile = File(scanResultsDirectory,
                "${pkg.id.name}-${pkgRevision}_$scannerName.$resultFileExt")

        if (ScanResultsCache.read(pkg, resultsFile)) {
            return getResult(resultsFile)
        }

        val sourceDirectory = try {
            Main.download(pkg, downloadDirectory ?: File(outputDirectory, "downloads"))
        } catch (e: DownloadException) {
            if (com.here.ort.utils.printStackTrace) {
                e.printStackTrace()
            }

            throw ScanException("Package '${pkg.id}' could not be scanned.", e)
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
    fun scan(path: File, outputDirectory: File): Result {
        val scanResultsDirectory = File(outputDirectory, "scanResults").apply { safeMkdirs() }
        val scannerName = toString().toLowerCase()
        val resultsFile = File(scanResultsDirectory,
                "${path.nameWithoutExtension}_$scannerName.$resultFileExt")

        return scanPath(path, resultsFile)
    }

    /**
     * Bootstrap the scanner to be ready for use, like downloading and / or configuring it.
     *
     * @return The directory the scanner is installed in, or null if the scanner was not bootstrapped.
     */
    protected open fun bootstrap(): File? = null

    /**
     * Scan the provided [path] for license information, writing results to [resultsFile].
     */
    protected abstract fun scanPath(path: File, resultsFile: File): Result

    /**
     * Convert the scanner's native file format to a [Result].
     */
    internal abstract fun getResult(resultsFile: File): Result
}
