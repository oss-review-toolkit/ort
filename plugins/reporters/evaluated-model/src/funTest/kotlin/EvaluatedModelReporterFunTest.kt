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

package org.ossreviewtoolkit.plugins.reporters.evaluatedmodel

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.normalizeLineBreaks
import org.ossreviewtoolkit.utils.test.readOrtResult
import org.ossreviewtoolkit.utils.test.readResource

class EvaluatedModelReporterFunTest : WordSpec({
    fun EvaluatedModelReporter.generateReport(ortResult: OrtResult) =
        generateReport(
            input = ReporterInput(
                ortResult = ortResult,
                howToFixTextProvider = { "Some how to fix text." }
            ),
            outputDir = tempdir()
        ).single().getOrThrow().readText().normalizeLineBreaks()

    "EvaluatedModelReporter" should {
        "create the expected JSON output" {
            val expectedResult = readResource("/evaluated-model-reporter-test-expected-output.json")
            val ortResult = readOrtResult("/reporter-test-input.yml")

            EvaluatedModelReporterFactory.create().generateReport(ortResult) shouldBe expectedResult
        }

        "create the expected YAML output" {
            val expectedResult = readResource("/evaluated-model-reporter-test-expected-output.yml")
            val ortResult = readOrtResult("/reporter-test-input.yml")

            EvaluatedModelReporterFactory.create(
                outputFileFormats = listOf("yml")
            ).generateReport(ortResult) shouldBe expectedResult
        }

        "create the expected YAML output with dependency tree de-duplication enabled" {
            val expectedResult = readResource("/evaluated-model-reporter-test-deduplicate-expected-output.yml")
            val ortResult = readOrtResult("/reporter-test-input.yml")

            EvaluatedModelReporterFactory.create(
                outputFileFormats = listOf("yml"),
                deduplicateDependencyTree = true
            ).generateReport(ortResult) shouldBe expectedResult
        }
    }
})
