/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.reporters.trustsource

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.file.aFile
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.reporter.ORT_RESULT
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.test.readResource

class TrustSourceReporterFunTest : StringSpec({
    "The expected report should be generated" {
        val expectedReport = readResource("/expected-report.json")

        val reportFiles = TrustSourceReporter().generateReport(ReporterInput(ORT_RESULT), tempdir())

        reportFiles.shouldBeSingleton {
            it shouldBeSuccess { reportFile ->
                reportFile shouldBe aFile()
                reportFile.readText() shouldEqualJson expectedReport
            }
        }
    }
})
