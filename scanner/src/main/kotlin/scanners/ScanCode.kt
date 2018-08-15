/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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
import com.here.ort.model.Error
import com.here.ort.model.LicenseFinding
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
import com.here.ort.utils.spdx.LICENSE_FILE_NAMES

import java.io.File
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Paths
import java.time.Instant
import java.util.regex.Pattern
import java.util.SortedMap
import java.util.SortedSet

import kotlin.math.absoluteValue

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
        ProcessCapture(configurePath.absolutePath, "--clean").requireSuccess()
        ProcessCapture(configurePath.absolutePath).requireSuccess()

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
            getCommandVersion(dir.resolve(scannerExe).absolutePath, transform = {
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
                scannerPath.absolutePath,
                *options.toTypedArray(),
                path.absolutePath,
                OUTPUT_FORMAT_OPTION,
                resultsFile.absolutePath
        )

        val endTime = Instant.now()

        if (process.stderr().isNotBlank()) {
            log.debug { process.stderr() }
        }

        val result = getResult(resultsFile)
        val summary = generateSummary(startTime, endTime, result)

        val errors = summary.errors.toMutableList()

        val hasOnlyMemoryErrors = mapUnknownErrors(errors)
        val hasOnlyTimeoutErrors = mapTimeoutErrors(errors)

        with(process) {
            if (isSuccess() || hasOnlyMemoryErrors || hasOnlyTimeoutErrors) {
                return ScanResult(provenance, scannerDetails, summary.copy(errors = errors), result)
            } else {
                throw ScanException(errorMessage)
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
        val findings = associateFindings(result)
        val errors = mutableListOf<Error>()

        result["files"]?.forEach { file ->
            val path = file["path"].textValue()
            errors += file["scan_errors"].map {
                Error(source = javaClass.simpleName, message = "${it.textValue()} (File: $path)")
            }
        }

        return ScanSummary(startTime, endTime, fileCount, findings, errors)
    }

    /**
     * Map messages about unknown errors to a more compact form. Return true if solely memory errors occurred, return
     * false otherwise.
     */
    internal fun mapUnknownErrors(errors: MutableList<Error>): Boolean {
        if (errors.isEmpty()) {
            return false
        }

        var onlyMemoryErrors = true

        val mappedErrors = errors.map { fullError ->
            UNKNOWN_ERROR_REGEX.matcher(fullError.message).let { matcher ->
                if (matcher.matches()) {
                    val file = matcher.group("file")
                    val error = matcher.group("error")
                    if (error == "MemoryError") {
                        fullError.copy(message = "ERROR: MemoryError while scanning file '$file'.")
                    } else {
                        onlyMemoryErrors = false
                        val message = matcher.group("message").trim()
                        fullError.copy(message = "ERROR: $error while scanning file '$file' ($message).")
                    }
                } else {
                    onlyMemoryErrors = false
                    fullError
                }
            }
        }

        errors.clear()
        errors += mappedErrors.distinctBy { it.message }

        return onlyMemoryErrors
    }

    /**
     * Map messages about timeout errors to a more compact form. Return true if solely timeout errors occurred, return
     * false otherwise.
     */
    internal fun mapTimeoutErrors(errors: MutableList<Error>): Boolean {
        if (errors.isEmpty()) {
            return false
        }

        var onlyTimeoutErrors = true

        val mappedErrors = errors.map { fullError ->
            TIMEOUT_ERROR_REGEX.matcher(fullError.message).let { matcher ->
                if (matcher.matches() && matcher.group("timeout") == TIMEOUT.toString()) {
                    val file = matcher.group("file")
                    fullError.copy(message = "ERROR: Timeout after $TIMEOUT seconds while scanning file '$file'.")
                } else {
                    onlyTimeoutErrors = false
                    fullError
                }
            }
        }

        errors.clear()
        errors += mappedErrors.distinctBy { it.message }

        return onlyTimeoutErrors
    }

    /**
     * Get the SPDX license id (or a fallback) for a license finding.
     */
    private fun getLicenseId(license: JsonNode): String {
        var name = license["spdx_license_key"].asText()

        if (name.isEmpty()) {
            val key = license["key"].asText()
            name = if (key == "unknown") "NOASSERTION" else "LicenseRef-$key"
        }

        return name
    }

    /**
     * Get the license found in one of the commonly named license files, if any, or an empty string otherwise.
     */
    internal fun getRootLicense(result: JsonNode): String {
        val matchersForLicenseFiles = LICENSE_FILE_NAMES.map {
            FileSystems.getDefault().getPathMatcher("glob:$it")
        }

        val rootLicenseFile = result["files"].singleOrNull {
            val path = it["path"].asText()
            matchersForLicenseFiles.any { it.matches(Paths.get(path)) }
        } ?: return ""

        return rootLicenseFile["licenses"].singleOrNull()?.let { getLicenseId(it) } ?: ""
    }

    /**
     * Return the copyright statements in the vicinity, as specified by [toleranceLines], of [startLine]. The default
     * value of [toleranceLines] is set to 5 which seems to be a good balance between associating findings separated by
     * blank lines but not skipping complete license statements.
     */
    internal fun getClosestCopyrightStatements(copyrights: JsonNode, startLine: Int, toleranceLines: Int = 5):
            SortedSet<String> {
        val closestCopyrights = copyrights.filter {
            (it["start_line"].asInt() - startLine).absoluteValue <= toleranceLines
        }

        return closestCopyrights.flatMap { it["statements"] }.map { it.asText() }.toSortedSet()
    }

    /**
     * Associate copyright findings to license findings within a single file.
     */
    private fun associateFileFindings(licenses: JsonNode, copyrights: JsonNode, rootLicense: String = ""):
            SortedMap<String, SortedSet<String>> {
        val copyrightsForLicenses = sortedMapOf<String, SortedSet<String>>()
        val allCopyrightStatements = copyrights.flatMap { it["statements"] }.map { it.asText() }.toSortedSet()

        when (licenses.size()) {
            0 -> {
                // If there is no license finding but copyright findings, associate them with the root license, if any.
                if (allCopyrightStatements.isNotEmpty() && rootLicense.isNotEmpty()) {
                    copyrightsForLicenses[rootLicense] = allCopyrightStatements
                }
            }

            1 -> {
                // If there is only a single license finding, associate all copyright findings with that license.
                val licenseId = getLicenseId(licenses.first())
                copyrightsForLicenses[licenseId] = allCopyrightStatements
            }

            else -> {
                // If there are multiple license findings in a single file, search for the closest copyright statements
                // for each of these, if any.
                licenses.forEach {
                    val licenseId = getLicenseId(it)
                    val licenseStartLine = it["start_line"].asInt()
                    val closestCopyrights = getClosestCopyrightStatements(copyrights, licenseStartLine)
                    copyrightsForLicenses.getOrPut(licenseId) { sortedSetOf() } += closestCopyrights
                }
            }
        }

        return copyrightsForLicenses
    }

    /**
     * Associate copyright findings to license findings throughout the whole result.
     */
    internal fun associateFindings(result: JsonNode): SortedSet<LicenseFinding> {
        val copyrightsForLicenses = sortedMapOf<String, SortedSet<String>>()
        val rootLicense = getRootLicense(result)

        result["files"].forEach { file ->
            val licenses = file["licenses"] ?: EMPTY_JSON_NODE
            val copyrights = file["copyrights"] ?: EMPTY_JSON_NODE
            val findings = associateFileFindings(licenses, copyrights, rootLicense)
            findings.forEach { license, copyrightsForLicense ->
                copyrightsForLicenses.getOrPut(license) { sortedSetOf() } += copyrightsForLicense
            }
        }

        return copyrightsForLicenses.map { (license, copyrights) ->
            LicenseFinding(license, copyrights)
        }.toSortedSet()
    }
}
