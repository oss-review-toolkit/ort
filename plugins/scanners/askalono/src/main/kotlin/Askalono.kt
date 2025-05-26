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

package org.ossreviewtoolkit.plugins.scanners.askalono

import java.io.File
import java.time.Instant

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeToSequence

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.scanner.LocalPathScannerWrapper
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.scanner.ScanException
import org.ossreviewtoolkit.scanner.ScannerMatcher
import org.ossreviewtoolkit.scanner.ScannerWrapperFactory
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os

private const val CONFIDENCE_NOTICE = "Confidence threshold not high enough for any known license"

object AskalonoCommand : CommandLineTool {
    override fun command(workingDir: File?) =
        listOfNotNull(workingDir, if (Os.isWindows) "askalono.exe" else "askalono").joinToString(File.separator)

    override fun transformVersion(output: String) =
        // The version string can be something like:
        // askalono 0.2.0-beta.1
        output.removePrefix("askalono ")
}

@OrtPlugin(
    displayName = "askalono",
    description = "askalono is a library and command-line tool to help detect license texts. It's designed to be " +
        "fast, accurate, and to support a wide variety of license texts.",
    factory = ScannerWrapperFactory::class
)
class Askalono(
    override val descriptor: PluginDescriptor = AskalonoFactory.descriptor,
    config: AskalonoConfig
) : LocalPathScannerWrapper() {
    override val configuration = ""
    override val matcher by lazy { ScannerMatcher.create(details, config) }

    override val version by lazy {
        require(AskalonoCommand.isInPath()) {
            "The '${AskalonoCommand.command()}' command is not available in the PATH environment."
        }

        AskalonoCommand.getVersion()
    }

    override val readFromStorage = config.readFromStorage
    override val writeToStorage = config.writeToStorage

    override fun runScanner(path: File, context: ScanContext): String {
        val process = AskalonoCommand.run(
            "--format", "json",
            "crawl", path.absolutePath
        )

        return with(process) {
            if (stderr.isNotBlank()) logger.debug { stderr }
            if (isError) throw ScanException(errorMessage)

            stdout
        }
    }

    override fun createSummary(result: String, startTime: Instant, endTime: Instant): ScanSummary {
        val results = result.byteInputStream().use { Json.decodeToSequence<AskalonoResult>(it) }

        val licenseFindings = mutableSetOf<LicenseFinding>()

        val issues = mutableListOf(
            Issue(
                source = descriptor.id,
                message = "This scanner is not capable of detecting copyright statements.",
                severity = Severity.HINT
            )
        )

        results.forEach {
            if (it.result != null) {
                licenseFindings += LicenseFinding(
                    license = it.result.license.name,
                    location = TextLocation(it.path, TextLocation.UNKNOWN_LINE),
                    score = it.result.score
                )
            }

            if (it.error != null) {
                issues += Issue(
                    source = descriptor.id,
                    message = it.error,
                    severity = if (it.error == CONFIDENCE_NOTICE) Severity.HINT else Severity.ERROR
                )
            }
        }

        return ScanSummary(
            startTime = startTime,
            endTime = endTime,
            licenseFindings = licenseFindings,
            issues = issues
        )
    }
}
