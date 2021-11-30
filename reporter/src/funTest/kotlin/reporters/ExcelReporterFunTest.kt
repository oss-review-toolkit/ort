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
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.util.CellReference

import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.core.createOrtTempDir
import org.ossreviewtoolkit.utils.test.readOrtResult

class ExcelReporterFunTest : WordSpec({
    "ExcelReporter" should {
        "successfully export to an Excel sheet" {
            // TODO: Find out why Apache POI seems to prevent immediate deletion of the written XLSX file on Windows.
            val outputDir = createOrtTempDir().apply { deleteOnExit() }
            val ortResult = readOrtResult(
                "../scanner/src/funTest/assets/dummy-expected-output-for-analyzer-result.yml"
            )

            val report = ExcelReporter().generateReport(ReporterInput(ortResult), outputDir).single()
            val actualWorkbook = WorkbookFactory.create(report)
            val actualSheetNames = actualWorkbook.sheetIterator().asSequence().map { it.sheetName }.toList()

            // Open the sheet in shared read mode so Excel can have the file opened in the background.
            val path = Paths.get("src/funTest/assets/dummy-expected-scan-report.xlsx")
            val expectedWorkbook = Files.newInputStream(path, StandardOpenOption.READ).use {
                WorkbookFactory.create(it)
            }
            val expectedSheetNames = expectedWorkbook.sheetIterator().asSequence().map { it.sheetName }.toList()

            actualSheetNames shouldContainExactly expectedSheetNames

            actualSheetNames.map { Address(it) }.forAll { sheetAddress ->
                val actualSheet = actualWorkbook.getSheet(sheetAddress.sheet)
                val actualAddresses = mutableSetOf<Address>()
                actualSheet.rowIterator().asSequence().forEach { row ->
                    actualSheet.uniqueCells(row).mapTo(actualAddresses) { cell ->
                        sheetAddress.copy(row = cell.rowIndex, column = cell.columnIndex)
                    }
                }

                val expectedSheet = expectedWorkbook.getSheet(sheetAddress.sheet)
                val expectedAddresses = mutableSetOf<Address>()
                expectedSheet.rowIterator().asSequence().forEach { row ->
                    expectedSheet.uniqueCells(row).mapTo(expectedAddresses) { cell ->
                        sheetAddress.copy(row = cell.rowIndex, column = cell.columnIndex)
                    }
                }

                actualAddresses shouldContainExactly expectedAddresses

                actualAddresses.forAll { address ->
                    // The non-null assertions are fine as we pass a row / column in the outer loop.
                    val actualCell = actualSheet.getRow(address.row!!).getCell(address.column!!)
                    val expectedCell = expectedSheet.getRow(address.row).getCell(address.column)

                    actualCell.cellType shouldBe expectedCell.cellType
                    actualCell.stringCellValue shouldBe expectedCell.stringCellValue
                    actualCell.cellStyle.fillBackgroundColor shouldBe expectedCell.cellStyle.fillBackgroundColor
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

private fun Sheet.uniqueCells(row: Row): Sequence<Cell> =
    row.cellIterator().asSequence().distinctBy { cell ->
        mergedRegions.find { range ->
            range.isInRange(cell)
        }?.first() ?: cell
    }
