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

package org.ossreviewtoolkit.reporter.reporters

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.contain

import java.io.File

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.reporter.ORT_RESULT
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.ORT_NAME

class NoticeSummaryReporterTest : WordSpec({
    "NoticeReporter" should {
        "generate the correct license notes" {
            val expectedText = File("src/funTest/assets/notice-summary-reporter-expected-results").readText()

            val report = generateReport(ORT_RESULT)

            report shouldBe expectedText
        }

        "evaluate the provided pre-processing script" {
            val expectedText =
                File("src/funTest/assets/notice-summary-reporter-pre-processed-expected-results").readText()

            // Filter out all MIT findings so that this license is not contained in the generated notices.
            val preProcessingScript = createTempFile(ORT_NAME, javaClass.simpleName).apply {
                writeText("""
                    headers = listOf("Header 1\n", "Header 2\n")
                    headerWithLicenses = "Custom header with licenses.\n"
                    findings = model.findings.mapValues { (_, findings) ->
                        findings.filterKeys { it != "MIT" }.toSortedMap()
                    }.toSortedMap()
                    footers = listOf("Footer 1\n", "Footer 2\n")
                    """.trimIndent()
                )
            }

            val report = generateReport(ORT_RESULT, preProcessingScript = preProcessingScript)

            report shouldBe expectedText
        }

        "return the input as-is for an empty pre-processing script" {
            val expectedText = File("src/funTest/assets/notice-summary-reporter-expected-results").readText()

            val preProcessingScript = createTempFile(ORT_NAME, javaClass.simpleName)

            val report = generateReport(ORT_RESULT, preProcessingScript = preProcessingScript)

            report shouldBe expectedText
        }

        "contain a copyright statement if not contained in copyright garbage" {
            val report = generateReport(ORT_RESULT, CopyrightGarbage())

            report should contain("\nCopyright 1")
        }

        "contain a copyright statement if only its prefix is contained in copyright garbage" {
            val report = generateReport(ORT_RESULT, CopyrightGarbage("Copyright"))

            report should contain("\nCopyright 1")
        }

        "contain a copyright statement if only its super string contained in copyright garbage" {
            val report = generateReport(ORT_RESULT, CopyrightGarbage("Copyright 1X"))

            report should contain("\nCopyright 1")
        }

        "not contain a copyright statement if it is contained in garbage" {
            val report = generateReport(ORT_RESULT, CopyrightGarbage("Copyright 1"))

            report shouldNot contain("\nCopyright 1")
        }
    }
})

private fun generateReport(
    ortResult: OrtResult,
    copyrightGarbage: CopyrightGarbage = CopyrightGarbage(),
    preProcessingScript: File? = null
): String {
    val input = ReporterInput(
        ortResult,
        copyrightGarbage = copyrightGarbage
    )

    val outputDir = createTempDir(ORT_NAME, NoticeSummaryReporterTest::class.simpleName).apply { deleteOnExit() }

    val options = preProcessingScript?.let { mapOf("preProcessingScript" to it.absolutePath) }.orEmpty()

    return NoticeSummaryReporter().generateReport(input, outputDir, options).single().readText()
}
