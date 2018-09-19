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

import ch.frankel.slf4k.*

import com.here.ort.model.ScanRecord
import com.here.ort.model.VcsInfo
import com.here.ort.utils.isValidUrl
import com.here.ort.utils.log

import org.apache.poi.common.usermodel.HyperlinkType
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.CreationHelper
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
    private val defaultColumns = 5

    private val borderColor = XSSFColor(Color(211, 211, 211))
    private val errorColor = XSSFColor(Color(240, 128, 128))
    private val successColor = XSSFColor(Color(173, 216, 230))
    private val warningColor = XSSFColor(Color(255, 255, 224))

    private lateinit var defaultStyle: CellStyle
    private lateinit var headerStyle: CellStyle
    private lateinit var successStyle: CellStyle
    private lateinit var warningStyle: CellStyle
    private lateinit var errorStyle: CellStyle

    private lateinit var creationHelper: CreationHelper

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

        creationHelper = workbook.creationHelper

        if (tabularScanRecord.metadata.isNotEmpty()) {
            createMetadataSheet(workbook, tabularScanRecord.metadata)
        }

        createSummarySheet(workbook, "Summary", "all", tabularScanRecord.summary, tabularScanRecord.vcsInfo,
                tabularScanRecord.extraColumns)
        tabularScanRecord.projectDependencies.forEach { project, table ->
            createProjectSheet(workbook, project.id.toString(), project.definitionFilePath, table, project.vcsProcessed,
                    tabularScanRecord.extraColumns)
        }

        val outputFile = File(outputDir, "scan-report.xlsx")

        log.info { "Writing Excel report to '${outputFile.absolutePath}'." }

        outputFile.outputStream().use {
            workbook.write(it)
        }
    }

    private fun createMetadataSheet(workbook: XSSFWorkbook, metadata: Map<String, String>) {
        val sheetName = createUniqueSheetName(workbook, "Metadata")

        val sheet = workbook.createSheet(sheetName)

        var currentRow = 0

        sheet.createRow(currentRow).apply {
            CellUtil.createCell(this, 0, "Metadata", headerStyle)
        }

        metadata.forEach { (key, value) ->
            sheet.createRow(++currentRow).apply {
                CellUtil.createCell(this, 0, "$key:", defaultStyle)
                CellUtil.createCell(this, 1, value, defaultStyle).apply {
                    if (value.isValidUrl()) {
                        hyperlink = creationHelper.createHyperlink(HyperlinkType.URL).apply {
                            address = value
                        }
                    }
                }
            }
        }

        (0..1).forEach { sheet.autoSizeColumn(it) }
    }

    private fun createSummarySheet(workbook: XSSFWorkbook, name: String, file: String, table: SummaryTable,
                                   vcsInfo: VcsInfo, extraColumns: List<String>) {
        val sheetName = createUniqueSheetName(workbook, name)

        val sheet = workbook.createSheet(sheetName)

        val headerRows = createHeader(sheet, name, file, vcsInfo, extraColumns)
        var currentRow = headerRows

        table.rows.forEach { row ->
            val cellStyle = when {
                row.analyzerErrors.isNotEmpty() || row.scanErrors.isNotEmpty() -> errorStyle
                row.declaredLicenses.isEmpty() && row.detectedLicenses.isEmpty() -> warningStyle
                else -> successStyle
            }

            sheet.createRow(currentRow).apply {
                CellUtil.createCell(this, 0, row.id.toString(), cellStyle)
                CellUtil.createCell(this, 1, row.scopes.keys.joinWithLimit(), cellStyle)
                CellUtil.createCell(this, 2, row.declaredLicenses.joinWithLimit(), cellStyle)
                CellUtil.createCell(this, 3, row.detectedLicenses.joinWithLimit(), cellStyle)
                CellUtil.createCell(this, 4, row.analyzerErrors.map { it.toString() }.joinWithLimit(), cellStyle)
                CellUtil.createCell(this, 5, row.scanErrors.map { it.toString() }.joinWithLimit(), cellStyle)

                val maxLines = listOf(row.scopes.values, row.declaredLicenses, row.detectedLicenses,
                        row.analyzerErrors.values, row.scanErrors.values).map { it.size }.max() ?: 1
                heightInPoints = maxLines * getSheet().defaultRowHeightInPoints
            }
            ++currentRow
        }

        sheet.finalize(headerRows, currentRow, defaultColumns + extraColumns.size)
    }

    private fun createProjectSheet(workbook: XSSFWorkbook, name: String, file: String, table: ProjectTable,
                                   vcsInfo: VcsInfo, extraColumns: List<String>) {
        val sheetName = createUniqueSheetName(workbook, name)

        val sheet = workbook.createSheet(sheetName)

        val headerRows = createHeader(sheet, name, file, vcsInfo, extraColumns)
        var currentRow = headerRows

        table.rows.forEach { row ->
            val cellStyle = when {
                row.analyzerErrors.isNotEmpty() || row.scanErrors.isNotEmpty() -> errorStyle
                row.declaredLicenses.isEmpty() && row.detectedLicenses.isEmpty() -> warningStyle
                else -> successStyle
            }

            sheet.createRow(currentRow).apply {
                CellUtil.createCell(this, 0, row.id.toString(), cellStyle)
                CellUtil.createCell(this, 1, row.scopes.keys.joinWithLimit(), cellStyle)
                CellUtil.createCell(this, 2, row.declaredLicenses.joinWithLimit(), cellStyle)
                CellUtil.createCell(this, 3, row.detectedLicenses.joinWithLimit(), cellStyle)
                CellUtil.createCell(this, 4, row.analyzerErrors.map { it.toString() }.joinWithLimit(), cellStyle)
                CellUtil.createCell(this, 5, row.scanErrors.map { it.toString() }.joinWithLimit(), cellStyle)

                val maxLines = listOf(row.scopes.values, row.declaredLicenses, row.detectedLicenses,
                        row.analyzerErrors, row.scanErrors).map { it.size }.max() ?: 1
                heightInPoints = maxLines * getSheet().defaultRowHeightInPoints
            }
            ++currentRow
        }

        sheet.finalize(headerRows, currentRow, defaultColumns + extraColumns.size)
    }

    private fun createHeader(sheet: XSSFSheet, name: String, file: String, vcsInfo: VcsInfo,
                             extraColumns: List<String>): Int {
        val columns = defaultColumns + extraColumns.size

        sheet.createRow(0).apply {
            CellUtil.createCell(this, 0, "Project:", headerStyle)
            CellUtil.createCell(this, 1, name, headerStyle)
        }
        sheet.addMergedRegion(CellRangeAddress(0, 0, 1, columns))

        sheet.createRow(1).apply {
            CellUtil.createCell(this, 0, "File:", headerStyle)
            CellUtil.createCell(this, 1, file, headerStyle)
        }
        sheet.addMergedRegion(CellRangeAddress(1, 1, 1, columns))

        var rows = 2

        sheet.createRow(++rows).apply {
            CellUtil.createCell(this, 0, "VCS", headerStyle)
        }

        sheet.createRow(++rows).apply {
            CellUtil.createCell(this, 0, "Type:", defaultStyle)
            CellUtil.createCell(this, 1, vcsInfo.type, defaultStyle)
        }
        sheet.addMergedRegion(CellRangeAddress(rows, rows, 1, columns))

        sheet.createRow(++rows).apply {
            CellUtil.createCell(this, 0, "URL:", defaultStyle)
            CellUtil.createCell(this, 1, vcsInfo.url, defaultStyle)
        }
        sheet.addMergedRegion(CellRangeAddress(rows, rows, 1, columns))

        sheet.createRow(++rows).apply {
            CellUtil.createCell(this, 0, "Path:", defaultStyle)
            CellUtil.createCell(this, 1, vcsInfo.path, defaultStyle)
        }
        sheet.addMergedRegion(CellRangeAddress(rows, rows, 1, columns))

        sheet.createRow(++rows).apply {
            CellUtil.createCell(this, 0, "Revision:", defaultStyle)
            CellUtil.createCell(this, 1, vcsInfo.revision, defaultStyle)
        }
        sheet.addMergedRegion(CellRangeAddress(rows, rows, 1, columns))

        ++rows

        sheet.createRow(++rows).apply {
            CellUtil.createCell(this, 0, "Package", headerStyle)
            CellUtil.createCell(this, 1, "Scopes", headerStyle)
            CellUtil.createCell(this, 2, "Declared Licenses", headerStyle)
            CellUtil.createCell(this, 3, "Detected Licenses", headerStyle)
            CellUtil.createCell(this, 4, "Analyzer Errors", headerStyle)
            CellUtil.createCell(this, 5, "Scan Errors", headerStyle)

            extraColumns.forEachIndexed { index, column ->
                CellUtil.createCell(this, 6 + index, column, headerStyle)
            }
        }

        return ++rows
    }
}

