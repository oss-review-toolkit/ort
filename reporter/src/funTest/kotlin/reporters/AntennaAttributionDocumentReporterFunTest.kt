/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.test.readOrtResult

class AntennaAttributionDocumentReporterFunTest : StringSpec({
    "Replacement of unavailable glyphs works" {
        val text = "This is a text with \u221e of \u2661 in it."
        replaceGlyphs(text) shouldBe "This is a text with (infinity) of (heart) in it."
    }

    "PDF output is created successfully from an existing result" {
        val ortResult = readOrtResult(
            "../scanner/src/funTest/assets/file-counter-expected-output-for-analyzer-result.yml"
        )

        val report = generateReport(ortResult)

        report.single().length() shouldBe 53797L
    }
})

private fun generateReport(ortResult: OrtResult): List<File> {
    val outputDir = createTempDir(
        ORT_NAME, AntennaAttributionDocumentReporterFunTest::class.simpleName
    ).apply { deleteOnExit() }

    return AntennaAttributionDocumentReporter().generateReport(ReporterInput(ortResult), outputDir)
}
