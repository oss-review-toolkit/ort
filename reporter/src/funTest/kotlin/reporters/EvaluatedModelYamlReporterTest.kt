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

package com.here.ort.reporter.reporters

import com.here.ort.model.OrtResult
import com.here.ort.reporter.ReporterInput
import com.here.ort.utils.test.readOrtResult

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.ByteArrayOutputStream
import java.io.File

class EvaluatedModelYamlReporterTest : WordSpec({
    "Evaluated model" should {
        "contain the expected stats" {
            val expectedResult = File(
                "src/funTest/assets/evaluated-model-test-expected-statistics.yml"
            ).readText()
            val ortResult = readOrtResult("src/funTest/assets/static-html-reporter-test-input.yml")

            println(generateReport(ortResult))
            generateReport(ortResult) shouldBe expectedResult
        }
    }
})

private fun generateReport(ortResult: OrtResult) =
    ByteArrayOutputStream().also { outputStream ->
        EvaluatedModelYamlReporter().generateReport(outputStream, ReporterInput(ortResult))
    }.toString("UTF-8")