// Use the same name as in XSSFWorkbook.MAX_SENSITIVE_SHEET_NAME_LEN, which is private.
private const val MAX_SENSITIVE_SHEET_NAME_LEN = 31

internal fun createUniqueSheetName(workbook: XSSFWorkbook, name: String): String {
    fun isSheetNameTaken(workbook: XSSFWorkbook, name: String) =
            name.toLowerCase() in Sequence { workbook.sheetIterator() }.map { it.sheetName.toLowerCase() }

    var uniqueName = WorkbookUtil.createSafeSheetName(name)
    var i = 0

    while (isSheetNameTaken(workbook, uniqueName)) {
        val suffix = "-${++i}"
        uniqueName = uniqueName.take(MAX_SENSITIVE_SHEET_NAME_LEN - suffix.length) + suffix
    }

    return uniqueName
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

private fun XSSFSheet.finalize(headerRows: Int, totalRows: Int, totalColumns: Int) {
    createFreezePane(1, headerRows)
    (0..totalColumns).forEach { autoSizeColumn(it) }
    setAutoFilter(CellRangeAddress(headerRows - 1, totalRows - 1, 0, totalColumns))
}

private const val ELLIPSIS = "[...]"

private fun Collection<*>.joinWithLimit(
        separator: String = " \n",
        maxLength: Int = 32767 /* This is the maximum Excel cell content length. */
) = joinToString(separator).let { cellContent ->
    cellContent.takeIf { it.length <= maxLength } ?: "${cellContent.take(maxLength - ELLIPSIS.length)}$ELLIPSIS"
}
