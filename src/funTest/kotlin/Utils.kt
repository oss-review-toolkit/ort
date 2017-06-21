package com.here.provenanceanalyzer.functionaltest

import java.io.File

fun readResource(path: String): List<String> {
    // Create an anonymous class to be able to get a class reference, see https://stackoverflow.com/a/38230890/639421.
    @Suppress("EmptyClassBlock")
    return File(object {}.javaClass.getResource(path).toURI()).readLines()
}
