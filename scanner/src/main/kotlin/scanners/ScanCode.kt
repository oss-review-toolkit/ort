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
import com.here.ort.scanner.LocalScanner

import com.here.ort.scanner.ScanException
import com.here.ort.utils.OS
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.getCommandVersion
import com.here.ort.utils.jsonMapper
import com.here.ort.utils.log
import com.here.ort.utils.searchUpwardsForSubdirectory

import java.io.File
import java.util.regex.Pattern

object ScanCode : LocalScanner() {
    private const val OUTPUT_FORMAT = "json-pp"
    private const val TIMEOUT = 300

    private val DEFAULT_OPTIONS = listOf(
            "--copyright",
            "--license",
            "--license-text",
            "--info",
            "--only-findings",
            "--strip-root"
    )

    private val TIMEOUT_REGEX = Pattern.compile(
            "ERROR: Processing interrupted: timeout after (?<timeout>\\d+) seconds. \\(File: .+\\)")

    override val scannerExe = if (OS.isWindows) "scancode.bat" else "scancode"
    override val resultFileExt = "json"

    override fun bootstrap(): File? {
        val gitRoot = File(".").searchUpwardsForSubdirectory(".git")
        val scancodeDir = File(gitRoot, "scanner/src/funTest/assets/scanners/scancode-toolkit")
        if (!scancodeDir.isDirectory) return null
        val configureExe = if (OS.isWindows) "configure.bat" else "configure"
        val configurePath = File(scancodeDir, configureExe)
        ProcessCapture(configurePath.absolutePath, "--clean").requireSuccess()
        ProcessCapture(configurePath.absolutePath).requireSuccess()
        return scancodeDir
    }

    override fun getVersion(executable: String) =
            getCommandVersion(scannerPath.absolutePath, transform = {
                // "scancode --version" returns a string like "ScanCode version 2.0.1.post1.fb67a181", so simply remove
                // the prefix.
                it.substringAfter("ScanCode version ")
            })

    override fun scanPath(path: File, resultsFile: File): Result {
        val options = DEFAULT_OPTIONS.toMutableList()
        if (log.isEnabledFor(Level.DEBUG)) {
            options.add("--license-diag")
            options.add("--verbose")
        }

        val outputFormatOption = if (OUTPUT_FORMAT.startsWith("json")) {
            "--$OUTPUT_FORMAT"
        } else {
            "--output-$OUTPUT_FORMAT"
        }

        val process = ProcessCapture(
                scannerPath.absolutePath,
                *options.toTypedArray(),
                "--timeout", TIMEOUT.toString(),
                "--processes", Math.max(1, Runtime.getRuntime().availableProcessors() - 1).toString(),
                path.absolutePath,
                outputFormatOption, resultsFile.absolutePath
        )

        if (process.stderr().isNotBlank()) {
            log.debug { process.stderr() }
        }

        val result = getResult(resultsFile)

        with(process) {
            if (exitValue() == 0 || hasOnlyTimeoutErrors(result)) {
                return result
            } else {
                throw ScanException(failMessage)
            }
        }

        // TODO: convert json output to spdx
        // TODO: convert json output to html
        // TODO: Add results of license scan to YAML model
    }

    override fun getResult(resultsFile: File): Result {
        val licenses = sortedSetOf<String>()
        val errors = sortedSetOf<String>()

        if (resultsFile.isFile && resultsFile.length() > 0) {
            val json = jsonMapper.readTree(resultsFile)
            json["files"]?.forEach { file ->
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
        }

        return Result(licenses, errors)
    }

    internal fun hasOnlyTimeoutErrors(result: Result): Boolean {
        if (result.errors.isEmpty()) {
            return false
        }

        result.errors.forEach { error ->
            TIMEOUT_REGEX.matcher(error).let { matcher ->
                if (!matcher.matches() || matcher.group("timeout") != TIMEOUT.toString()) {
                    return false
                }
            }
        }

        return true
    }
}
