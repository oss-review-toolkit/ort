/*
 * Copyright (c) 2017 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

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
    private const val TIMEOUT = 300

    private val DEFAULT_OPTIONS = listOf("--copyright", "--license", "--info", "--diag", "--only-findings",
            "--strip-root")

    override fun scan(pkg: Package, outputDirectory: File): Set<String> {
        val scanResultsDirectory = File(outputDirectory, "scanResults").apply { safeMkdirs() }
        val scannerName = javaClass.simpleName.toLowerCase()
        val resultsFile = File(scanResultsDirectory,
                "${pkg.name}-${pkg.version}_$scannerName.$OUTPUT_EXTENSION")

        if (ScanResultsCache.read(pkg, resultsFile)) {
            return parseLicenses(resultsFile)
        }

        val sourceDirectory = try {
            val downloadDirectory = File(outputDirectory, "download").apply { safeMkdirs() }
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
        val scannerName = javaClass.simpleName.toLowerCase()
        val resultsFile = File(scanResultsDirectory,
                "${path.nameWithoutExtension}_$scannerName.$OUTPUT_EXTENSION")

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
