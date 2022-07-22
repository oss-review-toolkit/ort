/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter.reporters.evaluatedmodel

import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.normalizeLineBreaks
import org.ossreviewtoolkit.utils.test.createTestTempDir
import org.ossreviewtoolkit.utils.test.getAssetAsString
import org.ossreviewtoolkit.utils.test.readOrtResult

class EvaluatedModelReporterFunTest : WordSpec({
    "EvaluatedModelReporter" should {
        "create the expected JSON output" {
            val expectedResult = getAssetAsString("evaluated-model-reporter-test-expected-output.json")
            val ortResult = readOrtResult("src/funTest/assets/static-html-reporter-test-input.yml")

            generateReport(ortResult) shouldBe expectedResult
        }

        "create the expected YAML output" {
            val expectedResult = getAssetAsString("evaluated-model-reporter-test-expected-output.yml")
            val ortResult = readOrtResult("src/funTest/assets/static-html-reporter-test-input.yml")
            val options = mapOf(EvaluatedModelReporter.OPTION_OUTPUT_FILE_FORMATS to FileFormat.YAML.fileExtension)

            generateReport(ortResult, options) shouldBe expectedResult
        }

        "create the expected YAML output with dependency tree de-duplication enabled" {
            val expectedResult = getAssetAsString("evaluated-model-reporter-test-deduplicate-expected-output.yml")
            val ortResult = readOrtResult("src/funTest/assets/static-html-reporter-test-input.yml")
            val options = mapOf(
                EvaluatedModelReporter.OPTION_OUTPUT_FILE_FORMATS to FileFormat.YAML.fileExtension,
                EvaluatedModelReporter.OPTION_DEDUPLICATE_DEPENDENCY_TREE to "True"
            )

            generateReport(ortResult, options) shouldBe expectedResult
        }
    }
})

private fun TestConfiguration.generateReport(ortResult: OrtResult, options: Map<String, String> = emptyMap()): String {
    val input = ReporterInput(
        ortResult = ortResult,
        resolutionProvider = DefaultResolutionProvider.create(ortResult),
        howToFixTextProvider = { "Some how to fix text." }
    )

    val outputDir = createTestTempDir()

    return EvaluatedModelReporter().generateReport(input, outputDir, options).single().readText().normalizeLineBreaks()
}
