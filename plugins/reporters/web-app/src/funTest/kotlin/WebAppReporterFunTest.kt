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

package org.ossreviewtoolkit.plugins.reporters.webapp

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.longs.beInRange
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should

import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.test.readOrtResult

class WebAppReporterFunTest : WordSpec({
    "WebAppReporter" should {
        "successfully export to a web application" {
            val ortResult = readOrtResult("/scan-result-for-synthetic-gradle-lib.yml")
            val outputDir = tempdir()

            val reportFileResults = WebAppReporterFactory.create()
                .generateReport(ReporterInput(ortResult), outputDir)

            reportFileResults.shouldBeSingleton {
                it shouldBeSuccess { reportFile ->
                    reportFile.length() should beInRange(1599000L..1629000L)
                }
            }
        }
    }
})
