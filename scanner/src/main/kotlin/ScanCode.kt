package com.here.provenanceanalyzer.scanner

import ch.frankel.slf4k.*
import ch.qos.logback.classic.Level

import com.here.ort.downloader.DownloadException
import com.here.ort.downloader.Main
import com.here.provenanceanalyzer.model.Package
import com.here.provenanceanalyzer.util.ProcessCapture
import com.here.provenanceanalyzer.util.getCommandVersion
import com.here.provenanceanalyzer.util.jsonMapper
import com.here.provenanceanalyzer.util.log
import com.here.provenanceanalyzer.util.safeMkdirs

import java.io.File

object ScanCode : Scanner() {

    private const val OUTPUT_EXTENSION = "json"
    private const val OUTPUT_FORMAT = "json-pp"
    private const val SCANCODE_PROCESSES = 6
    private const val SCANCODE_TIMEOUT = 600

    override fun scan(pkg: Package, outputDirectory: File): Set<String> {
        val scancodeVersion = getCommandVersion("scancode", transform = {
            // "scancode --version" returns a string like "ScanCode version 2.0.1.post1.fb67a181", so remove the prefix.
            it.substringAfter("ScanCode version ")
        })

        log.info { "Detected ScanCode version $scancodeVersion." }

        val downloadDirectory = File(outputDirectory, "download").apply { safeMkdirs() }
        val scanResultsDirectory = File(outputDirectory, "scanResults").apply { safeMkdirs() }

        val resultsFile = File(scanResultsDirectory,
                "${pkg.name}-${pkg.version}_scancode.$OUTPUT_EXTENSION")

        if (ScanResultsCache.read(pkg, resultsFile)) {
            return parseLicenses(resultsFile)
        }

        try {
            val sourceDirectory = Main.download(pkg, downloadDirectory)

            val scancodeOptions = mutableListOf("--copyright", "--license", "--info", "--diag",
                    "--only-findings", "--strip-root")
            if (log.isEnabledFor(Level.DEBUG)) {
                scancodeOptions.add("--verbose")
            }

            println("Run ScanCode in directory '${sourceDirectory.absolutePath}'.")
            val process = ProcessCapture(sourceDirectory,
                    "scancode", *scancodeOptions.toTypedArray(),
                    "--timeout", SCANCODE_TIMEOUT.toString(),
                    "-n", SCANCODE_PROCESSES.toString(),
                    "-f", OUTPUT_FORMAT,
                    ".", resultsFile.absolutePath)

            with(process) {
                if (exitValue() == 0) {
                    println("Stored ScanCode results in ${resultsFile.absolutePath}.")
                    ScanResultsCache.write(pkg, resultsFile)
                    return parseLicenses(resultsFile)
                } else {
                    throw ScanException(failMessage)
                }
            }

            // TODO: convert json output to spdx
            // TODO: convert json output to html
            // TODO: Add results of license scan to YAML model
        } catch (e: DownloadException) {
            if (Main.stacktrace) {
                e.printStackTrace()
            }

            throw ScanException("Package ${pkg.identifier} could not be scanned.", e)
        }
    }

    private fun parseLicenses(resultsFile: File): Set<String> {
        val result = mutableSetOf<String>()
        val json = jsonMapper.readTree(resultsFile)
        val files = json["files"]
        files?.forEach { file ->
            val licenses = file["licenses"]
            licenses?.forEach { license ->
                var name = license["spdx_license_key"].asText()
                if (name.isNullOrBlank()) {
                    val key = license["key"].asText()
                    name = if (key == "unknown") "NOASSERTION" else "LicenseRef-$key"
                }
                result.add(name)
            }
        }
        return result
    }

    override fun toString(): String {
        return javaClass.simpleName
    }

}
