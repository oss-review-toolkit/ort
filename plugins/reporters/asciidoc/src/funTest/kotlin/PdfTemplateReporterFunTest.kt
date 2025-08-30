/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.reporters.asciidoc

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.longs.beInRange
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.plugins.licensefactproviders.spdx.SpdxLicenseFactProviderFactory
import org.ossreviewtoolkit.reporter.ORT_RESULT
import org.ossreviewtoolkit.reporter.ORT_RESULT_WITH_VULNERABILITIES
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.div

class PdfTemplateReporterFunTest : StringSpec({
    "The report is created successfully from an existing result and default template" {
        val reportFileResults = PdfTemplateReporterFactory.create().generateReport(
            ReporterInput(
                ORT_RESULT,
                licenseFactProvider = SpdxLicenseFactProviderFactory.create()
            ),
            tempdir()
        )

        reportFileResults.shouldBeSingleton {
            it shouldBeSuccess { reportFile ->
                reportFile.reader().use { stream ->
                    val header = CharArray(4)
                    stream.read(header) shouldBe header.size
                    String(header) shouldBe "%PDF"
                }

                reportFile.length() should beInRange(111000L..115000L)
            }
        }
    }

    "Report generation is aborted when path to non-existing PDF theme file is given" {
        shouldThrow<IllegalArgumentException> {
            PdfTemplateReporterFactory.create(pdfThemeFile = "dummy.file")
                .generateReport(ReporterInput(ORT_RESULT), tempdir())
        }
    }

    "Report generation is aborted when a non-existent PDF fonts directory is given" {
        shouldThrow<IllegalArgumentException> {
            PdfTemplateReporterFactory.create(pdfFontsDir = "fake.path")
                .generateReport(ReporterInput(ORT_RESULT), tempdir())
        }
    }

    "Advisor reports are generated if the result contains an advisor section" {
        val outputDir = tempdir()
        val reportFileResults = PdfTemplateReporterFactory.create().generateReport(
            ReporterInput(ORT_RESULT_WITH_VULNERABILITIES),
            outputDir
        )

        val reportFiles = reportFileResults.map { it.shouldBeSuccess() }
        reportFiles should containExactlyInAnyOrder(
            outputDir / "AsciiDoc_defect_report.pdf",
            outputDir / "AsciiDoc_disclosure_document.pdf",
            outputDir / "AsciiDoc_vulnerability_report.pdf"
        )
    }
})
