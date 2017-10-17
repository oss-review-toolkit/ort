package com.here.ort.scanner

import com.here.ort.model.Package
import com.here.ort.scanner.scanners.*

import java.io.File

val ALL_SCANNERS = listOf(
        ScanCode
)

abstract class Scanner {

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
