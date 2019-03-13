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

import bad.robot.excel.matchers.WorkbookMatcher.sameWorkbook

import com.here.ort.model.OrtResult
import com.here.ort.model.config.CopyrightGarbage
import com.here.ort.model.readValue
import com.here.ort.reporter.DefaultResolutionProvider

import io.kotlintest.specs.WordSpec

import java.io.File

import org.apache.poi.ss.usermodel.WorkbookFactory

import org.hamcrest.MatcherAssert.assertThat

class ExcelReporterTest : WordSpec({
    val ortResult = File("../scanner/src/funTest/assets/file-counter-expected-output-for-analyzer-result.yml")
            .readValue<OrtResult>()

    "ExcelReporter" should {
        "successfully export to an Excel sheet" {
            val outputDir = createTempDir().apply { deleteOnExit() }
            val actualFile = outputDir.resolve("scan-report.xlsx")

            ExcelReporter().generateReport(ortResult, DefaultResolutionProvider(), CopyrightGarbage(),
                    actualFile.outputStream())

            val expectedFile = File("src/funTest/assets/file-counter-expected-scan-report.xlsx")

            assertThat(WorkbookFactory.create(actualFile), sameWorkbook(WorkbookFactory.create(expectedFile)))
        }
    }
})
