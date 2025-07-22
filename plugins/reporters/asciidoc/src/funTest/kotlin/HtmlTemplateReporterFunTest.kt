/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.plugins.licensefactproviders.spdx.SpdxLicenseFactProviderFactory
import org.ossreviewtoolkit.reporter.ORT_RESULT
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.test.patchExpectedResult
import org.ossreviewtoolkit.utils.test.readResource

class HtmlTemplateReporterFunTest : StringSpec({
    "HTML report is created from default template" {
        val expectedResult = readResource("/html-template-reporter-expected-result.html")

        val reporter = HtmlTemplateReporterFactory.create()
        val reportContent = reporter.generateReport(
            ReporterInput(
                ORT_RESULT,
                licenseFactProvider = SpdxLicenseFactProviderFactory.create()
            ),
            tempdir()
        ).single().getOrThrow().readText()

        reportContent.patchAsciiDocTemplateResult() shouldBe patchExpectedResult(
            expectedResult,
            custom = mapOf(
                "<REPLACE_ASCIIDOCTOR_VERSION>" to AsciiDocTemplateReporter.ASCIIDOCTOR.asciidoctorVersion()
            )
        )
    }
})

private fun String.patchAsciiDocTemplateResult() =
    // Asciidoctor renders the line breaks platform dependent.
    replace("\r\n", "\n")
        .replace("""\d{4}-\d{2}-\d{2}""".toRegex(), "1970-01-01")
        .replace("""\d{2}:\d{2}:\d{2}""".toRegex(), "00:00:00")
        // Asciidoctor renders time zones differently depending on the platform.
        // For macOS the time is rendered as `00:00:00 +0000` while for Linux it is `00:00:00 UTC`.
        .replace("""[+-]\d{4}""".toRegex(), "UTC")
