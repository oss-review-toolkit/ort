/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.scanner.ScanException
import org.ossreviewtoolkit.scanner.ScannerWrapperFactory
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

import org.semver4j.range.RangeList
import org.semver4j.range.RangeListFactory

private const val OUTPUT_FORMAT_OPTION = "--json"

internal object ProvenantCommand : CommandLineTool {
    override fun command(workingDir: File?) = "provenant"

    override fun getVersionRequirement(): RangeList = RangeListFactory.create(">=0.0.14")

    override fun transformVersion(output: String): String =
        output.lineSequence().first().withoutPrefix("provenant-cli ").orEmpty()
}

/**
 * A wrapper for [Provenant](https://github.com/mstykow/provenant).
 */
@OrtPlugin(
    displayName = "Provenant",
    description = "A wrapper for [Provenant](https://github.com/mstykow/provenant).",
    factory = ScannerWrapperFactory::class
)
class Provenant(
    override val descriptor: PluginDescriptor = ProvenantFactory.descriptor,
    config: ScanCodeConfig
) : ScanCode(descriptor, config) {
    override val version by lazy {
        require(ProvenantCommand.isInPath()) {
            "The '${ProvenantCommand.command()}' command is not available in the PATH environment."
        }

        ProvenantCommand.getVersion()
    }

    override fun runScanner(path: File, context: ScanContext): String {
        val resultFile = createOrtTempDir() / "result.json"
        val process = runProvenant(path, resultFile)

        return with(process) {
            if (stderr.isNotBlank()) logger.debug { stderr }

            // Do not throw yet if the process exited with an error as some errors might turn out to be tolerable during
            // parsing.
            if (isError && stdout.isNotBlank()) logger.debug { stdout }

            if (!resultFile.isFile) throw ScanException(errorMessage)

            resultFile.readText().also { resultFile.parentFile.safeDeleteRecursively() }
        }
    }

    /**
     * Execute Provenant with the configured arguments to scan the given [path] and produce [resultFile].
     */
    internal fun runProvenant(path: File, resultFile: File) =
        ProcessCapture(
            ProvenantCommand.command(),
            *commandLineOptions.toTypedArray(),
            // The output format option needs to directly precede the result file path.
            OUTPUT_FORMAT_OPTION, resultFile.absolutePath,
            path.absolutePath
        )
}
