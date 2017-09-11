package com.here.provenanceanalyzer.util

/**
 * Operating-System-specific utility functions.
 */
object OS {
    private val OS_NAME = System.getProperty("os.name").toLowerCase()

    val isWindows = OS_NAME.contains("windows")
}
