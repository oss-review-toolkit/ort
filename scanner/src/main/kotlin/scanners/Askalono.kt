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
import java.io.IOException
import java.net.HttpURLConnection
import java.time.Instant

import kotlin.io.path.createTempDirectory

import okhttp3.Request

import org.ossreviewtoolkit.model.EMPTY_JSON_NODE
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.scanner.AbstractScannerFactory
import org.ossreviewtoolkit.scanner.LocalScanner
import org.ossreviewtoolkit.scanner.ScanException
import org.ossreviewtoolkit.scanner.experimental.LocalScannerWrapper
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.core.ORT_NAME
import org.ossreviewtoolkit.utils.core.OkHttpClientHelper
import org.ossreviewtoolkit.utils.core.ProcessCapture
import org.ossreviewtoolkit.utils.core.createOrtTempDir
import org.ossreviewtoolkit.utils.core.log
import org.ossreviewtoolkit.utils.core.unpackZip
import org.ossreviewtoolkit.utils.spdx.calculatePackageVerificationCode

class Askalono(
    name: String,
    scannerConfig: ScannerConfiguration,
    downloaderConfig: DownloaderConfiguration
) : LocalScanner(name, scannerConfig, downloaderConfig), LocalScannerWrapper {
    class Factory : AbstractScannerFactory<Askalono>("Askalono") {
        override fun create(scannerConfig: ScannerConfiguration, downloaderConfig: DownloaderConfiguration) =
            Askalono(scannerName, scannerConfig, downloaderConfig)
    }

    override val name = "Askalono"
    override val criteria by lazy { getScannerCriteria() }
    override val expectedVersion = "0.4.3"
    override val configuration = ""
    override val resultFileExt = "txt"

    override fun command(workingDir: File?) =
        listOfNotNull(workingDir, if (Os.isWindows) "askalono.exe" else "askalono").joinToString(File.separator)

    override fun transformVersion(output: String) =
        // "askalono --version" returns a string like "askalono 0.2.0-beta.1", so simply remove the prefix.
        output.removePrefix("askalono ")

    override fun bootstrap(): File {
        val platform = when {
            Os.isLinux -> "Linux"
            Os.isMac -> "macOS"
            Os.isWindows -> "Windows"
            else -> throw IllegalArgumentException("Unsupported operating system.")
        }

        val archive = "askalono-$platform.zip"
        val url = "https://github.com/amzn/askalono/releases/download/$expectedVersion/$archive"

        log.info { "Downloading $scannerName from $url... " }

        val request = Request.Builder().get().url(url).build()

        return OkHttpClientHelper.execute(request).use { response ->
            val body = response.body

            if (response.code != HttpURLConnection.HTTP_OK || body == null) {
                throw IOException("Failed to download $scannerName from $url.")
            }

            if (response.cacheResponse != null) {
                log.info { "Retrieved $scannerName from local cache." }
            }

            val unpackDir = createTempDirectory("$ORT_NAME-$scannerName-$expectedVersion").toFile().apply {
                deleteOnExit()
            }

            log.info { "Unpacking '$archive' to '$unpackDir'... " }
            body.bytes().unpackZip(unpackDir)

            unpackDir
        }
    }

    override fun scanPathInternal(path: File, resultsFile: File): ScanSummary {
        val startTime = Instant.now()

        val process = ProcessCapture(
            scannerPath.absolutePath,
            "crawl", path.absolutePath
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

    override fun getRawResult(resultsFile: File): JsonNode {
        if (!resultsFile.isFile || resultsFile.length() == 0L) return EMPTY_JSON_NODE

        val yamlNodes = resultsFile.readLines().chunked(3) { (path, license, score) ->
            val licenseNoOriginalText = license.substringBeforeLast(" (original text)")
            val yamlString = listOf("Path: $path", licenseNoOriginalText, score).joinToString("\n")
            yamlMapper.readTree(yamlString)
        }

        return yamlMapper.createArrayNode().apply { addAll(yamlNodes) }
    }

    private fun generateSummary(startTime: Instant, endTime: Instant, scanPath: File, result: JsonNode): ScanSummary {
        val licenseFindings = sortedSetOf<LicenseFinding>()

        result.mapTo(licenseFindings) {
            val filePath = File(it["Path"].textValue())
            LicenseFinding(
                license = it["License"].textValue(),
                location = TextLocation(
                    // Turn absolute paths in the native result into relative paths to not expose any information.
                    relativizePath(scanPath, filePath),
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
