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
import com.here.ort.utils.getPathFromEnvironment
import com.here.ort.utils.log

import java.io.File
import java.io.IOException
import java.time.Instant

object Licensee : LocalScanner() {
    override val scannerExe = if (OS.isWindows) "licensee.bat" else "licensee"
    override val scannerVersion = "9.9.0.beta.3"
    override val resultFileExt = "json"

    val CONFIGURATION_OPTIONS = listOf("--json")

    override fun bootstrap(): File {
        val gem = if (OS.isWindows) "gem.cmd" else "gem"

        // Work around Travis CI not being able to handle gem user installs, see
        // https://github.com/travis-ci/travis-ci/issues/9412.
        // TODO: Use toBoolean() here once https://github.com/JetBrains/kotlin/pull/1644 is merged.
        val isTravisCi = listOf("TRAVIS", "CI").all { java.lang.Boolean.parseBoolean(System.getenv(it)) }
        return if (isTravisCi) {
            ProcessCapture(gem, "install", "licensee", "-v", scannerVersion).requireSuccess()
            getPathFromEnvironment(scannerExe)?.parentFile
                    ?: throw IOException("Install directory for licensee not found.")
        } else {
            ProcessCapture(gem, "install", "--user-install", "licensee", "-v", scannerVersion).requireSuccess()

            val ruby = ProcessCapture("ruby", "-rubygems", "-e", "puts Gem.user_dir").requireSuccess()
            val userDir = ruby.stdout().trimEnd()

            File(userDir, "bin")
        }
    }

    override fun getConfiguration() = CONFIGURATION_OPTIONS.joinToString(" ")

    override fun getVersion(dir: File) = getCommandVersion(dir.resolve(scannerExe).absolutePath, "version")

    override fun scanPath(path: File, resultsFile: File, provenance: Provenance, scannerDetails: ScannerDetails)
            : ScanResult {
        // Licensee has issues with absolute Windows paths passed as an argument. Work around that by using the path to
        // scan as the working directory.
        val (parentPath, relativePath) = if (path.isDirectory) {
            Pair(path, ".")
        } else {
            Pair(path.parentFile, path.name)
        }

        val startTime = Instant.now()

        val process = ProcessCapture(
                parentPath,
                scannerPath.absolutePath,
                "detect",
                *CONFIGURATION_OPTIONS.toTypedArray(),
                relativePath
        )

        val endTime = Instant.now()

        if (process.stderr().isNotBlank()) {
            log.debug { process.stderr() }
        }

        with(process) {
            if (isSuccess()) {
                stdoutFile.copyTo(resultsFile)
                val result = getResult(resultsFile)
                val summary = ScanSummary(startTime, endTime, result.fileCount, result.licenses, result.errors)
                return ScanResult(provenance, scannerDetails, summary, result.rawResult)
            } else {
                throw ScanException(failMessage)
            }
        }
    }

    override fun getResult(resultsFile: File): Result {
        var fileCount = 0
        val licenses = sortedSetOf<String>()
        val errors = sortedSetOf<String>()

        val json = if (resultsFile.isFile && resultsFile.length() > 0) {
            jsonMapper.readTree(resultsFile.readText()).also {
                val licenseSummary = it["licenses"]
                val matchedFiles = it["matched_files"]

                fileCount = matchedFiles.count()

                matchedFiles.forEach {
                    val licenseKey = it["matched_license"].asText()
                    licenseSummary.find {
                        it["key"].asText() == licenseKey
                    }?.let {
                        licenses.add(it["spdx_id"].asText())
                    }
                }
            }
        } else {
            EMPTY_JSON_NODE
        }

        return Result(fileCount, licenses, errors, json)
    }
}
