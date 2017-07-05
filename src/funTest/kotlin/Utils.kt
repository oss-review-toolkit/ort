package com.here.provenanceanalyzer.functionaltest

import java.io.File

internal object Resources

/**
 * A helper function to return the contents of a resource text file as a list of strings.
 *
 * @param path The path to the resource file.
 * @return The text as a list of strings.
 */
fun readResource(path: String): List<String> {
    return File(Resources.javaClass.getResource(path).toURI()).readLines()
}
