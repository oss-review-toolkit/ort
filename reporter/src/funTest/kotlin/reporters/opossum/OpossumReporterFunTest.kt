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

package org.ossreviewtoolkit.reporter.reporters.opossum

import com.fasterxml.jackson.databind.json.JsonMapper

import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.sequences.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.normalizeLineBreaks
import org.ossreviewtoolkit.utils.common.unpackZip
import org.ossreviewtoolkit.utils.test.createTestTempDir
import org.ossreviewtoolkit.utils.test.readOrtResult

class OpossumReporterFunTest : WordSpec({
    "generateReport()" should {
        val ortResult = readOrtResult("src/funTest/assets/static-html-reporter-test-input.yml")
        val reportStr = generateReport(ortResult).normalizeLineBreaks()

        "create '.opossum' output containing an 'input.json' with expected string" {
            reportStr shouldContain "fileCreationDate"
        }

        "create a parseable result and contain some expected values" {
            with(JsonMapper().readTree(reportStr)) {
                isObject shouldBe true
                get("metadata").get("projectId").asText() shouldBe "0"
                get("attributionBreakpoints").size() shouldBe 4
                get("externalAttributionSources").size() shouldBe 6
                get("resourcesToAttributions").fieldNames().asSequence() shouldContain
                        "/analyzer/src/funTest/assets/projects/synthetic/gradle/lib/build.gradle/" +
                        "compile/org.apache.commons/commons-text@1.1/dependencies/org.apache.commons/commons-lang3@3.5"
            }
        }
    }
})

private fun TestConfiguration.generateReport(ortResult: OrtResult): String {
    val input = ReporterInput(
        ortResult = ortResult,
        resolutionProvider = DefaultResolutionProvider(ortResult.getResolutions()),
        howToFixTextProvider = { "Some how to fix text." }
    )

    val outputDir = createTestTempDir()
    val outputFile = OpossumReporter().generateReport(input, outputDir, emptyMap()).single()
    outputFile.unpackZip(outputDir)

    return outputDir.resolve("input.json").readText()
}
