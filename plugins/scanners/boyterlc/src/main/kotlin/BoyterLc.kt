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

package org.ossreviewtoolkit.plugins.scanners.boyterlc

import java.io.File
import java.time.Instant

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.scanner.AbstractScannerWrapperFactory
import org.ossreviewtoolkit.scanner.CommandLinePathScannerWrapper
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.scanner.ScanException
import org.ossreviewtoolkit.scanner.ScannerCriteria
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

class BoyterLc internal constructor(
    name: String,
    private val scannerConfig: ScannerConfiguration
) : CommandLinePathScannerWrapper(name) {
    companion object : Logging {
        val CONFIGURATION_OPTIONS = listOf(
            "--confidence", "0.95", // Cut-off value to only get most relevant matches.
            "--format", "json"
        )
    }

    class Factory : AbstractScannerWrapperFactory<BoyterLc>("BoyterLc") {
        override fun create(scannerConfig: ScannerConfiguration, downloaderConfig: DownloaderConfiguration) =
            BoyterLc(type, scannerConfig)
    }

    override val configuration = CONFIGURATION_OPTIONS.joinToString(" ")

    override val criteria by lazy { ScannerCriteria.fromConfig(details, scannerConfig) }

    override fun command(workingDir: File?) =
        listOfNotNull(workingDir, if (Os.isWindows) "lc.exe" else "lc").joinToString(File.separator)

    override fun transformVersion(output: String) =
        // The version string can be something like:
        // licensechecker version 1.1.1
        output.removePrefix("licensechecker version ")

    override fun runScanner(path: File, context: ScanContext): String {
        val resultFile = createOrtTempDir().resolve("result.json")
        val process = run(
            *CONFIGURATION_OPTIONS.toTypedArray(),
            "--output", resultFile.absolutePath,
            path.absolutePath
        )

        return with(process) {
            if (stderr.isNotBlank()) logger.debug { stderr }
            if (isError) throw ScanException(errorMessage)

            resultFile.readText().also { resultFile.parentFile.safeDeleteRecursively(force = true) }
        }
    }

    override fun createSummary(result: String, startTime: Instant, endTime: Instant): ScanSummary {
        val licenseFindings = mutableSetOf<LicenseFinding>()
        val json = jsonMapper.readTree(result)

        json.flatMapTo(licenseFindings) { file ->
            val filePath = File(file["Directory"].textValue(), file["Filename"].textValue())
            file["LicenseGuesses"].map {
                LicenseFinding(
                    license = it["LicenseId"].textValue(),
                    location = TextLocation(filePath.invariantSeparatorsPath, TextLocation.UNKNOWN_LINE),
                    score = it["Percentage"].floatValue()
                )
            }
        }

        return ScanSummary(
            startTime = startTime,
            endTime = endTime,
            licenseFindings = licenseFindings,
            issues = listOf(
                Issue(
                    source = name,
                    message = "This scanner is not capable of detecting copyright statements.",
                    severity = Severity.HINT
                )
            )
        )
    }
}
