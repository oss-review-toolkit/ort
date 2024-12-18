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

import kotlinx.serialization.json.Json

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.scanner.LocalPathScannerWrapper
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.scanner.ScanException
import org.ossreviewtoolkit.scanner.ScannerMatcher
import org.ossreviewtoolkit.scanner.ScannerMatcherConfig
import org.ossreviewtoolkit.scanner.ScannerWrapperFactory
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

private val JSON = Json { ignoreUnknownKeys = true }

object BoyterLcCommand : CommandLineTool {
    override fun command(workingDir: File?) =
        listOfNotNull(workingDir, if (Os.isWindows) "lc.exe" else "lc").joinToString(File.separator)

    override fun transformVersion(output: String) =
        // The version string can be something like:
        // licensechecker version 1.1.1
        output.removePrefix("licensechecker version ")
}

data class BoyterLcConfig(
    /**
     * A regular expression to match the scanner name when looking up scan results in the storage.
     */
    val regScannerName: String?,

    /**
     * The minimum version of stored scan results to use.
     */
    val minVersion: String?,

    /**
     * The maximum version of stored scan results to use.
     */
    val maxVersion: String?,

    /**
     * The configuration to use for the scanner. Only scan results with the same configuration are used when looking up
     * scan results in the storage.
     */
    val configuration: String?,

    /**
     * Whether to read scan results from the storage.
     */
    @OrtPluginOption(defaultValue = "true")
    val readFromStorage: Boolean,

    /**
     * Whether to write scan results to the storage.
     */
    @OrtPluginOption(defaultValue = "true")
    val writeToStorage: Boolean
)

@OrtPlugin(
    displayName = "BoyterLc",
    description = "A command line application which scans directories and identifies what software license things " +
        "are under.",
    factory = ScannerWrapperFactory::class
)
class BoyterLc(
    override val descriptor: PluginDescriptor = BoyterLcFactory.descriptor,
    config: BoyterLcConfig
) : LocalPathScannerWrapper() {
    companion object {
        val CONFIGURATION_OPTIONS = listOf(
            "--confidence", "0.95", // Cut-off value to only get most relevant matches.
            "--format", "json"
        )
    }

    override val configuration = CONFIGURATION_OPTIONS.joinToString(" ")

    override val matcher by lazy {
        ScannerMatcher.create(
            details,
            ScannerMatcherConfig(
                config.regScannerName,
                config.minVersion,
                config.maxVersion,
                config.configuration
            )
        )
    }

    override val version by lazy { BoyterLcCommand.getVersion() }

    override val readFromStorage = config.readFromStorage
    override val writeToStorage = config.writeToStorage

    override fun runScanner(path: File, context: ScanContext): String {
        val resultFile = createOrtTempDir().resolve("result.json")
        val process = BoyterLcCommand.run(
            *CONFIGURATION_OPTIONS.toTypedArray(),
            "--output", resultFile.absolutePath,
            path.absolutePath
        )

        return with(process) {
            if (stderr.isNotBlank()) logger.debug { stderr }
            if (isError) throw ScanException(errorMessage)

            resultFile.readText().also { resultFile.parentFile.safeDeleteRecursively() }
        }
    }

    override fun createSummary(result: String, startTime: Instant, endTime: Instant): ScanSummary {
        val results = JSON.decodeFromString<List<BoyterLcResult>>(result)

        val licenseFindings = results.flatMapTo(mutableSetOf()) {
            val filePath = File(it.directory, it.filename)
            it.licenseGuesses.map { guess ->
                LicenseFinding(
                    license = guess.licenseId,
                    location = TextLocation(filePath.invariantSeparatorsPath, TextLocation.UNKNOWN_LINE),
                    score = guess.percentage
                )
            }
        }

        return ScanSummary(
            startTime = startTime,
            endTime = endTime,
            licenseFindings = licenseFindings,
            issues = listOf(
                Issue(
                    source = descriptor.id,
                    message = "This scanner is not capable of detecting copyright statements.",
                    severity = Severity.HINT
                )
            )
        )
    }
}
