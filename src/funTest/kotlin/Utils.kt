package com.here.provenanceanalyzer.functionaltest

import java.io.File

/**
 * A helper function to return the contents of a resource text file as a list of strings.
 *
 * @param path The path to the resource file.
 * @return The text as a list of strings.
 */
fun readResource(path: String): List<String> {
    // Create an anonymous class to be able to get a class reference, see https://stackoverflow.com/a/38230890/639421.
    @Suppress("EmptyClassBlock")
    return File(object {}.javaClass.getResource(path).toURI()).readLines()
}
