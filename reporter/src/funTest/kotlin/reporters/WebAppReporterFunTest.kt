/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.longs.beInRange
import io.kotest.matchers.should

import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.test.createTestTempDir
import org.ossreviewtoolkit.utils.test.readOrtResult

class WebAppReporterFunTest : WordSpec({
    "WebAppReporter" should {
        "successfully export to a web application" {
            val ortResult = readOrtResult(
                "../scanner/src/funTest/assets/dummy-expected-output-for-analyzer-result.yml"
            )

            val outputDir = createTestTempDir()

            val report = WebAppReporter().generateReport(ReporterInput(ortResult), outputDir).single()

            report.length() should beInRange(1910000L..1920000L)
        }
    }
})
