/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.scanner.scanners.scancode

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.time.Instant

import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.math.max

import okhttp3.Request

import okio.buffer
import okio.sink

import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.readJsonFile
import org.ossreviewtoolkit.scanner.AbstractScannerFactory
import org.ossreviewtoolkit.scanner.LocalScanner
import org.ossreviewtoolkit.scanner.ScanException
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.OkHttpClientHelper
import org.ossreviewtoolkit.utils.Os
import org.ossreviewtoolkit.utils.ProcessCapture
import org.ossreviewtoolkit.utils.isTrue
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.unpack

/**
 * A wrapper for [ScanCode](https://github.com/nexB/scancode-toolkit).
 *
 * This scanner can be configured in [ScannerConfiguration.options] using the key "ScanCode". It offers the following
 * configuration options:
 *
 * * **"commandLine":** Command line options that modify the result. These are added to the [ScannerDetails] when
 *   looking up results from the [ScanResultsStorage]. Defaults to [DEFAULT_CONFIGURATION_OPTIONS].
 * * **"commandLineNonConfig":** Command line options that do not modify the result and should therefore not be
 *   considered in [configuration], like "--processes". Defaults to [DEFAULT_NON_CONFIGURATION_OPTIONS].
 */
class ScanCode(
    name: String,
    scannerConfig: ScannerConfiguration,
    downloaderConfig: DownloaderConfiguration
) : LocalScanner(name, scannerConfig, downloaderConfig) {
    class Factory : AbstractScannerFactory<ScanCode>(SCANNER_NAME) {
        override fun create(scannerConfig: ScannerConfiguration, downloaderConfig: DownloaderConfiguration) =
            ScanCode(scannerName, scannerConfig, downloaderConfig)
    }

    companion object {
        const val SCANNER_NAME = "ScanCode"

        private const val OUTPUT_FORMAT = "json-pp"
        internal const val TIMEOUT = 300

        /**
         * Configuration options that are relevant for [configuration] because they change the result file.
         */
        private val DEFAULT_CONFIGURATION_OPTIONS = listOf(
            "--copyright",
            "--license",
            "--info",
            "--strip-root",
            "--timeout", TIMEOUT.toString()
        )

        /**
         * Configuration options that are not relevant for [configuration] because they do not change the result
         * file.
         */
        private val DEFAULT_NON_CONFIGURATION_OPTIONS = listOf(
            "--processes", max(1, Runtime.getRuntime().availableProcessors() - 1).toString()
        )

        private val OUTPUT_FORMAT_OPTION = if (OUTPUT_FORMAT.startsWith("json")) {
            "--$OUTPUT_FORMAT"
        } else {
            "--output-$OUTPUT_FORMAT"
        }
    }

    override val expectedVersion = "3.2.1-rc2"

    override val configuration by lazy {
        mutableListOf<String>().apply {
            addAll(configurationOptions)
            add(OUTPUT_FORMAT_OPTION)
        }.joinToString(" ")
    }

    override val resultFileExt = "json"

    private val scanCodeConfiguration = scannerConfig.options?.get("ScanCode").orEmpty()

    private val configurationOptions = scanCodeConfiguration["commandLine"]?.split(' ')
        ?: DEFAULT_CONFIGURATION_OPTIONS
    private val nonConfigurationOptions = scanCodeConfiguration["commandLineNonConfig"]?.split(' ')
        ?: DEFAULT_NON_CONFIGURATION_OPTIONS

    val commandLineOptions by lazy {
        mutableListOf<String>().apply {
            addAll(configurationOptions)
            addAll(nonConfigurationOptions)
        }.toList()
    }

    override fun command(workingDir: File?) =
        listOfNotNull(workingDir, if (Os.isWindows) "scancode.bat" else "scancode").joinToString(File.separator)

    override fun transformVersion(output: String): String {
        // "scancode --version" returns a string like "ScanCode version 2.0.1.post1.fb67a181" which might be preceded
        // by a line saying "Configuring ScanCode for first use...".
        val prefix = "ScanCode version "
        return output.lineSequence().first { it.startsWith(prefix) }.substring(prefix.length)
    }

    override fun bootstrap(): File {
        val versionWithoutHyphen = expectedVersion.replace("-", "")

        val archive = when {
            // Use the .zip file despite it being slightly larger than the .tar.gz file here as the latter for some
            // reason does not complete to unpack on Windows.
            Os.isWindows -> "v$versionWithoutHyphen.zip"
            else -> "v$versionWithoutHyphen.tar.gz"
        }

        // Use the source code archive instead of the release artifact from S3 to enable OkHttp to cache the download
        // locally. For details see https://github.com/square/okhttp/issues/4355#issuecomment-435679393.
        val url = "https://github.com/nexB/scancode-toolkit/archive/$archive"

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

            val scannerArchive = createTempFile(ORT_NAME, "$scannerName-${url.substringAfterLast("/")}").toFile()
            scannerArchive.sink().buffer().use { it.writeAll(body.source()) }

            val unpackDir = createTempDirectory("$ORT_NAME-$scannerName-$expectedVersion").toFile().apply {
                deleteOnExit()
            }

            log.info { "Unpacking '$scannerArchive' to '$unpackDir'... " }
            scannerArchive.unpack(unpackDir)
            if (!scannerArchive.delete()) {
                log.warn { "Unable to delete temporary file '$scannerArchive'." }
            }

            val scannerDir = unpackDir.resolve("scancode-toolkit-$versionWithoutHyphen")

            scannerDir
        }
    }

    override fun scanPathInternal(path: File, resultsFile: File): ScanSummary {
        val startTime = Instant.now()

        val process = ProcessCapture(
            scannerPath.absolutePath,
            *commandLineOptions.toTypedArray(),
            path.absolutePath,
            OUTPUT_FORMAT_OPTION,
            resultsFile.absolutePath
        )

        val endTime = Instant.now()

        if (process.stderr.isNotBlank()) {
            log.debug { process.stderr }
        }

        val result = getRawResult(resultsFile)
        val parseLicenseExpressions = scanCodeConfiguration["parseLicenseExpressions"].isTrue()
        val summary = generateSummary(startTime, endTime, path, result, parseLicenseExpressions)

        val issues = summary.issues.toMutableList()

        val hasOnlyMemoryErrors = mapUnknownIssues(issues)
        val hasOnlyTimeoutErrors = mapTimeoutErrors(issues)

        with(process) {
            if (isSuccess || hasOnlyMemoryErrors || hasOnlyTimeoutErrors) {
                return summary.copy(issues = issues)
            } else {
                throw ScanException(errorMessage)
            }
        }
    }

    override fun getVersion(workingDir: File?): String =
        // The release candidate version names lack a hyphen in between the minor version and the extension, e.g.
        // 3.2.1rc2. Insert that hyphen for compatibility with Semver.
        super.getVersion(workingDir).let {
            val index = it.indexOf("rc")
            if (index != -1) {
                "${it.substring(0, index)}-${it.substring(index)}"
            } else {
                it
            }
        }

    override fun getRawResult(resultsFile: File) = readJsonFile(resultsFile)
}
