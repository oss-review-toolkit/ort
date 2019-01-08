/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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
import com.here.ort.model.config.CopyrightGarbage
import com.here.ort.model.readValue
import com.here.ort.reporter.DefaultResolutionProvider

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.File

class WebAppReporterTest : WordSpec({
    val ortResult = File("../scanner/src/funTest/assets/file-counter-expected-output-for-analyzer-result.yml")
            .readValue<OrtResult>()

    "WebAppReporter" should {
        "successfully export to a web application" {
            val outputDir = createTempDir().apply { deleteOnExit() }
            WebAppReporter().generateReport(ortResult, DefaultResolutionProvider(), CopyrightGarbage(), outputDir)
            outputDir.resolve("scan-report-web-app.html").isFile shouldBe true
        }
    }
})
