/*
 * Copyright (C) 2020-2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.reporter.reporters.freemarker.asciidoc

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.contain
import io.kotest.matchers.longs.beInRange
import io.kotest.matchers.should

import org.ossreviewtoolkit.reporter.ORT_RESULT
import org.ossreviewtoolkit.reporter.ORT_RESULT_WITH_VULNERABILITIES
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.test.createTestTempDir

class PdfTemplateReporterFunTest : StringSpec({
    "The report is created successfully from an existing result and default template" {
        val report = PdfTemplateReporter().generateReport(ReporterInput(ORT_RESULT), createTestTempDir())

        report.single().length() should beInRange(92000L..97000L)
    }

    "Report generation is aborted when path to non-existing PDF theme file is given" {
        shouldThrow<IllegalArgumentException> {
            PdfTemplateReporter().generateReport(
                ReporterInput(ORT_RESULT),
                createTestTempDir(),
                mapOf("pdf.theme.file" to "dummy.file")
            )
        }
    }

    "Report generation is aborted when a non-existent PDF fonts directory is given" {
        shouldThrow<IllegalArgumentException> {
            PdfTemplateReporter().generateReport(
                ReporterInput(ORT_RESULT),
                createTestTempDir(),
                mapOf("pdf.fonts.dir" to "fake.path")
            )
        }
    }

    "Advisor reports are generated if the result contains an advisor section" {
        val reports =
            PdfTemplateReporter().generateReport(ReporterInput(ORT_RESULT_WITH_VULNERABILITIES), createTestTempDir())

        val reportFileNames = reports.map { it.name }
        reportFileNames should contain("AsciiDoc_vulnerability_report.pdf")
        reportFileNames should contain("AsciiDoc_defect_report.pdf")
    }
})
