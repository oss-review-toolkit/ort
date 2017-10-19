package com.here.ort.scanner.scanners

import ch.frankel.slf4k.*
import ch.qos.logback.classic.Level

import com.here.ort.downloader.DownloadException
import com.here.ort.downloader.Main
import com.here.ort.model.Package
import com.here.ort.scanner.ScanException
import com.here.ort.scanner.ScanResultsCache
import com.here.ort.scanner.Scanner
import com.here.ort.util.ProcessCapture
import com.here.ort.util.getCommandVersion
import com.here.ort.util.jsonMapper
import com.here.ort.util.log
import com.here.ort.util.safeMkdirs

import java.io.File

object ScanCode : Scanner() {

    private const val OUTPUT_EXTENSION = "json"
    private const val OUTPUT_FORMAT = "json-pp"
    private const val PROCESSES = 6
    private const val TIMEOUT = 600

    private val DEFAULT_OPTIONS = listOf("--copyright", "--license", "--info", "--diag", "--only-findings",
            "--strip-root")

    override fun scan(pkg: Package, outputDirectory: File): Set<String> {
        val downloadDirectory = File(outputDirectory, "download").apply { safeMkdirs() }
        val scanResultsDirectory = File(outputDirectory, "scanResults").apply { safeMkdirs() }

        val resultsFile = File(scanResultsDirectory,
                "${pkg.name}-${pkg.version}_scancode.$OUTPUT_EXTENSION")

        if (ScanResultsCache.read(pkg, resultsFile)) {
            return parseLicenses(resultsFile)
        }

        val sourceDirectory = try {
            Main.download(pkg, downloadDirectory)
        } catch (e: DownloadException) {
            if (Main.stacktrace) {
                e.printStackTrace()
            }

            throw ScanException("Package '${pkg.identifier}' could not be scanned.", e)
        }

        return scanPath(sourceDirectory, resultsFile).also { ScanResultsCache.write(pkg, resultsFile) }
    }

    override fun scan(path: File, outputDirectory: File): Set<String> {
        val scanResultsDirectory = File(outputDirectory, "scanResults").apply { safeMkdirs() }

        val resultsFile = File(scanResultsDirectory,
                "${path.nameWithoutExtension}_scancode.$OUTPUT_EXTENSION")

        return scanPath(path, resultsFile)
    }

    private fun scanPath(path: File, resultsFile: File): Set<String> {
        val scancodeVersion = getCommandVersion("scancode", transform = {
            // "scancode --version" returns a string like "ScanCode version 2.0.1.post1.fb67a181", so remove the prefix.
            it.substringAfter("ScanCode version ")
        })

        log.info { "Detected ScanCode version $scancodeVersion." }

        val scancodeOptions = DEFAULT_OPTIONS.toMutableList()
        if (log.isEnabledFor(Level.DEBUG)) {
            scancodeOptions.add("--verbose")
        }

        println("Running ScanCode in directory '${path.absolutePath}'...")
        val process = ProcessCapture(
                "scancode", *scancodeOptions.toTypedArray(),
                "--timeout", TIMEOUT.toString(),
                "-n", PROCESSES.toString(),
                "-f", OUTPUT_FORMAT,
                path.absolutePath,
                resultsFile.absolutePath
        )

        with(process) {
            if (exitValue() == 0) {
                println("Stored ScanCode results in '${resultsFile.absolutePath}'.")
                return parseLicenses(resultsFile)
            } else {
                throw ScanException(failMessage)
            }
        }

        // TODO: convert json output to spdx
        // TODO: convert json output to html
        // TODO: Add results of license scan to YAML model
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
