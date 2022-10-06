/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import java.io.File
import java.time.Instant

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.Options
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.scanner.BuildConfig
import org.ossreviewtoolkit.scanner.CommandLinePathScannerWrapper
import org.ossreviewtoolkit.scanner.ScanException
import org.ossreviewtoolkit.scanner.ScannerCriteria
import org.ossreviewtoolkit.scanner.experimental.AbstractScannerWrapperFactory
import org.ossreviewtoolkit.scanner.experimental.ScanContext
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.spdx.calculatePackageVerificationCode

class Licensee internal constructor(
    name: String,
    private val scannerConfig: ScannerConfiguration
) : CommandLinePathScannerWrapper(name) {
    companion object : Logging {
        val CONFIGURATION_OPTIONS = listOf("--json")
    }

    class Factory : AbstractScannerWrapperFactory<Licensee>("Licensee") {
        override fun create(scannerConfig: ScannerConfiguration, downloaderConfig: DownloaderConfiguration) =
            Licensee(scannerName, scannerConfig)
    }

    override val name = "Licensee"
    override val criteria by lazy { ScannerCriteria.fromConfig(details, scannerConfig) }
    override val expectedVersion = BuildConfig.LICENSEE_VERSION
    override val configuration = CONFIGURATION_OPTIONS.joinToString(" ")

    override fun command(workingDir: File?) =
        listOfNotNull(workingDir, if (Os.isWindows) "licensee.bat" else "licensee").joinToString(File.separator)

    override fun getVersionArguments() = "version"

    override fun bootstrap(): File {
        val gem = if (Os.isWindows) "gem.cmd" else "gem"

        ProcessCapture(gem, "install", "--user-install", "licensee", "-v", expectedVersion).requireSuccess()

        val ruby = ProcessCapture("ruby", "-r", "rubygems", "-e", "puts Gem.user_dir").requireSuccess()
        val userDir = ruby.stdout.trimEnd()

        return File(userDir, "bin")
    }

    override fun filterSecretOptions(options: Options): Options = options

    override fun scanPath(path: File, context: ScanContext): ScanSummary {
        val startTime = Instant.now()

        val process = ProcessCapture(
            scannerPath.absolutePath,
            "detect",
            *CONFIGURATION_OPTIONS.toTypedArray(),
            path.absolutePath
        )

        val endTime = Instant.now()

        return with(process) {
            if (stderr.isNotBlank()) logger.debug { stderr }
            if (isError) throw ScanException(errorMessage)

            generateSummary(startTime, endTime, path, stdout)
        }
    }

    private fun generateSummary(startTime: Instant, endTime: Instant, scanPath: File, result: String): ScanSummary {
        val licenseFindings = sortedSetOf<LicenseFinding>()

        val json = jsonMapper.readTree(result)
        val matchedFiles = json["matched_files"]

        matchedFiles.mapTo(licenseFindings) {
            val filePath = File(it["filename"].textValue())
            LicenseFinding.createAndMap(
                license = it["matched_license"].textValue(),
                location = TextLocation(
                    // The path is already relative.
                    filePath.path,
                    TextLocation.UNKNOWN_LINE
                ),
                score = it["matcher"]["confidence"].floatValue(),
                detectedLicenseMapping = scannerConfig.detectedLicenseMapping
            )
        }

        return ScanSummary(
            startTime = startTime,
            endTime = endTime,
            packageVerificationCode = calculatePackageVerificationCode(scanPath),
            licenseFindings = licenseFindings,
            copyrightFindings = sortedSetOf(),
            issues = listOf(
                OrtIssue(
                    source = name,
                    message = "This scanner is not capable of detecting copyright statements.",
                    severity = Severity.HINT
                )
            )
        )
    }
}
