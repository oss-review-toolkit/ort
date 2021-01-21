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

package org.ossreviewtoolkit.scanner.scanners.scancode

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch

import org.ossreviewtoolkit.model.config.ScannerConfiguration

class ScanCodeTest : WordSpec({
    val scanner = ScanCode("ScanCode", ScannerConfiguration())

    "getConfiguration()" should {
        "return the default values if the scanner configuration is empty" {
            scanner.configuration shouldBe "--copyright --license --info --strip-root --timeout 300 --json-pp"
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

            scannerWithConfig.configuration shouldBe "--command --line --json-pp --debug --commandLine"
        }
    }

    "commandLineOptions" should {
        "contain the default values if the scanner configuration is empty" {
            scanner.commandLineOptions.joinToString(" ") shouldMatch
                    "--copyright --license --info --strip-root --timeout 300 --processes \\d+ --verbose"
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
