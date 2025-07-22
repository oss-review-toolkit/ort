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

package org.ossreviewtoolkit.plugins.reporters.opossum

import io.kotest.assertions.json.shouldEqualSpecifiedJsonIgnoringOrder
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir

import java.time.LocalDateTime

import org.ossreviewtoolkit.plugins.licensefactproviders.spdx.SpdxLicenseFactProviderFactory
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.unpackZip
import org.ossreviewtoolkit.utils.test.patchActualResult
import org.ossreviewtoolkit.utils.test.patchExpectedResult
import org.ossreviewtoolkit.utils.test.readOrtResult
import org.ossreviewtoolkit.utils.test.readResource

class OpossumReporterFunTest : WordSpec({
    val replacements = mapOf(
        "(\"fileCreationDate\" ?: ?)\"[^\"]+\"" to "$1\"${LocalDateTime.MIN}\"",
        "\"[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\"" to "\"00000000-0000-0000-0000-000000000000\""
    )

    "The generated report" should {
        "match the expected result" {
            val ortResult = readOrtResult("/reporter-test-input.yml")
            val input = ReporterInput(ortResult, licenseFactProvider = SpdxLicenseFactProviderFactory.create())
            val outputDir = tempdir()
            val expectedResult = readResource("/reporter-test-output.json")

            OpossumReporterFactory.create().generateReport(input, outputDir).single().getOrThrow().unpackZip(outputDir)

            val actualResult = outputDir.resolve("input.json").readText()
            val patchedActualResult = patchActualResult(actualResult, custom = replacements)
            patchedActualResult shouldEqualSpecifiedJsonIgnoringOrder patchExpectedResult(
                expectedResult,
                custom = replacements
            )
        }
    }
})
