package com.here.ort.scanner

import com.here.ort.model.Package
import com.here.ort.scanner.scanners.*

import java.io.File

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
     * Scan the provided package for license information. If a scan result is found in the cache, it is used without
     * running the actual scan. If no cached scan result is found, the package's source code is downloaded automatically
     * and scanned afterwards.
     *
     * @param pkg The package to scan.
     * @param outputDirectory The directory to store scan results in.
     *
     * @return The set of found licenses.
     *
     * @throws ScanException In case the package could not be scanned.
     */
    abstract fun scan(pkg: Package, outputDirectory: File): Set<String>

    /**
     * Scan the provided path for license information. Note that no caching will be used in this mode.
     *
     * @param path The directory or file to scan.
     * @param outputDirectory The directory to store scan results in.
     *
     * @return The set of found licenses.
     *
     * @throws ScanException In case the package could not be scanned.
     */
    abstract fun scan(path: File, outputDirectory: File): Set<String>

}
