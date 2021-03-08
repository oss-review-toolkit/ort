/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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
import io.kotest.matchers.shouldBe

import java.io.File

import kotlin.io.path.createTempDirectory

import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.normalizeLineBreaks
import org.ossreviewtoolkit.utils.test.readOrtResult

class EvaluatedModelReporterFunTest : WordSpec({
    "EvaluatedModelReporter" should {
        "create the expected JSON output" {
            val expectedResult = File(
                "src/funTest/assets/evaluated-model-reporter-test-expected-output.json"
            ).readText()
            val ortResult = readOrtResult("src/funTest/assets/static-html-reporter-test-input.yml")

            generateReport(ortResult).normalizeLineBreaks() shouldBe expectedResult
        }

        "create the expected YAML output" {
            val expectedResult = File(
                "src/funTest/assets/evaluated-model-reporter-test-expected-output.yml"
            ).readText()
            val ortResult = readOrtResult("src/funTest/assets/static-html-reporter-test-input.yml")

            val options = mapOf(EvaluatedModelReporter.OPTION_OUTPUT_FILE_FORMATS to FileFormat.YAML.fileExtension)
            generateReport(ortResult, options) shouldBe expectedResult
        }
    }
})

private fun generateReport(ortResult: OrtResult, options: Map<String, String> = emptyMap()): String {
    val input = ReporterInput(
        ortResult = ortResult,
        resolutionProvider = DefaultResolutionProvider().add(ortResult.getResolutions()),
        howToFixTextProvider = { "Some how to fix text." }
    )

    val outputDir = createTempDirectory("$ORT_NAME-${EvaluatedModelReporterFunTest::class.simpleName}").toFile().apply {
        deleteOnExit()
    }

    return EvaluatedModelReporter().generateReport(input, outputDir, options).single().readText()
}
