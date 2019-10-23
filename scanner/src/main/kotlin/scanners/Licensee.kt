/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import com.fasterxml.jackson.databind.JsonNode

import com.here.ort.model.EMPTY_JSON_NODE
import com.here.ort.model.LicenseFinding
import com.here.ort.model.Provenance
import com.here.ort.model.ScanResult
import com.here.ort.model.ScanSummary
import com.here.ort.model.TextLocation
import com.here.ort.model.config.ScannerConfiguration
import com.here.ort.model.jsonMapper
import com.here.ort.scanner.AbstractScannerFactory
import com.here.ort.scanner.LocalScanner
import com.here.ort.scanner.ScanException
import com.here.ort.spdx.calculatePackageVerificationCode
import com.here.ort.utils.Ci
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.Os
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.getPathFromEnvironment
import com.here.ort.utils.log

import java.io.File
import java.io.IOException
import java.time.Instant

class Licensee(name: String, config: ScannerConfiguration) : LocalScanner(name, config) {
    class Factory : AbstractScannerFactory<Licensee>("Licensee") {
        override fun create(config: ScannerConfiguration) = Licensee(scannerName, config)
    }

    companion object {
        val CONFIGURATION_OPTIONS = listOf("--json")
    }

    override val scannerVersion = "9.12.0"
    override val resultFileExt = "json"

    override fun command(workingDir: File?) = if (Os.isWindows) "licensee.bat" else "licensee"

    override fun getVersion(dir: File): String {
        // Create a temporary tool to get its version from the installation in a specific directory.
        val cmd = command()
        val tool = object : CommandLineTool {
            override fun command(workingDir: File?) = dir.resolve(cmd).absolutePath
        }

        return tool.getVersion("version")
    }

    override fun bootstrap(): File {
        val gem = if (Os.isWindows) "gem.cmd" else "gem"

        // Work around Travis CI not being able to handle gem user installs, see
        // https://github.com/travis-ci/travis-ci/issues/9412.
        return if (Ci.isTravis) {
            ProcessCapture(gem, "install", "licensee", "-v", scannerVersion).requireSuccess()
            getPathFromEnvironment(command())?.parentFile
                ?: throw IOException("Install directory for licensee not found.")
        } else {
            ProcessCapture(gem, "install", "--user-install", "licensee", "-v", scannerVersion).requireSuccess()

            val ruby = ProcessCapture("ruby", "-r", "rubygems", "-e", "puts Gem.user_dir").requireSuccess()
            val userDir = ruby.stdout.trimEnd()

            File(userDir, "bin")
        }
    }

    override fun getConfiguration() = CONFIGURATION_OPTIONS.joinToString(" ")

    override fun scanPath(path: File, resultsFile: File): ScanResult {
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

        if (process.stderr.isNotBlank()) {
            log.debug { process.stderr }
        }

        with(process) {
            if (isSuccess) {
                stdoutFile.copyTo(resultsFile)
                val result = getRawResult(resultsFile)
                val summary = generateSummary(startTime, endTime, path, result)
                return ScanResult(Provenance(), getDetails(), summary, result)
            } else {
                throw ScanException(errorMessage)
            }
        }
    }

    override fun getRawResult(resultsFile: File) =
        if (resultsFile.isFile && resultsFile.length() > 0L) {
            jsonMapper.readTree(resultsFile)
        } else {
            EMPTY_JSON_NODE
        }

    private fun generateSummary(startTime: Instant, endTime: Instant, scanPath: File, result: JsonNode): ScanSummary {
        val matchedFiles = result["matched_files"]
        val licenseFindings = sortedSetOf<LicenseFinding>()

        matchedFiles.mapTo(licenseFindings) {
            val filePath = File(it["filename"].textValue())
            LicenseFinding(
                license = getSpdxLicenseIdString(it["matched_license"].textValue()),
                location = TextLocation(relativizePath(scanPath, filePath), -1, -1)
            )
        }

        return ScanSummary(
            startTime = startTime,
            endTime = endTime,
            fileCount = matchedFiles.count(),
            packageVerificationCode = calculatePackageVerificationCode(scanPath),
            licenseFindings = licenseFindings,
            copyrightFindings = sortedSetOf(),
            errors = mutableListOf()
        )
    }
}
