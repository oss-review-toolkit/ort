/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

import com.fasterxml.jackson.databind.JsonNode

import com.here.ort.model.EMPTY_JSON_NODE
import com.here.ort.model.Provenance
import com.here.ort.model.ScanResult
import com.here.ort.model.ScanSummary
import com.here.ort.model.ScannerDetails
import com.here.ort.model.jsonMapper
import com.here.ort.scanner.LocalScanner
import com.here.ort.scanner.ScanException
import com.here.ort.utils.OS
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.getCommandVersion
import com.here.ort.utils.log
import com.here.ort.utils.searchUpwardsForSubdirectory

import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.regex.Pattern
import java.util.SortedSet

object ScanCode : LocalScanner() {
    private const val OUTPUT_FORMAT = "json-pp"
    private const val TIMEOUT = 300

    /**
     * Configuration options that are relevant for [getConfiguration] because they change the result file.
     */
    private val DEFAULT_CONFIGURATION_OPTIONS = listOf(
            "--copyright",
            "--license",
            "--info",
            "--strip-root",
            "--timeout", TIMEOUT.toString()
    )

    /**
     * Configuration options that are not relevant for [getConfiguration] because they do not change the result file.
     */
    private val DEFAULT_NON_CONFIGURATION_OPTIONS = listOf(
            "--processes", Math.max(1, Runtime.getRuntime().availableProcessors() - 1).toString()
    )

    /**
     * Debug configuration options that are relevant for [getConfiguration] because they change the result file.
     */
    private val DEBUG_CONFIGURATION_OPTIONS = listOf("--license-diag")

    /**
     * Debug configuration options that are not relevant for [getConfiguration] because they do not change the result
     * file.
     */
    private val DEBUG_NON_CONFIGURATION_OPTIONS = listOf("--verbose")

    private val OUTPUT_FORMAT_OPTION = if (OUTPUT_FORMAT.startsWith("json")) {
        "--$OUTPUT_FORMAT"
    } else {
        "--output-$OUTPUT_FORMAT"
    }

    // Note: The "(File: ...)" part in the patterns below is actually added by our own getResult() function.
    private val UNKNOWN_ERROR_REGEX = Pattern.compile(
            "(ERROR: for scanner: (?<scanner>\\w+):\n)?" +
                    "ERROR: Unknown error:\n.+\n(?<error>\\w+Error)(:|\n)(?<message>.*) \\(File: (?<file>.+)\\)",
            Pattern.DOTALL)

    private val TIMEOUT_ERROR_REGEX = Pattern.compile(
            "(ERROR: for scanner: (?<scanner>\\w+):\n)?" +
                    "ERROR: Processing interrupted: timeout after (?<timeout>\\d+) seconds. \\(File: (?<file>.+)\\)")

    override val scannerExe = if (OS.isWindows) "scancode.bat" else "scancode"
    override val scannerVersion = "2.9.2"
    override val resultFileExt = "json"

    override fun bootstrap(): File {
        val gitRoot = File(".").searchUpwardsForSubdirectory(".git")
        val scancodeDir = File(gitRoot, "scanner/src/funTest/assets/scanners/scancode-toolkit")
        if (!scancodeDir.isDirectory) throw IOException("Directory '$scancodeDir' not found.")

        val configureExe = if (OS.isWindows) "configure.bat" else "configure"
        val configurePath = File(scancodeDir, configureExe)
        ProcessCapture(configurePath.canonicalPath, "--clean").requireSuccess()
        ProcessCapture(configurePath.canonicalPath).requireSuccess()

        return scancodeDir
    }

    override fun getConfiguration() = DEFAULT_CONFIGURATION_OPTIONS.toMutableList().run {
        add(OUTPUT_FORMAT_OPTION)
        if (log.isEnabledFor(Level.DEBUG)) {
            addAll(DEBUG_CONFIGURATION_OPTIONS)
        }
        joinToString(" ")
    }

    override fun getVersion(dir: File) =
            getCommandVersion(dir.resolve(scannerExe).canonicalPath, transform = {
                // "scancode --version" returns a string like "ScanCode version 2.0.1.post1.fb67a181", so simply remove
                // the prefix.
                it.substringAfter("ScanCode version ")
            })

