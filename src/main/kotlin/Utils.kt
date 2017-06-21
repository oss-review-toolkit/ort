package com.here.provenanceanalyzer

object OS {
    private val OS_NAME = System.getProperty("os.name").toLowerCase()

    val isWindows get() = OS_NAME.contains("windows")
}
