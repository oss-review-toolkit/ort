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

package org.ossreviewtoolkit.plugins.scanners.scancode

import java.io.File
import java.time.Instant

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.config.PluginConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.scanner.CommandLinePathScannerWrapper
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.scanner.ScanStorage
import org.ossreviewtoolkit.scanner.ScannerMatcher
import org.ossreviewtoolkit.scanner.ScannerWrapperConfig
import org.ossreviewtoolkit.scanner.ScannerWrapperFactory
import org.ossreviewtoolkit.utils.common.Options
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

import org.semver4j.RangesList
import org.semver4j.RangesListFactory
import org.semver4j.Semver

/**
 * A wrapper for [ScanCode](https://github.com/aboutcode-org/scancode-toolkit).
 *
 * This scanner can be configured in [ScannerConfiguration.config] using the key "ScanCode". It offers the following
 * configuration [options][PluginConfiguration.options]:
 *
 * * **"commandLine":** Command line options that modify the result. These are added to the [ScannerDetails] when
 *   looking up results from a [ScanStorage]. Defaults to [ScanCodeConfig.DEFAULT_COMMAND_LINE_OPTIONS].
 * * **"commandLineNonConfig":** Command line options that do not modify the result and should therefore not be
 *   considered in [configuration], like "--processes". Defaults to
 *   [ScanCodeConfig.DEFAULT_COMMAND_LINE_NON_CONFIG_OPTIONS].
 * * **preferFileLicense**: A flag to indicate whether the "high-level" per-file license reported by ScanCode starting
 *   with version 32 should be used instead of the individual "low-level" per-line license findings. The per-file
 *   license may be different from the conjunction of per-line licenses and is supposed to contain fewer
 *   false-positives. However, no exact line numbers can be associated to the per-file license anymore. If enabled, the
 *   start line of the per-file license finding is set to the minimum of all start lines for per-line findings in that
 *   file, the end line is set to the maximum of all end lines for per-line findings in that file, and the score is set
 *   to the arithmetic average of the scores of all per-line findings in that file.
 */
class ScanCode internal constructor(
    name: String,
    private val config: ScanCodeConfig,
    private val wrapperConfig: ScannerWrapperConfig
) : CommandLinePathScannerWrapper(name) {
    // This constructor is required by the `RequirementsCommand`.
    constructor(name: String, wrapperConfig: ScannerWrapperConfig) : this(name, ScanCodeConfig.DEFAULT, wrapperConfig)

    companion object {
        const val SCANNER_NAME = "ScanCode"

        private const val LICENSE_REFERENCES_OPTION_VERSION = "32.0.0"
        private const val OUTPUT_FORMAT_OPTION = "--json-pp"
    }

    class Factory : ScannerWrapperFactory<ScanCodeConfig>(SCANNER_NAME) {
        override fun create(config: ScanCodeConfig, wrapperConfig: ScannerWrapperConfig) =
            ScanCode(type, config, wrapperConfig)

        override fun parseConfig(options: Options, secrets: Options) = ScanCodeConfig.create(options)
    }

    private val commandLineOptions by lazy { getCommandLineOptions(version) }

    internal fun getCommandLineOptions(version: String) =
        buildList {
            addAll(config.commandLine)
            addAll(config.commandLineNonConfig)

            if (Semver(version).isGreaterThanOrEqualTo(LICENSE_REFERENCES_OPTION_VERSION)) {
                // Required to be able to map ScanCode license keys to SPDX IDs.
                add("--license-references")
            }
        }

    override val configuration by lazy {
        buildList {
            addAll(config.commandLine)
            add(OUTPUT_FORMAT_OPTION)

            // Add this in the style of a fake command line option for consistency with the above.
            if (config.preferFileLicense) add("--prefer-file-license")
        }.joinToString(" ")
    }

    override val matcher by lazy { ScannerMatcher.create(details, wrapperConfig.matcherConfig) }

    override val readFromStorage by lazy { wrapperConfig.readFromStorageWithDefault(matcher) }

    override val writeToStorage by lazy { wrapperConfig.writeToStorageWithDefault(matcher) }

    override fun command(workingDir: File?) =
        listOfNotNull(workingDir, if (Os.isWindows) "scancode.bat" else "scancode").joinToString(File.separator)

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

    override fun getVersionRequirement(): RangesList = RangesListFactory.create(">=3.0.0")

    override fun transformVersion(output: String): String {
        // On first use, the output is prefixed by "Configuring ScanCode for first use...". The version string can be
        // something like:
        // ScanCode version 2.0.1.post1.fb67a181
        // ScanCode version: 31.0.0b4
        return output.lineSequence().firstNotNullOfOrNull { line ->
            line.withoutPrefix("ScanCode version")?.removePrefix(":")?.trim()
        }.orEmpty()
    }

    override fun runScanner(path: File, context: ScanContext): String {
        val resultFile = createOrtTempDir().resolve("result.json")
        val process = runScanCode(path, resultFile)

        return with(process) {
            // Do not throw yet if the process exited with an error as some errors might turn out to be tolerable during
            // parsing.
            if (isError && stdout.isNotBlank()) logger.debug { stdout }
            if (stderr.isNotBlank()) logger.debug { stderr }

            resultFile.readText().also { resultFile.parentFile.safeDeleteRecursively() }
        }
    }

    override fun parseDetails(result: String): ScannerDetails {
        val details = parseResult(result)
        val header = details.headers.single()

        val options = header.getPrimitiveOptions()

        return ScannerDetails(
            name = name,
            version = header.toolVersion,
            // TODO: Filter out options that have no influence on scan results.
            configuration = options.joinToString(" ") { "${it.first} ${it.second}" }
        )
    }

    override fun createSummary(result: String, startTime: Instant, endTime: Instant): ScanSummary =
        parseResult(result).toScanSummary(config.preferFileLicense)

    /**
     * Execute ScanCode with the configured arguments to scan the given [path] and produce [resultFile].
     */
    internal fun runScanCode(path: File, resultFile: File) =
        ProcessCapture(
            command(),
            *commandLineOptions.toTypedArray(),
            // The output format option needs to directly precede the result file path.
            OUTPUT_FORMAT_OPTION, resultFile.absolutePath,
            path.absolutePath
        )
}
