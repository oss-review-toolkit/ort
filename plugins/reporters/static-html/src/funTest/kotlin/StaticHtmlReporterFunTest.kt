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

package org.ossreviewtoolkit.plugins.reporters.statichtml

import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe

import javax.xml.transform.TransformerFactory

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.reporter.HowToFixTextProvider
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.ort.ORT_VERSION
import org.ossreviewtoolkit.utils.test.patchExpectedResult
import org.ossreviewtoolkit.utils.test.readOrtResult
import org.ossreviewtoolkit.utils.test.readResource

private val HOW_TO_FIX_TEXT_PROVIDER = HowToFixTextProvider {
    """
        * *Step 1*
        * __Step 2__
        * ***Step 3***
        ```Some long issue resolution text to verify that overflow:scroll is working as expected.``` 
    """.trimIndent()
}

class StaticHtmlReporterFunTest : WordSpec({
    "StaticHtmlReporter" should {
        "use the Saxon TransformerFactory" {
            val transformer = TransformerFactory.newInstance().newTransformer()

            transformer.javaClass.name shouldBe "net.sf.saxon.jaxp.IdentityTransformer"
        }

        "successfully export to a static HTML page" {
            val timeStampPattern = Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z")
            val ortResult = readOrtResult("/reporter-test-input.yml")
            val actualReport = generateReport(ortResult).replaceFirst(timeStampPattern, "<REPLACE_TIMESTAMP>")

            val expectedReport = patchExpectedResult(
                readResource("/static-html-reporter-test-expected-output.html"),
                custom = mapOf("<REPLACE_ORT_VERSION>" to ORT_VERSION)
            )

            actualReport shouldBe expectedReport
        }
    }
})

private fun TestConfiguration.generateReport(ortResult: OrtResult): String {
    val input = ReporterInput(
        ortResult = ortResult,
        howToFixTextProvider = HOW_TO_FIX_TEXT_PROVIDER
    )

    val outputDir = tempdir()

    return StaticHtmlReporter().generateReport(input, outputDir).single().getOrThrow().readText()
}
