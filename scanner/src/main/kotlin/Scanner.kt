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
     * Scan the provided package for open source licenses.
     *
     * @param pkg The package to scan.
     * @param outputDirectory The directory to store scan results in.
     *
     * @return The set of found licenses.
     *
     * @throws ScanException In case the package could not be scanned.
     */
    abstract fun scan(pkg: Package, outputDirectory: File): Set<String>

}
