/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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

import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.readJsonFile
import org.ossreviewtoolkit.scanner.AbstractScannerFactory
import org.ossreviewtoolkit.scanner.LocalScanner
import org.ossreviewtoolkit.scanner.ScanException
import org.ossreviewtoolkit.scanner.experimental.LocalScannerWrapper
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.core.ProcessCapture
import org.ossreviewtoolkit.utils.core.createOrtTempDir
import org.ossreviewtoolkit.utils.core.log
import org.ossreviewtoolkit.utils.spdx.calculatePackageVerificationCode

class Licensee(
    name: String,
    scannerConfig: ScannerConfiguration,
    downloaderConfig: DownloaderConfiguration
) : LocalScanner(name, scannerConfig, downloaderConfig), LocalScannerWrapper {
    class Factory : AbstractScannerFactory<Licensee>("Licensee") {
        override fun create(scannerConfig: ScannerConfiguration, downloaderConfig: DownloaderConfiguration) =
            Licensee(scannerName, scannerConfig, downloaderConfig)
    }

    companion object {
        val CONFIGURATION_OPTIONS = listOf("--json")
    }

    override val name = "Licensee"
    override val criteria by lazy { getScannerCriteria() }
    override val expectedVersion = "9.13.0"
    override val configuration = CONFIGURATION_OPTIONS.joinToString(" ")
    override val resultFileExt = "json"

    override fun command(workingDir: File?) =
        listOfNotNull(workingDir, if (Os.isWindows) "licensee.bat" else "licensee").joinToString(File.separator)

    override fun getVersionArguments() = "version"

    override fun bootstrap(): File {
        val gem = if (Os.isWindows) "gem.cmd" else "gem"

        if (Os.isWindows) {
            // Version 0.28.0 of rugged broke building on Windows and the fix is unreleased yet, see
            // https://github.com/libgit2/rugged/commit/2f5a8f6c8f4ae9b94a2d1f6ffabc315f2592868d. So install the latest
            // version < 0.28.0 (and => 0.24.0) manually to satisfy Licensee's needs.
            ProcessCapture(gem, "install", "rugged", "-v", "0.27.10.1").requireSuccess()
        }

        ProcessCapture(gem, "install", "--user-install", "licensee", "-v", expectedVersion).requireSuccess()

        val ruby = ProcessCapture("ruby", "-r", "rubygems", "-e", "puts Gem.user_dir").requireSuccess()
        val userDir = ruby.stdout.trimEnd()

        return File(userDir, "bin")
    }

    override fun scanPathInternal(path: File, resultsFile: File): ScanSummary {
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
                return generateSummary(startTime, endTime, path, result)
            } else {
                throw ScanException(errorMessage)
            }
        }
    }

    override fun getRawResult(resultsFile: File) = readJsonFile(resultsFile)

    private fun generateSummary(startTime: Instant, endTime: Instant, scanPath: File, result: JsonNode): ScanSummary {
        val matchedFiles = result["matched_files"]
        val licenseFindings = sortedSetOf<LicenseFinding>()

        matchedFiles.mapTo(licenseFindings) {
            val filePath = File(it["filename"].textValue())
            LicenseFinding(
                license = it["matched_license"].textValue(),
                location = TextLocation(
                    // The path is already relative.
                    filePath.path,
                    TextLocation.UNKNOWN_LINE
                )
            )
        }

        return ScanSummary(
            startTime = startTime,
            endTime = endTime,
            packageVerificationCode = calculatePackageVerificationCode(scanPath),
            licenseFindings = licenseFindings,
            copyrightFindings = sortedSetOf(),
            issues = mutableListOf()
        )
    }

    override fun scanPath(path: File): ScanSummary =
        scanPathInternal(path, createOrtTempDir(name).resolve("result.$resultFileExt"))
}
