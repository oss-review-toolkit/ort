/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

package com.here.ort.reporter.reporters

import com.here.ort.model.OrtResult
import com.here.ort.model.config.CopyrightGarbage
import com.here.ort.model.readValue
import com.here.ort.reporter.DefaultResolutionProvider
import com.here.ort.utils.safeDeleteRecursively

import io.kotlintest.Description
import io.kotlintest.TestResult
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.matchers.string.shouldNotContain

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.File

class NoticeReporterTest : WordSpec() {
    companion object {
        private fun readOrtResult(file: String) = File(file).readValue<OrtResult>()
    }

    private lateinit var tempDir: File

    override fun beforeTest(description: Description) {
        super.beforeTest(description)
        tempDir = createTempDir()
    }

    override fun afterTest(description: Description, result: TestResult) {
        tempDir.safeDeleteRecursively()
        super.afterTest(description, result)
    }

    private fun generateReport(ortResult: OrtResult,
                               copyrightGarbage: CopyrightGarbage = CopyrightGarbage(),
                               postProcessingScript: String? = null
    ): String {
        NoticeReporter().generateReport(
                ortResult,
                DefaultResolutionProvider(),
                copyrightGarbage,
                tempDir,
                postProcessingScript
        )

        return File(tempDir, "NOTICE").readText()
    }

    init {
        "NoticeReporter" should {
            "generate the correct license notes" {
                val expectedText = File("src/funTest/assets/NPM-is-windows-1.0.2-expected-NOTICE").readText()
                val ortResult = readOrtResult("src/funTest/assets/NPM-is-windows-1.0.2-scan-result.json")

                val report = generateReport(ortResult)

                report shouldBe expectedText
            }

            "contain all licenses without excludes" {
                val expectedText = File("src/funTest/assets/npm-test-without-exclude-expected-NOTICE").readText()
                val ortResult = readOrtResult("src/funTest/assets/npm-test-without-exclude-scan-results.yml")

                val report = generateReport(ortResult)

                report shouldBe expectedText
            }

            "not contain licenses of excluded packages" {
                val expectedText = File("src/funTest/assets/npm-test-with-exclude-expected-NOTICE").readText()
                val ortResult = readOrtResult("src/funTest/assets/npm-test-with-exclude-scan-results.yml")

                val report = generateReport(ortResult)

                report shouldBe expectedText
            }

            "evaluate the provided post-processing script" {
                val expectedText = File("src/funTest/assets/post-processed-expected-NOTICE").readText()
                val ortResult = readOrtResult("src/funTest/assets/NPM-is-windows-1.0.2-scan-result.json")

                val postProcessingScript = """
                    headers = listOf("Header 1\n", "Header 2\n")
                    findings = noticeReport.findings.filter { (_, copyrights) -> copyrights.isEmpty() }.toSortedMap()
                    footers = listOf("Footer 1\n", "Footer 2\n")
                """.trimIndent()

                val report = generateReport(ortResult, postProcessingScript = postProcessingScript)

                report shouldBe expectedText
            }

            "return the input as-is for an empty post-processing script" {
                val expectedText = File("src/funTest/assets/NPM-is-windows-1.0.2-expected-NOTICE").readText()
                val ortResult = readOrtResult("src/funTest/assets/NPM-is-windows-1.0.2-scan-result.json")

                val report = generateReport(ortResult, postProcessingScript = "")

                report shouldBe expectedText
            }

            "contain a copyright statement if not contained in copyright garbage" {
                val ortResult = readOrtResult("src/funTest/assets/npm-test-with-exclude-scan-results.yml")

                val report = generateReport(ortResult, CopyrightGarbage())

                report.shouldContain("\nCopyright (c) Felix Bohm")
            }

            "contain a copyright statement if only its prefix is contained in copyright garbage" {
                val ortResult = readOrtResult("src/funTest/assets/npm-test-with-exclude-scan-results.yml")

                val report = generateReport(ortResult, CopyrightGarbage("Copyright (c) Fel"))

                report.shouldContain("\nCopyright (c) Felix Bohm")
            }

            "contain a copyright statement if only its super string contained in copyright garbage" {
                val ortResult = readOrtResult("src/funTest/assets/npm-test-with-exclude-scan-results.yml")

                val report = generateReport(ortResult, CopyrightGarbage("Copyright (c) Felix BohmX"))

                report.shouldContain("\nCopyright (c) Felix Bohm")
            }

            "not contain a copyright statement if it is contained in garbage" {
                val ortResult = readOrtResult("src/funTest/assets/npm-test-with-exclude-scan-results.yml")

                val report = generateReport(ortResult, CopyrightGarbage("Copyright (c) Felix Bohm"))

                report.shouldNotContain("\nCopyright (c) Felix Bohm")
            }
        }
    }
}
