/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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
import com.here.ort.utils.unpackZip

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.File

class ExcelReporterTest : WordSpec({
    val ortResult = File("../scanner/src/funTest/assets/file-counter-expected-output-for-analyzer-result.yml")
            .readValue<OrtResult>()

    "ExcelReporter" should {
        "successfully export to an Excel sheet" {
            val outputDir = createTempDir().apply { deleteOnExit() }
            ExcelReporter().generateReport(ortResult, DefaultResolutionProvider(), CopyrightGarbage(), outputDir)

            val actualFile = outputDir.resolve("scan-report.xlsx")
            val actualXlsxUnpacked = createTempDir().apply { deleteOnExit() }
            actualFile.unpackZip(actualXlsxUnpacked)

            val expectedFile = File("src/funTest/assets/file-counter-expected-scan-report.xlsx")
            val expectedXlsxUnpacked = createTempDir().apply { deleteOnExit() }
            expectedFile.unpackZip(expectedXlsxUnpacked)

            val sheet1 = "xl/worksheets/sheet1.xml"
            val sheet2 = "xl/worksheets/sheet2.xml"
            actualXlsxUnpacked.resolve(sheet1).readText() shouldBe expectedXlsxUnpacked.resolve(sheet1).readText()
            actualXlsxUnpacked.resolve(sheet2).readText() shouldBe expectedXlsxUnpacked.resolve(sheet2).readText()
        }
    }
})
