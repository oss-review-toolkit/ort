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

import com.here.ort.scanner.ScanException
import com.here.ort.scanner.Scanner
import com.here.ort.utils.OS
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.getCommandVersion
import com.here.ort.utils.jsonMapper
import com.here.ort.utils.log

import java.io.File

object ScanCode : Scanner() {
    private const val OUTPUT_FORMAT = "json-pp"
    private const val TIMEOUT = 300

    private val DEFAULT_OPTIONS = listOf("--copyright", "--license", "--info", "--diag", "--only-findings",
            "--strip-root")

    private val TIMEOUT_REGEX = Regex("ERROR: Processing interrupted: timeout after (?<timeout>\\d+) seconds.")

    override val resultFileExtension = "json"

    override fun scanPath(path: File, resultsFile: File): Result {
        val executable = if (OS.isWindows) "scancode.bat" else "scancode"

        log.info { "Detecting the ScanCode version..." }

        val version = getCommandVersion(executable, transform = {
            // "scancode --version" returns a string like "ScanCode version 2.0.1.post1.fb67a181", so remove the prefix.
            it.substringAfter("ScanCode version ")
        })

        log.info { "Using ScanCode version $version." }

        val options = DEFAULT_OPTIONS.toMutableList()
        if (log.isEnabledFor(Level.DEBUG)) {
            options.add("--verbose")
        }

        println("Running ScanCode in directory '${path.absolutePath}'...")
        val process = ProcessCapture(
                executable,
                *options.toTypedArray(),
                "--timeout", TIMEOUT.toString(),
                "-n", Math.max(1, Runtime.getRuntime().availableProcessors() - 1).toString(),
                "-f", OUTPUT_FORMAT,
                path.absolutePath,
                resultsFile.absolutePath
        )

        val result = getResult(resultsFile)

        with(process) {
            if (exitValue() == 0 || hasOnlyTimeoutErrors(result)) {
                println("Stored ScanCode results in '${resultsFile.absolutePath}'.")
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

        if (resultsFile.isFile) {
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

                errors.addAll(file["scan_errors"].map { it.asText() })
            }
        }

        return Result(licenses, errors)
    }

    internal fun hasOnlyTimeoutErrors(result: Result): Boolean {
        result.errors.singleOrNull()?.let { error ->
            TIMEOUT_REGEX.matchEntire(error)?.let { match ->
                return match.groups["timeout"]!!.value == TIMEOUT.toString()
            }
        }

        return false
    }
}
