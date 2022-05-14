/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2022 Bosch.IO GmbH
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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldMatch

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk

import java.io.File

import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.test.createTestTempDir
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class ScanCodeTest : WordSpec({
    val scanner = ScanCode("ScanCode", ScannerConfiguration(), DownloaderConfiguration())

    "configuration()" should {
        "return the default values if the scanner configuration is empty" {
            scanner.configuration shouldBe "--copyright --license --info --strip-root --timeout 300 --json-pp"
        }

        "return the non-config values from the scanner configuration" {
            val scannerWithConfig = ScanCode(
                "ScanCode",
                ScannerConfiguration(
                    options = mapOf(
                        "ScanCode" to mapOf(
                            "commandLine" to "--command --line",
                            "commandLineNonConfig" to "--commandLineNonConfig"
                        )
                    )
                ),
                DownloaderConfiguration()
            )

            scannerWithConfig.configuration shouldBe "--command --line --json-pp"
        }
    }

    "commandLineOptions" should {
        "contain the default values if the scanner configuration is empty" {
            scanner.commandLineOptions.joinToString(" ") shouldMatch
                    "--copyright --license --info --strip-root --timeout 300 --processes \\d+"
        }

        "contain the values from the scanner configuration" {
            val scannerWithConfig = ScanCode(
                "ScanCode",
                ScannerConfiguration(
                    options = mapOf(
                        "ScanCode" to mapOf(
                            "commandLine" to "--command --line",
                            "commandLineNonConfig" to "--commandLineNonConfig"
                        )
                    )
                ),
                DownloaderConfiguration()
            )

            scannerWithConfig.commandLineOptions.joinToString(" ") shouldBe
                    "--command --line --commandLineNonConfig"
        }
    }

    "scanPathInternal" should {
        "handle a ScanCode result with errors" {
            val path = createTestTempDir("scan-code")

            val process = mockk<ProcessCapture>()
            every { process.isError } returns true
            every { process.stderr } returns "some error"
            every { process.errorMessage } returns "some error message"

            val scannerSpy = spyk(scanner)
            every { scannerSpy.details } returns ScannerDetails("ScanCode", "30.1.0", "")
            every { scannerSpy.runScanCode(any(), any()) } answers {
                val resultFile = File("src/test/assets/scancode-with-issues.json")
                val targetFile = secondArg<File>()
                resultFile.copyTo(targetFile)

                process
            }

            val result = scannerSpy.scanPath(path)

            result.scanner?.results.shouldNotBeNull {
                val summary = scanResults.iterator().next().value.single().summary
                summary.licenseFindings shouldNot beEmpty()
                summary.issues.find { it.message.contains("Unexpected EOF") } shouldNot beNull()
            }
        }
    }

    "transformVersion" should {
        val scanCode = ScanCode(
            "ScanCode",
            ScannerConfiguration(),
            DownloaderConfiguration()
        )

        "work with a version output without a colon" {
            scanCode.transformVersion(
                """
                    ScanCode version 30.0.1
                    ScanCode Output Format version 1.0.0
                    SPDX License list version 3.16
                """.trimIndent()
            ) shouldBe "30.0.1"
        }

        "work with a version output with a colon" {
            scanCode.transformVersion(
                """
                    ScanCode version: 31.0.0b4
                    ScanCode Output Format version: 2.0.0
                    SPDX License list version: 3.16
                """.trimIndent()
            ) shouldBe "31.0.0b4"
        }
    }
})
