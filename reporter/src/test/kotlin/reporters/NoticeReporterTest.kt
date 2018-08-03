/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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
import com.here.ort.model.readValue

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.File

class NoticeReporterTest : WordSpec({
    "Notices reporter" should {
        "generate the correct license notes" {
            val expectedResultFile = File("src/test/assets/NPM-is-windows-1.0.2-expected-NOTICE")
            val expectedText = expectedResultFile.readText()
            val scanRecordFile = File("src/test/assets/NPM-is-windows-1.0.2-scan-result.json")
            val ortResult = scanRecordFile.readValue(OrtResult::class.java)
            val outputDir = createTempDir().also { it.deleteOnExit() }

            NoticeReporter().generateReport(ortResult, outputDir)

            val resultFile = File(outputDir, "NOTICE")
            val actualText = resultFile.readText()

            actualText shouldBe expectedText
        }
    }
})
