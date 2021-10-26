/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.reporter.ORT_RESULT
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.reporter.patchAsciiDocTemplateResult
import org.ossreviewtoolkit.utils.test.createTestTempDir

class XHtmlTemplateReporterFunTest : StringSpec({
    "XHTML report is created from default template" {
        val expectedText = File("src/funTest/assets/xhtml-template-reporter-expected-result.xhtml").readText()

        val reportContent =
            XHtmlTemplateReporter().generateReport(ReporterInput(ORT_RESULT), createTestTempDir()).single().readText()

        reportContent.patchAsciiDocTemplateResult() shouldBe expectedText
    }
})
