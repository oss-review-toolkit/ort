/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter.reporters

import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import java.io.File

import javax.xml.transform.TransformerFactory

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.reporter.HowToFixTextProvider
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.test.createTestTempDir
import org.ossreviewtoolkit.utils.test.patchExpectedResult
import org.ossreviewtoolkit.utils.test.readOrtResult

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
            val ortResult = readOrtResult("src/funTest/assets/static-html-reporter-test-input.yml")
            val actualReport = generateReport(ortResult).replace(timeStampPattern, "<REPLACE_TIMESTAMP>")

            val expectedReport = patchExpectedResult(
                File("src/funTest/assets/static-html-reporter-test-expected-output.html"),
                mapOf("<REPLACE_ORT_VERSION>" to Environment.ORT_VERSION)
            )

            actualReport shouldBe expectedReport
        }
    }
})

private fun TestConfiguration.generateReport(ortResult: OrtResult): String {
    val input = ReporterInput(
        ortResult = ortResult,
        resolutionProvider = DefaultResolutionProvider.create(ortResult),
        howToFixTextProvider = HOW_TO_FIX_TEXT_PROVIDER
    )

    val outputDir = createTestTempDir()

    return StaticHtmlReporter().generateReport(input, outputDir).single().readText()
}
