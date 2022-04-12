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

package org.ossreviewtoolkit.scanner.scanners.scancode

import java.io.File
import java.time.Instant

import kotlin.math.max

import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.readJsonFile
import org.ossreviewtoolkit.scanner.AbstractScannerFactory
import org.ossreviewtoolkit.scanner.BuildConfig
import org.ossreviewtoolkit.scanner.CommandLineScanner
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.scanner.experimental.AbstractScannerWrapperFactory
import org.ossreviewtoolkit.scanner.experimental.PathScannerWrapper
import org.ossreviewtoolkit.scanner.experimental.ScanContext
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.common.isTrue
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.common.unpack
import org.ossreviewtoolkit.utils.core.OkHttpClientHelper
import org.ossreviewtoolkit.utils.core.createOrtTempDir
import org.ossreviewtoolkit.utils.core.log
import org.ossreviewtoolkit.utils.core.ortToolsDirectory

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
 * * **"parseLicenseExpressions":** By default the license `key`, which can contain a single license id, is used for the
 *   detected licenses. If this option is set to "true", the detected `license_expression` is used instead, which can
 *   contain an SPDX expression.
 */
class ScanCode internal constructor(
    name: String,
    scannerConfig: ScannerConfiguration,
    downloaderConfig: DownloaderConfiguration
) : CommandLineScanner(name, scannerConfig, downloaderConfig), PathScannerWrapper {
    class ScanCodeFactory : AbstractScannerWrapperFactory<ScanCode>(SCANNER_NAME) {
        override fun create(scannerConfig: ScannerConfiguration, downloaderConfig: DownloaderConfiguration) =
            ScanCode(scannerName, scannerConfig, downloaderConfig)
    }

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

    override val name = SCANNER_NAME
    override val criteria by lazy { getScannerCriteria() }
    override val expectedVersion = BuildConfig.SCANCODE_VERSION

    override val configuration by lazy {
        buildList {
            addAll(configurationOptions)
            add(OUTPUT_FORMAT_OPTION)
        }.joinToString(" ")
    }

    private val scanCodeConfiguration = scannerConfig.options?.get("ScanCode").orEmpty()

    private val configurationOptions = scanCodeConfiguration["commandLine"]?.split(' ')
        ?: DEFAULT_CONFIGURATION_OPTIONS
    private val nonConfigurationOptions = scanCodeConfiguration["commandLineNonConfig"]?.split(' ')
        ?: DEFAULT_NON_CONFIGURATION_OPTIONS

    val commandLineOptions by lazy {
        buildList {
            addAll(configurationOptions)
            addAll(nonConfigurationOptions)
        }
    }

    override fun command(workingDir: File?) =
        listOfNotNull(workingDir, if (Os.isWindows) "scancode.bat" else "scancode").joinToString(File.separator)

    override fun transformVersion(output: String): String {
        // On first use, the output is prefixed by "Configuring ScanCode for first use...". The version string can be
        // something like:
        // ScanCode version 2.0.1.post1.fb67a181
        val prefix = "ScanCode version "
        return output.lineSequence().first { it.startsWith(prefix) }.substring(prefix.length)
    }

    override fun bootstrap(): File {
        val versionWithoutHyphen = expectedVersion.replace("-", "")
        val unpackDir = ortToolsDirectory.resolve(name).resolve(expectedVersion)
        val scannerDir = unpackDir.resolve("scancode-toolkit-$versionWithoutHyphen")

        if (scannerDir.resolve(command()).isFile) {
            log.info { "Skipping to bootstrap $name as it was found in $unpackDir." }
            return scannerDir
        }

        val archive = when {
            // Use the .zip file despite it being slightly larger than the .tar.gz file here as the latter for some
            // reason does not complete to unpack on Windows.
            Os.isWindows -> "v$versionWithoutHyphen.zip"
            else -> "v$versionWithoutHyphen.tar.gz"
        }

        // Use the source code archive instead of the release artifact from S3 to enable OkHttp to cache the download
        // locally. For details see https://github.com/square/okhttp/issues/4355#issuecomment-435679393.
        val url = "https://github.com/nexB/scancode-toolkit/archive/$archive"

        // Download ScanCode to a file instead of unpacking directly from the response body as doing so on the > 200 MiB
        // archive causes issues.
        log.info { "Downloading $scannerName from $url... " }
        unpackDir.safeMkdirs()
        val scannerArchive = OkHttpClientHelper.downloadFile(url, unpackDir).getOrThrow()

        log.info { "Unpacking '$scannerArchive' to '$unpackDir'... " }
        scannerArchive.unpack(unpackDir)

        if (!scannerArchive.delete()) {
            log.warn { "Unable to delete temporary file '$scannerArchive'." }
        }

        return scannerDir
    }

    override fun scanPathInternal(path: File): ScanSummary {
        val startTime = Instant.now()

        val resultFile = createOrtTempDir().resolve("result.json")
        val process = runScanCode(path, resultFile)

        val endTime = Instant.now()

        val result = readJsonFile(resultFile)
        resultFile.parentFile.safeDeleteRecursively(force = true)

        val parseLicenseExpressions = scanCodeConfiguration["parseLicenseExpressions"].isTrue()
        val summary = generateSummary(startTime, endTime, path, result, parseLicenseExpressions)

        val issues = summary.issues.toMutableList()

        mapUnknownIssues(issues)
        mapTimeoutErrors(issues)

        return with(process) {
            if (stderr.isNotBlank()) log.debug { stderr }

            summary.copy(issues = issues)
        }
    }

    /**
     * Execute ScanCode with the configured arguments to scan the given [path] and produce [resultFile].
     */
    internal fun runScanCode(
        path: File,
        resultFile: File
    ) = ProcessCapture(
        scannerPath.absolutePath,
        *commandLineOptions.toTypedArray(),
        path.absolutePath,
        OUTPUT_FORMAT_OPTION,
        resultFile.absolutePath
    )

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

    override fun scanPath(path: File, context: ScanContext) = scanPathInternal(path)
}
