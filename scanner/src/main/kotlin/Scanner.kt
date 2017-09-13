package com.here.provenanceanalyzer.scanner

import com.here.provenanceanalyzer.model.Package

import java.io.File

val SCANNERS = listOf(
        ScanCode
)

abstract class Scanner {

    abstract fun scan(pkg: Package, outputDirectory: File)

}
