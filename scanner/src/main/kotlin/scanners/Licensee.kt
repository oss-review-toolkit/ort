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

package org.ossreviewtoolkit.scanner.scanners

import com.fasterxml.jackson.databind.JsonNode

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.model.EMPTY_JSON_NODE
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.scanner.AbstractScannerFactory
import org.ossreviewtoolkit.scanner.LocalScanner
import org.ossreviewtoolkit.scanner.ScanException
import org.ossreviewtoolkit.spdx.calculatePackageVerificationCode
import org.ossreviewtoolkit.utils.Os
import org.ossreviewtoolkit.utils.ProcessCapture
import org.ossreviewtoolkit.utils.log

class Licensee(name: String, config: ScannerConfiguration) : LocalScanner(name, config) {
    class Factory : AbstractScannerFactory<Licensee>("Licensee") {
        override fun create(config: ScannerConfiguration) = Licensee(scannerName, config)
    }

    companion object {
        val CONFIGURATION_OPTIONS = listOf("--json")
    }

    override val scannerVersion = "9.13.0"
    override val resultFileExt = "json"

    override fun command(workingDir: File?) =
        listOfNotNull(workingDir, if (Os.isWindows) "licensee.bat" else "licensee").joinToString(File.separator)

    override fun getVersionArguments() = "version"

    override fun getConfiguration() = CONFIGURATION_OPTIONS.joinToString(" ")

    override fun scanPathInternal(path: File, resultsFile: File): ScanResult {
        val startTime = Instant.now()

        val process = ProcessCapture(
            scannerPath.absolutePath,
            "detect",
            *CONFIGURATION_OPTIONS.toTypedArray(),
            path.absolutePath
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
                location = TextLocation(
                    // The path is already relative.
                    filePath.path,
                    TextLocation.UNKNOWN_LINE,
                    TextLocation.UNKNOWN_LINE
                )
            )
        }

        return ScanSummary(
            startTime = startTime,
            endTime = endTime,
            fileCount = matchedFiles.count(),
            packageVerificationCode = calculatePackageVerificationCode(scanPath),
            licenseFindings = licenseFindings,
            copyrightFindings = sortedSetOf(),
            issues = mutableListOf()
        )
    }
}
