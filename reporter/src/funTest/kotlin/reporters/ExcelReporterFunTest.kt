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

package org.ossreviewtoolkit.reporter.reporters

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

import java.io.File

import org.apache.poi.ss.usermodel.WorkbookFactory

import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.test.readOrtResult

class ExcelReporterFunTest : WordSpec({
    "ExcelReporter" should {
        "successfully export to an Excel sheet" {
            val outputDir = createTempDir(ORT_NAME, javaClass.simpleName).apply { deleteOnExit() }
            val ortResult = readOrtResult(
                "../scanner/src/funTest/assets/file-counter-expected-output-for-analyzer-result.yml"
            )

            val report = ExcelReporter().generateReport(ReporterInput(ortResult), outputDir).single()
            val actualWorkbook = WorkbookFactory.create(report)
            val actualSheetNames = actualWorkbook.sheetIterator().asSequence().map { it.sheetName }.toList()

            val asset = File("src/funTest/assets/file-counter-expected-scan-report.xlsx")
            val expectedWorkbook = WorkbookFactory.create(asset)
            val expectedSheetNames = expectedWorkbook.sheetIterator().asSequence().map { it.sheetName }.toList()

            actualSheetNames shouldContainExactly expectedSheetNames
            actualSheetNames.map { "Sheet" to it }.forAll { (_, sheetName) ->
                val actualSheet = actualWorkbook.getSheet(sheetName)
                val actualRowNums = actualSheet.rowIterator().asSequence().map { it.rowNum }.toList()

                val expectedSheet = expectedWorkbook.getSheet(sheetName)
                val expectedRowNums = expectedSheet.rowIterator().asSequence().map { it.rowNum }.toList()

                actualRowNums shouldContainExactly expectedRowNums
                actualRowNums.map { "Row" to it }.forAll { (_, rowNum) ->
                    val actualRow = actualSheet.getRow(rowNum)
                    val actualColumnIndices = actualRow.cellIterator().asSequence().map { it.columnIndex }.toList()

                    val expectedRow = expectedSheet.getRow(rowNum)
                    val expectedColumnIndices = expectedRow.cellIterator().asSequence().map { it.columnIndex }.toList()

                    actualColumnIndices shouldContainExactly expectedColumnIndices
                    actualColumnIndices.map { Triple(sheetName, rowNum, it) }.forAll { (_, _, columnIndex) ->
                        val actualCell = actualRow.getCell(columnIndex)
                        val expectedCell = expectedRow.getCell(columnIndex)

                        actualCell.cellType shouldBe expectedCell.cellType
                        actualCell.stringCellValue shouldBe expectedCell.stringCellValue
                        actualCell.cellStyle.fontIndexAsInt shouldBe expectedCell.cellStyle.fontIndexAsInt
                        actualCell.cellStyle.fillBackgroundColor shouldBe expectedCell.cellStyle.fillBackgroundColor
                    }
                }
            }
        }
    }
})
