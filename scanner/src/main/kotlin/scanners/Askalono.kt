/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2021-2022 Bosch.IO GmbH
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

import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.scanner.AbstractScannerFactory
import org.ossreviewtoolkit.scanner.BuildConfig
import org.ossreviewtoolkit.scanner.CommandLineScanner
import org.ossreviewtoolkit.scanner.ScanException
import org.ossreviewtoolkit.scanner.experimental.AbstractScannerWrapperFactory
import org.ossreviewtoolkit.scanner.experimental.PathScannerWrapper
import org.ossreviewtoolkit.scanner.experimental.ScanContext
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.common.unpackZip
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper
import org.ossreviewtoolkit.utils.ort.log
import org.ossreviewtoolkit.utils.ort.ortToolsDirectory
import org.ossreviewtoolkit.utils.spdx.calculatePackageVerificationCode

class Askalono internal constructor(
    name: String,
    scannerConfig: ScannerConfiguration,
    downloaderConfig: DownloaderConfiguration
) : CommandLineScanner(name, scannerConfig, downloaderConfig), PathScannerWrapper {
    class AskalonoFactory : AbstractScannerWrapperFactory<Askalono>("Askalono") {
        override fun create(scannerConfig: ScannerConfiguration, downloaderConfig: DownloaderConfiguration) =
            Askalono(scannerName, scannerConfig, downloaderConfig)
    }

    class Factory : AbstractScannerFactory<Askalono>("Askalono") {
        override fun create(scannerConfig: ScannerConfiguration, downloaderConfig: DownloaderConfiguration) =
            Askalono(scannerName, scannerConfig, downloaderConfig)
    }

    override val name = "Askalono"
    override val criteria by lazy { getScannerCriteria() }
    override val expectedVersion = BuildConfig.ASKALONO_VERSION
    override val configuration = ""

    override fun command(workingDir: File?) =
        listOfNotNull(workingDir, if (Os.isWindows) "askalono.exe" else "askalono").joinToString(File.separator)

    override fun transformVersion(output: String) =
        // The version string can be something like:
        // askalono 0.2.0-beta.1
        output.removePrefix("askalono ")

    override fun bootstrap(): File {
        val unpackDir = ortToolsDirectory.resolve(name).resolve(expectedVersion)

        if (unpackDir.resolve(command()).isFile) {
            log.info { "Skipping to bootstrap $name as it was found in $unpackDir." }
            return unpackDir
        }

        val platform = when {
            Os.isLinux -> "Linux"
            Os.isMac -> "macOS"
            Os.isWindows -> "Windows"
            else -> throw IllegalArgumentException("Unsupported operating system.")
        }

        val archive = "askalono-$platform.zip"
        val url = "https://github.com/amzn/askalono/releases/download/$expectedVersion/$archive"

        log.info { "Downloading $scannerName from $url... " }
        val (_, body) = OkHttpClientHelper.download(url).getOrThrow()

        log.info { "Unpacking '$archive' to '$unpackDir'... " }
        body.bytes().unpackZip(unpackDir)

        return unpackDir
    }

    override fun scanPathInternal(path: File): ScanSummary {
        val startTime = Instant.now()

        val process = ProcessCapture(
            scannerPath.absolutePath,
            "--format", "json",
            "crawl", path.absolutePath
        )

        val endTime = Instant.now()

        return with(process) {
            if (stderr.isNotBlank()) log.debug { stderr }
            if (isError) throw ScanException(errorMessage)

            generateSummary(startTime, endTime, path, stdout)
        }
    }

    private fun generateSummary(startTime: Instant, endTime: Instant, scanPath: File, result: String): ScanSummary {
        val licenseFindings = sortedSetOf<LicenseFinding>()

        result.lines().forEach { line ->
            val root = jsonMapper.readTree(line)
            root["result"]?.let { result ->
                val licenseFinding = LicenseFinding.createAndMap(
                    license = result["license"]["name"].textValue(),
                    location = TextLocation(
                        // Turn absolute paths in the native result into relative paths to not expose any information.
                        relativizePath(scanPath, File(root["path"].textValue())),
                        TextLocation.UNKNOWN_LINE
                    ),
                    score = result["score"].floatValue(),
                    detectedLicenseMapping = scannerConfig.detectedLicenseMapping
                )

                licenseFindings += licenseFinding
            }
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

    override fun scanPath(path: File, context: ScanContext) = scanPathInternal(path)
}
