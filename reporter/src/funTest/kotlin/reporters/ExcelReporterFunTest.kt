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

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.util.CellReference

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

            // Open the sheet in shared read mode so Excel can have the file opened in the background.
            val path = Paths.get("src/funTest/assets/file-counter-expected-scan-report.xlsx")
            val expectedWorkbook = Files.newInputStream(path, StandardOpenOption.READ).use {
                WorkbookFactory.create(it)
            }
            val expectedSheetNames = expectedWorkbook.sheetIterator().asSequence().map { it.sheetName }.toList()

            actualSheetNames shouldContainExactly expectedSheetNames
            actualSheetNames.map { Address(it) }.forAll { sheetAddress ->
                val actualSheet = actualWorkbook.getSheet(sheetAddress.sheet)
                val actualRowNums = actualSheet.rowIterator().asSequence().map {
                    sheetAddress.copy(row = it.rowNum)
                }.toList()

                val expectedSheet = expectedWorkbook.getSheet(sheetAddress.sheet)
                val expectedRowNums = expectedSheet.rowIterator().asSequence().map {
                    sheetAddress.copy(row = it.rowNum)
                }.toList()

                actualRowNums shouldContainExactly expectedRowNums
                actualRowNums.forAll { rowAddress ->
                    // The non-null assertion is fine as we pass a row in the outer loop.
                    val actualRow = actualSheet.getRow(rowAddress.row!!)
                    val actualColumnIndices = actualRow.cellIterator().asSequence().map {
                        rowAddress.copy(column = it.columnIndex)
                    }.toList()

                    val expectedRow = expectedSheet.getRow(rowAddress.row)
                    val expectedColumnIndices = expectedRow.cellIterator().asSequence().map {
                        rowAddress.copy(column = it.columnIndex)
                    }.toList()

                    actualColumnIndices shouldContainExactly expectedColumnIndices
                    actualColumnIndices.forAll { columnAddress ->
                        // The non-null assertion is fine as we pass a column in the outer loop.
                        val actualCell = actualRow.getCell(columnAddress.column!!)
                        val expectedCell = expectedRow.getCell(columnAddress.column)

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

private data class Address(val sheet: String, val row: Int? = null, val column: Int? = null) {
    override fun toString() =
        listOfNotNull(
            "sheet=$sheet",
            row?.let { "row=${row + 1}" },
            column?.let { "column=${CellReference.convertNumToColString(column)}" }
        ).joinToString(prefix = "${javaClass.simpleName}(", postfix = ")")
}
