package com.here.provenanceanalyzer.scanner

import ch.frankel.slf4k.*
import ch.qos.logback.classic.Level

import com.here.provenanceanalyzer.downloader.Main
import com.here.provenanceanalyzer.model.Package
import com.here.provenanceanalyzer.util.ProcessCapture
import com.here.provenanceanalyzer.util.getCommandVersion
import com.here.provenanceanalyzer.util.log
import com.here.provenanceanalyzer.util.safeMkdirs

import java.io.File

object ScanCode : Scanner() {

    private const val OUTPUT_FORMAT = "json"
    private const val SCANCODE_PROCESSES = 6
    private const val SCANCODE_TIMEOUT = 600

    override fun scan(pkg: Package, outputDirectory: File) {
        val scancodeVersion = getCommandVersion("scancode", transform = {
            // "scancode --version" returns a string like "ScanCode version 2.0.1.post1.fb67a181", so remove the prefix.
            it.substringAfter("ScanCode version ")
        })

        log.info { "Detected ScanCode version $scancodeVersion." }

        // TODO: Check if scan result for package is available in cache

        val downloadDirectory = File(outputDirectory, "download").apply { safeMkdirs() }
        val scanResultsDirectory = File(outputDirectory, "scanResults").apply { safeMkdirs() }

        val sourceDirectory = Main.download(pkg, downloadDirectory)
        if (sourceDirectory != null) {
            val scancodeOptions = mutableListOf("--copyright", "--license", "--info", "--diag",
                    "--only-findings", "--strip-root")
            if (log.isEnabledFor(Level.DEBUG)) {
                scancodeOptions.add("--verbose")
            }

            val resultsFile = File(scanResultsDirectory,
                    "${pkg.name}-${pkg.version}_scancode-$scancodeVersion.$OUTPUT_FORMAT")

            println("Run ScanCode in directory '${sourceDirectory.absolutePath}'.")
            val process = ProcessCapture(sourceDirectory,
                    "scancode", *scancodeOptions.toTypedArray(),
                    "--timeout", SCANCODE_TIMEOUT.toString(),
                    "-n", SCANCODE_PROCESSES.toString(),
                    "-f", OUTPUT_FORMAT,
                    ".", resultsFile.absolutePath)

            if (process.exitValue() == 0) {
                println("Stored ScanCode results in ${resultsFile.absolutePath}.")
            } else {
                log.error {
                    "'${process.commandLine}' failed with exit code ${process.exitValue()}:\n${process.stderr()}"
                }
            }

            // TODO: convert json output to spdx
            // TODO: convert json output to html
            // TODO: Add results of license scan to YAML model
        } else {
            log.error { "Package ${pkg.identifier} could not be scanned." }
        }
    }

    override fun toString(): String {
        return javaClass.simpleName
    }

}
