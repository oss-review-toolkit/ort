package com.here.provenanceanalyzer.scanner

import com.here.provenanceanalyzer.model.Package

import java.io.File

val SCANNERS = listOf(
        ScanCode
)

abstract class Scanner {

    /**
     * Scan the provided package for open source licenses.
     *
     * @param pkg The package to scan.
     * @param outputDirectory The directory to store scan results in.
     *
     * @throws ScanException In case the package could not be scanned.
     */
    abstract fun scan(pkg: Package, outputDirectory: File)

}
