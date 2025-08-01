/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.fossologynomossa

import java.io.File
import java.time.Instant

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.scanner.LocalPathScannerWrapper
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.scanner.ScannerMatcher
import org.ossreviewtoolkit.scanner.ScannerWrapperFactory
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.ProcessCapture

object NomossaCommand : CommandLineTool {
    override fun command(workingDir: File?): String {
        return listOfNotNull(workingDir, "nomossa").joinToString(File.separator)
    }

    override fun transformVersion(output: String): String {
        // Example output: nomossasa build version: 4.5.1.1 r(ff4fa7)
        val versionRegex = Regex("""(\d+\.\d+\.\d+)(?:\.\d+)?""")
        return versionRegex.find(output)?.groupValues?.get(1).orEmpty() // Returns 4.5.1
    }

    override fun getVersionArguments() = "-V"
}

/**
 * A wrapper for [Nomossa](https://github.com/fossology/fossology).
 *
 * This plugin integrates FOSSology's Nomossa scanner into ORT by calling its CLI
 * and mapping its output to ORT's scan result format.
 */
@OrtPlugin(
    id = "FOSSology-Nomossa",
    displayName = "FOSSology-Nomossa",
    description = "A wrapper for [Nomossa](https://github.com/fossology/fossology).",
    factory = ScannerWrapperFactory::class
)
class Nomossa(
    override val descriptor: PluginDescriptor = NomossaFactory.descriptor,
    private val config: NomossaConfig
) : LocalPathScannerWrapper() {

    private val commandLineOptions by lazy { getCommandLineOptions() }

    internal fun getCommandLineOptions(): List<String> {
        val options = LinkedHashSet(config.additionalOptions)
        options.add("-J")
        options.add("-l")
        options.add("-n")
        options.add(config.cpuCount.toString())
        return options.toList()
    }

    override val configuration by lazy {
        config.additionalOptions.joinToString(" ")
    }

    override val matcher by lazy { ScannerMatcher.create(details, config) }

    override val version by lazy {
        require(NomossaCommand.isInPath()) {
            "The '${NomossaCommand.command()}' command is not available in the PATH environment."
        }

        NomossaCommand.getVersion()
    }

    override val readFromStorage = config.readFromStorage
    override val writeToStorage = config.writeToStorage

    override fun runScanner(path: File, context: ScanContext): String {
        val process = runNomossa(path)

        val resultText = process.stdout
        logger.info { "Nomossa raw output:\n$resultText" }

        return with(process) {
            if (isError && stdout.isNotBlank()) logger.debug { stdout }
            if (stderr.isNotBlank()) logger.debug { stderr }

            stdout
        }
    }

    override fun parseDetails(result: String): ScannerDetails {
        return ScannerDetails(
            name = descriptor.id,
            version = version,
            configuration = config.additionalOptions.joinToString(" ")
        )
    }

    override fun createSummary(result: String, startTime: Instant, endTime: Instant): ScanSummary =
        parseNomossaResult(result).toScanSummary(startTime, endTime)

    /**
     * Execute Nomossa with the configured arguments to scan the given [path].
     */
    internal fun runNomossa(path: File): ProcessCapture =
        ProcessCapture(
            NomossaCommand.command(),
            *commandLineOptions.toTypedArray(),
            "-d", path.absolutePath // Scan this directory
        )
}
