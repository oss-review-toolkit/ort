/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
 * Copyright (C) 2021 TNG Technology Consulting GmbH
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

import com.fasterxml.jackson.databind.json.JsonMapper

import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.sequences.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.normalizeLineBreaks
import org.ossreviewtoolkit.utils.test.createTestTempDir
import org.ossreviewtoolkit.utils.test.readOrtResult

class OpossumReporterFunTest : WordSpec({
    "OpossumReporter" should {
        val ortResult = readOrtResult("src/funTest/assets/static-html-reporter-test-input.yml")
        val reportStr = generateReport(ortResult).normalizeLineBreaks()

        "create JSON.GZ output containing an expected string" {
            reportStr shouldContain "fileCreationDate"
        }

        "create a parseable result and contain some expected values" {
            with(JsonMapper().readTree(reportStr)) {
                isObject shouldBe true
                get("metadata").get("projectId").asText() shouldBe "0"
                get("attributionBreakpoints").size() shouldBe 8
                get("externalAttributionSources").size() shouldBe 6
                get("resourcesToAttributions").fieldNames().asSequence() shouldContain
                        "/analyzer/src/funTest/assets/projects/synthetic/gradle/lib/build.gradle/" +
                        "testCompile/junit/junit@4.12/dependencies/com.foobar/foobar@1.0"
            }
        }
    }
})

private fun TestConfiguration.generateReport(ortResult: OrtResult): String {
    val input = ReporterInput(
        ortResult = ortResult,
        resolutionProvider = DefaultResolutionProvider().add(ortResult.getResolutions()),
        howToFixTextProvider = { "Some how to fix text." }
    )

    val outputDir = createTestTempDir()

    val outputFile = OpossumReporter().generateReport(input, outputDir, emptyMap()).single().inputStream()

    return GzipCompressorInputStream(outputFile)
        .bufferedReader()
        .use { it.readText() }
}
