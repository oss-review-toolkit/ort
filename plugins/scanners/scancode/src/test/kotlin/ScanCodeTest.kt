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

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldMatch

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk

import java.io.File

import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.common.extractResource
import org.ossreviewtoolkit.utils.test.readResource

class ScanCodeTest : WordSpec({
    val scanner = ScanCodeFactory.create()

    "configuration" should {
        "return the default values if the scanner configuration is empty" {
            scanner.configuration shouldBe "--copyright --license --info --strip-root --timeout 300 --json"
        }

        "return the non-config values from the scanner configuration" {
            val scannerWithConfig = ScanCodeFactory.create(
                commandLine = listOf("--command", "--line"),
                commandLineNonConfig = listOf("--commandLineNonConfig")
            )

            scannerWithConfig.configuration shouldBe "--command --line --json"
        }
    }

    "getCommandLineOptions()" should {
        "contain the default values if the scanner configuration is empty" {
            scanner.getCommandLineOptions("31.2.4").joinToString(" ") shouldMatch
                "--copyright --license --info --strip-root --timeout 300"
            scanner.getCommandLineOptions("32.0.0").joinToString(" ") shouldMatch
                "--copyright --license --info --strip-root --timeout 300 --license-references"
        }

        "contain the values from the scanner configuration" {
            val scannerWithConfig = ScanCodeFactory.create(
                commandLine = listOf("--command", "--line"),
                commandLineNonConfig = listOf("--commandLineNonConfig")
            )

            scannerWithConfig.getCommandLineOptions("31.2.4").joinToString(" ") shouldMatch
                "--command --line --commandLineNonConfig"
        }
    }

    "scanPath()" should {
        "handle a ScanCode result with errors" {
            val path = tempdir("scan-code")

            val process = mockk<ProcessCapture>()
            every { process.isError } returns true
            every { process.stdout } returns ""
            every { process.stderr } returns "some error"

            val scannerSpy = spyk(scanner)
            every { scannerSpy.runScanCode(any(), any()) } answers {
                val targetFile = secondArg<File>()
                extractResource("/scancode-with-issues.json", targetFile)

                process
            }

            val summary = scannerSpy.scanPath(path, ScanContext(labels = emptyMap(), packageType = PackageType.PACKAGE))

            with(summary) {
                licenseFindings shouldNot beEmpty()
                issues.any { "Unexpected EOF" in it.message } shouldBe true
            }
        }
    }

    "transformVersion()" should {
        "work with a version output without a colon" {
            ScanCodeCommand.transformVersion(
                """
                    ScanCode version 30.0.1
                    ScanCode Output Format version 1.0.0
                    SPDX License list version 3.16
                """.trimIndent()
            ) shouldBe "30.0.1"
        }

        "work with a version output with a colon" {
            ScanCodeCommand.transformVersion(
                """
                    ScanCode version: 31.0.0b4
                    ScanCode Output Format version: 2.0.0
                    SPDX License list version: 3.16
                """.trimIndent()
            ) shouldBe "31.0.0b4"
        }
    }

    "parseDetails()" should {
        "return details for a raw scan result" {
            val result = readResource("/scancode-with-issues.json")

            scanner.parseDetails(result) shouldBe ScannerDetails(
                name = "ScanCode",
                version = "30.1.0",
                configuration = "--copyright true --info true " +
                    "--json-pp C:\\temp\\ort\\projects\\test-result\\result.json --license true --max-in-memory 5000 " +
                    "--processes 3 --strip-root true --timeout 300.0"
            )
        }
    }
})
