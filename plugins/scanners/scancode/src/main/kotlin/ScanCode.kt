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
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.scanner.LocalPathScannerWrapper
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.scanner.ScanException
import org.ossreviewtoolkit.scanner.ScannerMatcher
import org.ossreviewtoolkit.scanner.ScannerWrapperFactory
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

import org.semver4j.Semver
import org.semver4j.range.RangeList
import org.semver4j.range.RangeListFactory

object ScanCodeCommand : CommandLineTool {
    override fun command(workingDir: File?): String {
        val executable = if (Os.isWindows) {
            // Installing ScanCode as a developer from the distribution archive provides a "scancode.bat", while
            // installing as a user via pip provides a "scancode.exe".
            Os.getPathFromEnvironment("scancode.bat")?.name ?: "scancode.exe"
        } else {
            "scancode"
        }

        return listOfNotNull(workingDir, executable).joinToString(File.separator)
    }

    override fun getVersionRequirement(): RangeList = RangeListFactory.create(">=30.0.0")

    override fun transformVersion(output: String): String =
        output.lineSequence().firstNotNullOfOrNull { line ->
            line.withoutPrefix("ScanCode version")?.removePrefix(":")?.trim()
        }.orEmpty()
}

/**
 * A wrapper for [ScanCode](https://github.com/aboutcode-org/scancode-toolkit).
 */
@OrtPlugin(
    displayName = "ScanCode",
    description = "A wrapper for [ScanCode](https://github.com/aboutcode-org/scancode-toolkit).",
    factory = ScannerWrapperFactory::class
)
class ScanCode(
    override val descriptor: PluginDescriptor = ScanCodeFactory.descriptor,
    private val config: ScanCodeConfig
) : LocalPathScannerWrapper() {
    companion object {
        private const val LICENSE_REFERENCES_OPTION_VERSION = "32.0.0"
        private const val OUTPUT_FORMAT_OPTION = "--json"
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

    override val matcher by lazy { ScannerMatcher.create(details, config) }

    override val version by lazy {
        require(ScanCodeCommand.isInPath()) {
            "The '${ScanCodeCommand.command()}' command is not available in the PATH environment."
        }

        ScanCodeCommand.getVersion()
    }

    override val readFromStorage = config.readFromStorage
    override val writeToStorage = config.writeToStorage

    override fun runScanner(path: File, context: ScanContext): String {
        val resultFile = createOrtTempDir() / "result.json"
        val process = runScanCode(path, resultFile)

        return with(process) {
            if (stderr.isNotBlank()) logger.debug { stderr }

            // Do not throw yet if the process exited with an error as some errors might turn out to be tolerable during
            // parsing.
            if (isError && stdout.isNotBlank()) logger.debug { stdout }

            if (!resultFile.isFile) throw ScanException(errorMessage)

            resultFile.readText().also { resultFile.parentFile.safeDeleteRecursively() }
        }
    }

    override fun parseDetails(result: String): ScannerDetails {
        val details = parseResult(result)
        val header = details.headers.single()

        val options = header.getPrimitiveOptions()

        return ScannerDetails(
            name = descriptor.id,
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
            ScanCodeCommand.command(),
            *commandLineOptions.toTypedArray(),
            // The output format option needs to directly precede the result file path.
            OUTPUT_FORMAT_OPTION, resultFile.absolutePath,
            path.absolutePath
        )
}
