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

import com.here.ort.model.ScanRecord

import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.ss.util.CellUtil
import org.apache.poi.ss.util.WorkbookUtil
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook

import java.awt.Color
import java.io.File

/**
 * A [Reporter] that creates an Excel sheet report from a [ScanRecord] in the Open XML XLSX format. It creates one sheet
 * for each project in [ScanRecord.analyzerResult] and an additional sheet that summarizes all dependencies.
 */
class ExcelReporter : TableReporter() {
    val borderColor = XSSFColor(Color(211, 211, 211))
    val errorColor = XSSFColor(Color(240, 128, 128))
    val successColor = XSSFColor(Color(173, 216, 230))
    val warningColor = XSSFColor(Color(255, 255, 224))

    lateinit var defaultStyle: CellStyle
    lateinit var headerStyle: CellStyle
    lateinit var successStyle: CellStyle
    lateinit var warningStyle: CellStyle
    lateinit var errorStyle: CellStyle

    override fun generateReport(tabularScanRecord: TabularScanRecord, outputDir: File) {
        val workbook = XSSFWorkbook()

        defaultStyle = workbook.createCellStyle().apply {
            setVerticalAlignment(VerticalAlignment.TOP)
            wrapText = true

            setBorder(BorderStyle.THIN)
            setBorderColor(borderColor)
        }

        val headerFont = workbook.createFont().apply {
            bold = true
        }

        headerStyle = workbook.createCellStyle().apply {
            cloneStyleFrom(defaultStyle)
            setFont(headerFont)
        }

        successStyle = workbook.createCellStyle().apply {
            cloneStyleFrom(defaultStyle)
            setFillForegroundColor(successColor)
            setFillPattern(FillPatternType.SOLID_FOREGROUND)
        }

        warningStyle = workbook.createCellStyle().apply {
            cloneStyleFrom(defaultStyle)
            setFillForegroundColor(warningColor)
            setFillPattern(FillPatternType.SOLID_FOREGROUND)
        }

        errorStyle = workbook.createCellStyle().apply {
            cloneStyleFrom(defaultStyle)
            setFillForegroundColor(errorColor)
            setFillPattern(FillPatternType.SOLID_FOREGROUND)
        }

        createSheet(workbook, "Summary", "all", tabularScanRecord.summary)
        tabularScanRecord.projectDependencies.forEach { project, table ->
            createSheet(workbook, project.id.toString(), project.definitionFilePath, table)
        }

        val outputFile = File(outputDir, "scan-report.xlsx")
        println("Writing Excel report to '${outputFile.absolutePath}'.")
        outputFile.outputStream().use {
            workbook.write(it)
        }
    }

    fun createSheet(workbook: XSSFWorkbook, name: String, file: String, table: Table) {
        var sheetName = WorkbookUtil.createSafeSheetName(name).let {
            var uniqueName = it
            var i = 0

            while (uniqueName in Sequence { workbook.sheetIterator() }.map { it.sheetName }) {
                var suffix = "-${++i}"
                uniqueName = uniqueName.dropLast(suffix.length) + suffix
            }
            uniqueName
        }

        val sheet = workbook.createSheet(sheetName)

        val headerRows = createHeader(sheet, name, file)
        var currentRow = headerRows

        table.entries.forEach { entry ->
            val cellStyle = when {
                entry.analyzerErrors.isNotEmpty() || entry.scanErrors.isNotEmpty() -> errorStyle
                entry.declaredLicenses.isEmpty() && entry.detectedLicenses.isEmpty() -> warningStyle
                else -> successStyle
            }

            sheet.createRow(currentRow).apply {
                CellUtil.createCell(this, 0, entry.id.toString(), cellStyle)
                CellUtil.createCell(this, 1, entry.scopes.joinWithLimit(), cellStyle)
                CellUtil.createCell(this, 2, entry.declaredLicenses.joinWithLimit(), cellStyle)
                CellUtil.createCell(this, 3, entry.detectedLicenses.joinWithLimit(), cellStyle)
                CellUtil.createCell(this, 4, entry.analyzerErrors.joinWithLimit(), cellStyle)
                CellUtil.createCell(this, 5, entry.scanErrors.joinWithLimit(), cellStyle)

                val maxLines = listOf(entry.scopes, entry.declaredLicenses, entry.detectedLicenses,
                        entry.analyzerErrors, entry.scanErrors).map { it.size }.max() ?: 1
                heightInPoints = maxLines * getSheet().defaultRowHeightInPoints
            }
            ++currentRow
        }

        sheet.finalize(headerRows, currentRow)
    }

    private fun createHeader(sheet: XSSFSheet, name: String, file: String): Int {
        sheet.createRow(0).apply {
            CellUtil.createCell(this, 0, "Project:", headerStyle)
            CellUtil.createCell(this, 1, name, headerStyle)
        }
        sheet.addMergedRegion(CellRangeAddress(0, 0, 1, 5))

        sheet.createRow(1).apply {
            CellUtil.createCell(this, 0, "File:", headerStyle)
            CellUtil.createCell(this, 1, file, headerStyle)
        }
        sheet.addMergedRegion(CellRangeAddress(1, 1, 1, 5))

        sheet.createRow(3).apply {
            CellUtil.createCell(this, 0, "Package", headerStyle)
            CellUtil.createCell(this, 1, "Scopes", headerStyle)
            CellUtil.createCell(this, 2, "Declared Licenses", headerStyle)
            CellUtil.createCell(this, 3, "Detected Licenses", headerStyle)
            CellUtil.createCell(this, 4, "Analyzer Errors", headerStyle)
            CellUtil.createCell(this, 5, "Scan Errors", headerStyle)
        }

        return 4
    }
}

private fun XSSFCellStyle.setBorder(borderStyle: BorderStyle) {
    setBorderTop(borderStyle)
    setBorderRight(borderStyle)
    setBorderBottom(borderStyle)
    setBorderLeft(borderStyle)
}

private fun XSSFCellStyle.setBorderColor(borderColor: XSSFColor) {
    setTopBorderColor(borderColor)
    setRightBorderColor(borderColor)
    setBottomBorderColor(borderColor)
    setLeftBorderColor(borderColor)
}

private fun XSSFSheet.finalize(headerRows: Int, totalRows: Int) {
    createFreezePane(1, headerRows)
    (0..5).forEach { autoSizeColumn(it) }
    setAutoFilter(CellRangeAddress(headerRows - 1, totalRows - 1, 0, 5))
}

private val ELLIPSIS = "[...]"

private fun Collection<String>.joinWithLimit(
        separator: String = " \n",
        maxLength: Int = 32767 /* This is the maximum Excel cell content length. */
) = joinToString(separator).let {
    it.takeIf { it.length <= maxLength } ?: "${it.dropLast(ELLIPSIS.length)}$ELLIPSIS"
}