    override fun scanPath(scannerDetails: ScannerDetails, path: File, provenance: Provenance, resultsFile: File)
            : ScanResult {
        val options = (DEFAULT_CONFIGURATION_OPTIONS + DEFAULT_NON_CONFIGURATION_OPTIONS).toMutableList()

        if (log.isEnabledFor(Level.DEBUG)) {
            options += DEBUG_CONFIGURATION_OPTIONS
            options += DEBUG_NON_CONFIGURATION_OPTIONS
        }

        val startTime = Instant.now()

        val process = ProcessCapture(
                scannerPath.canonicalPath,
                *options.toTypedArray(),
                path.canonicalPath,
                OUTPUT_FORMAT_OPTION,
                resultsFile.canonicalPath
        )

        val endTime = Instant.now()

        if (process.stderr().isNotBlank()) {
            log.debug { process.stderr() }
        }

        val result = getResult(resultsFile)
        val summary = generateSummary(startTime, endTime, result)

        val hasOnlyMemoryErrors = mapUnknownErrors(summary.errors)
        val hasOnlyTimeoutErrors = mapTimeoutErrors(summary.errors)

        with(process) {
            if (isSuccess() || hasOnlyMemoryErrors || hasOnlyTimeoutErrors) {
                return ScanResult(provenance, scannerDetails, summary)
            } else {
                throw ScanException(failMessage)
            }
        }

        // TODO: convert json output to spdx
        // TODO: convert json output to html
        // TODO: Add results of license scan to YAML model
    }

    override fun getResult(resultsFile: File): JsonNode {
        return if (resultsFile.isFile && resultsFile.length() > 0L) {
            jsonMapper.readTree(resultsFile)
        } else {
            EMPTY_JSON_NODE
        }
    }

    override fun generateSummary(startTime: Instant, endTime: Instant, result: JsonNode): ScanSummary {
        val fileCount = result["files_count"].intValue()
        val licenses = sortedSetOf<String>()
        val errors = sortedSetOf<String>()

        result["files"]?.forEach { file ->
            file["licenses"]?.forEach { license ->
                var name = license["spdx_license_key"].asText()
                if (name.isNullOrBlank()) {
                    val key = license["key"].asText()
                    name = if (key == "unknown") "NOASSERTION" else "LicenseRef-$key"
                }
                licenses.add(name)
            }

            val path = file["path"].asText()
            errors.addAll(file["scan_errors"].map { "${it.asText()} (File: $path)" })
        }

        // Work around https://youtrack.jetbrains.com/issue/KT-20972.
        val findings = licenses.associate { Pair(it, emptySet<String>().toSortedSet()) }.toSortedMap()
        return ScanSummary(startTime, endTime, fileCount, findings, errors)
    }

    /**
     * Map messages about unknown errors to a more compact form. Return true if solely memory errors occurred, return
     * false otherwise.
     */
    internal fun mapUnknownErrors(errors: SortedSet<String>): Boolean {
        if (errors.isEmpty()) {
            return false
        }

        var onlyMemoryErrors = true

        val mappedErrors = errors.map { fullError ->
            UNKNOWN_ERROR_REGEX.matcher(fullError).let { matcher ->
                if (matcher.matches()) {
                    val file = matcher.group("file")
                    val error = matcher.group("error")
                    if (error == "MemoryError") {
                        "ERROR: MemoryError while scanning file '$file'."
                    } else {
                        onlyMemoryErrors = false
                        val message = matcher.group("message").trim()
                        "ERROR: $error while scanning file '$file' ($message)."
                    }
                } else {
                    onlyMemoryErrors = false
                    fullError
                }
            }
        }

        errors.clear()
        errors.addAll(mappedErrors)

        return onlyMemoryErrors
    }

    /**
     * Map messages about timeout errors to a more compact form. Return true if solely timeout errors occurred, return
     * false otherwise.
     */
    internal fun mapTimeoutErrors(errors: SortedSet<String>): Boolean {
        if (errors.isEmpty()) {
            return false
        }

        var onlyTimeoutErrors = true

        val mappedErrors = errors.map { fullError ->
            TIMEOUT_ERROR_REGEX.matcher(fullError).let { matcher ->
                if (matcher.matches() && matcher.group("timeout") == TIMEOUT.toString()) {
                    val file = matcher.group("file")
                    "ERROR: Timeout after $TIMEOUT seconds while scanning file '$file'."
                } else {
                    onlyTimeoutErrors = false
                    fullError
                }
            }
        }

        errors.clear()
        errors.addAll(mappedErrors)

        return onlyTimeoutErrors
    }
}
