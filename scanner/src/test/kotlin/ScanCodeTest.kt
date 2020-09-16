/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package org.ossreviewtoolkit.scanner

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.scanner.scanners.ScanCode
import org.ossreviewtoolkit.scanner.scanners.generateSummary
import org.ossreviewtoolkit.utils.ORT_REPO_CONFIG_FILENAME

class ScanCodeTest : WordSpec({
    val scanner = ScanCode("ScanCode", ScannerConfiguration())

    "mapTimeoutErrors()" should {
        "return true for scan results with only timeout errors" {
            val resultFile = File("src/test/assets/esprima-2.7.3_scancode-2.2.1.post277.4d68f9377.json")
            val result = scanner.getRawResult(resultFile)

            // The "scanPath" argument should point to the path that was scanned, but as the scanned files are
            // not available here anymore, instead pass the "resultFile" to test the calculation of the package
            // verification code on an arbitrary file.
            val summary = generateSummary(Instant.now(), Instant.now(), resultFile, result)

            val issues = summary.issues.toMutableList()

            ScanCode.mapTimeoutErrors(issues) shouldBe true
            issues.joinToString("\n") { it.message } shouldBe listOf(
                "ERROR: Timeout after 300 seconds while scanning file " +
                        "'test/3rdparty/syntax/angular-1.2.5.tokens'.",
                "ERROR: Timeout after 300 seconds while scanning file " +
                        "'test/3rdparty/syntax/jquery-1.9.1.tokens'.",
                "ERROR: Timeout after 300 seconds while scanning file " +
                        "'test/3rdparty/syntax/jquery.mobile-1.4.2.tokens'.",
                "ERROR: Timeout after 300 seconds while scanning file " +
                        "'test/3rdparty/syntax/mootools-1.4.5.tokens'.",
                "ERROR: Timeout after 300 seconds while scanning file " +
                        "'test/3rdparty/syntax/underscore-1.5.2.tokens'.",
                "ERROR: Timeout after 300 seconds while scanning file " +
                        "'test/3rdparty/syntax/yui-3.12.0.tokens'.",

                "ERROR: Timeout after 300 seconds while scanning file " +
                        "'test/3rdparty/syntax/angular-1.2.5.json'.",
                "ERROR: Timeout after 300 seconds while scanning file " +
                        "'test/3rdparty/syntax/jquery-1.9.1.json'.",
                "ERROR: Timeout after 300 seconds while scanning file " +
                        "'test/3rdparty/syntax/jquery.mobile-1.4.2.json'.",
                "ERROR: Timeout after 300 seconds while scanning file " +
                        "'test/3rdparty/syntax/mootools-1.4.5.json'.",
                "ERROR: Timeout after 300 seconds while scanning file " +
                        "'test/3rdparty/syntax/underscore-1.5.2.json'.",
                "ERROR: Timeout after 300 seconds while scanning file " +
                        "'test/3rdparty/syntax/yui-3.12.0.json'."
            ).joinToString("\n")
        }

        "return false for scan results without errors" {
            val resultFile = File("src/test/assets/esprima-2.7.3_scancode-2.2.1.json")
            val result = scanner.getRawResult(resultFile)

            // The "scanPath" argument should point to the path that was scanned, but as the scanned files are
            // not available here anymore, instead pass the "resultFile" to test the calculation of the package
            // verification code on an arbitrary file.
            val summary = generateSummary(Instant.now(), Instant.now(), resultFile, result)

            ScanCode.mapTimeoutErrors(summary.issues.toMutableList()) shouldBe false
        }
    }

    "mapUnknownErrors()" should {
        "return true for scan results with only memory errors" {
            val resultFile = File("src/test/assets/very-long-json-lines_scancode-2.2.1.post277.4d68f9377.json")
            val result = scanner.getRawResult(resultFile)

            // The "scanPath" argument should point to the path that was scanned, but as the scanned files are
            // not available here anymore, instead pass the "resultFile" to test the calculation of the package
            // verification code on an arbitrary file.
            val summary = generateSummary(Instant.now(), Instant.now(), resultFile, result)

            val issues = summary.issues.toMutableList()

            ScanCode.mapUnknownIssues(issues) shouldBe true
            issues.joinToString("\n") { it.message } shouldBe listOf(
                "ERROR: MemoryError while scanning file 'data.json'."
            ).joinToString("\n")
        }

        "return false for scan results with other unknown errors" {
            val resultFile = File("src/test/assets/kotlin-annotation-processing-gradle-1.2.21_scancode.json")
            val result = scanner.getRawResult(resultFile)

            // The "scanPath" argument should point to the path that was scanned, but as the scanned files are
            // not available here anymore, instead pass the "resultFile" to test the calculation of the package
            // verification code on an arbitrary file.
            val summary = generateSummary(Instant.now(), Instant.now(), resultFile, result)

            val issues = summary.issues.toMutableList()

            ScanCode.mapUnknownIssues(issues) shouldBe false
            issues.joinToString("\n") { it.message } shouldBe listOf(
                "ERROR: AttributeError while scanning file 'compiler/testData/cli/js-dce/withSourceMap.js.map' " +
                        "('NoneType' object has no attribute 'splitlines')."
            ).joinToString("\n")
        }

        "return false for scan results without errors" {
            val resultFile = File("src/test/assets/esprima-2.7.3_scancode-2.2.1.json")
            val result = scanner.getRawResult(resultFile)

            // The "scanPath" argument should point to the path that was scanned, but as the scanned files are
            // not available here anymore, instead pass the "resultFile" to test the calculation of the package
            // verification code on an arbitrary file.
            val summary = generateSummary(Instant.now(), Instant.now(), resultFile, result)

            ScanCode.mapUnknownIssues(summary.issues.toMutableList()) shouldBe false
        }
    }

    "getConfiguration()" should {
        "return the default values if the scanner configuration is empty" {
            scanner.getConfiguration() shouldBe
                    "--copyright --license --ignore *$ORT_REPO_CONFIG_FILENAME --info --strip-root --timeout 300 " +
                    "--ignore HERE_NOTICE --ignore META-INF/DEPENDENCIES --json-pp --license-diag"
        }

        "return the non-config values from the scanner configuration" {
            val scannerWithConfig = ScanCode(
                "ScanCode", ScannerConfiguration(
                    options = mapOf(
                        "ScanCode" to mapOf(
                            "commandLine" to "--command --line",
                            "commandLineNonConfig" to "--commandLineNonConfig",
                            "debugCommandLine" to "--debug --commandLine",
                            "debugCommandLineNonConfig" to "--debugCommandLineNonConfig"
                        )
                    )
                )
            )

            scannerWithConfig.getConfiguration() shouldBe "--command --line --json-pp --debug --commandLine"
        }
    }

    "commandLineOptions" should {
        "contain the default values if the scanner configuration is empty" {
            scanner.commandLineOptions.joinToString(" ") shouldMatch
                    "--copyright --license --ignore \\*$ORT_REPO_CONFIG_FILENAME --info --strip-root --timeout 300 " +
                        "--ignore HERE_NOTICE --ignore META-INF/DEPENDENCIES --processes \\d+ --license-diag " +
                        "--verbose"
        }

        "contain the values from the scanner configuration" {
            val scannerWithConfig = ScanCode(
                "ScanCode", ScannerConfiguration(
                    options = mapOf(
                        "ScanCode" to mapOf(
                            "commandLine" to "--command --line",
                            "commandLineNonConfig" to "--commandLineNonConfig",
                            "debugCommandLine" to "--debug --commandLine",
                            "debugCommandLineNonConfig" to "--debugCommandLineNonConfig"
                        )
                    )
                )
            )

            scannerWithConfig.commandLineOptions.joinToString(" ") shouldBe
                    "--command --line --commandLineNonConfig --debug --commandLine --debugCommandLineNonConfig"
        }
    }
})
