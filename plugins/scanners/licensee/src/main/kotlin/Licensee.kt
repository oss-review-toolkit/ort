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

package org.ossreviewtoolkit.plugins.scanners.licensee

import java.io.File
import java.time.Instant

import kotlin.collections.joinToString

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.scanner.CommandLinePathScannerWrapper
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.scanner.ScanException
import org.ossreviewtoolkit.scanner.ScannerMatcher
import org.ossreviewtoolkit.scanner.ScannerWrapperConfig
import org.ossreviewtoolkit.scanner.ScannerWrapperFactory
import org.ossreviewtoolkit.utils.common.Options
import org.ossreviewtoolkit.utils.common.Os

private val JSON = Json {
    ignoreUnknownKeys = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}

class Licensee internal constructor(name: String, private val wrapperConfig: ScannerWrapperConfig) :
    CommandLinePathScannerWrapper(name) {
    companion object {
        val CONFIGURATION_OPTIONS = listOf("--json")
    }

    class Factory : ScannerWrapperFactory<Unit>("Licensee") {
        override fun create(config: Unit, wrapperConfig: ScannerWrapperConfig) = Licensee(type, wrapperConfig)

        override fun parseConfig(options: Options, secrets: Options) = Unit
    }

    override val configuration = CONFIGURATION_OPTIONS.joinToString(" ")

    override val matcher by lazy { ScannerMatcher.create(details, wrapperConfig.matcherConfig) }

    override val readFromStorage by lazy { wrapperConfig.readFromStorageWithDefault(matcher) }

    override val writeToStorage by lazy { wrapperConfig.writeToStorageWithDefault(matcher) }

    override fun command(workingDir: File?) =
        listOfNotNull(workingDir, if (Os.isWindows) "licensee.bat" else "licensee").joinToString(File.separator)

    override fun getVersionArguments() = "version"

    override fun runScanner(path: File, context: ScanContext): String {
        val process = run(
            "detect",
            *CONFIGURATION_OPTIONS.toTypedArray(),
            path.absolutePath
        )

        return with(process) {
            if (stderr.isNotBlank()) logger.debug { stderr }
            if (isError) throw ScanException(errorMessage)

            stdout
        }
    }

    override fun parseDetails(result: String): ScannerDetails {
        val details = JSON.parseToJsonElement(result).jsonObject
        val version = details.getValue("version").jsonPrimitive.content

        val parameters = details.getValue("parameters").jsonArray

        return ScannerDetails(
            name = name,
            version = version,
            // TODO: Filter out parameters that have no influence on scan results.
            configuration = parameters.joinToString(" ") { it.jsonPrimitive.content }
        )
    }

    override fun createSummary(result: String, startTime: Instant, endTime: Instant): ScanSummary {
        val results = JSON.decodeFromString<LicenseeResult>(result)

        val licenseFindings = results.matchedFiles.mapTo(mutableSetOf()) {
            LicenseFinding(
                license = it.matchedLicense,
                location = TextLocation(it.filename, TextLocation.UNKNOWN_LINE),
                score = it.matcher.confidence
            )
        }

        return ScanSummary(
            startTime = startTime,
            endTime = endTime,
            licenseFindings = licenseFindings,
            issues = listOf(
                Issue(
                    source = name,
                    message = "This scanner is not capable of detecting copyright statements.",
                    severity = Severity.HINT
                )
            )
        )
    }
}
