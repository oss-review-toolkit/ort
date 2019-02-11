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

import com.here.ort.model.AnalyzerResult
import com.here.ort.model.OrtResult
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.CopyrightGarbage
import com.here.ort.model.config.ProjectExclude
import com.here.ort.reporter.Reporter
import com.here.ort.reporter.ResolutionProvider
import com.here.ort.reporter.reporters.ReportTableModel.ProjectTable
import com.here.ort.reporter.reporters.ReportTableModel.SummaryTable
import com.here.ort.utils.isValidUri

import java.awt.Color
import java.io.OutputStream

import org.apache.poi.common.usermodel.HyperlinkType
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.CreationHelper
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.RichTextString
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.ss.util.CellUtil
import org.apache.poi.ss.util.WorkbookUtil
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFFont
import org.apache.poi.xssf.usermodel.XSSFRichTextString
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook

/**
 * A [Reporter] that creates an Excel sheet report from an [OrtResult] in the Open XML XLSX format. It creates one sheet
 * for each project in the [AnalyzerResult] from [OrtResult.analyzer] and an additional sheet that summarizes all
 * dependencies.
 */
class ExcelReporter : Reporter() {
    private val defaultColumns = 5

    private val borderColor = XSSFColor(Color(211, 211, 211))
    private val errorColor = XSSFColor(Color(240, 128, 128))
    private val excludedColor = XSSFColor(Color(180, 180, 180))
    private val excludedFontColor = XSSFColor(Color(100, 100, 100))
    private val successColor = XSSFColor(Color(173, 216, 230))
    private val warningColor = XSSFColor(Color(255, 255, 224))

    private lateinit var defaultStyle: CellStyle
    private lateinit var excludedStyle: CellStyle
    private lateinit var headerStyle: CellStyle
    private lateinit var successStyle: CellStyle
    private lateinit var warningStyle: CellStyle
    private lateinit var errorStyle: CellStyle

    private lateinit var defaultFont: XSSFFont
    private lateinit var headerFont: XSSFFont
    private lateinit var excludedFont: XSSFFont

    private lateinit var creationHelper: CreationHelper

    override val reporterName = "Excel"
    override val defaultFilename = "scan-report.xlsx"

    override fun generateReport(
            ortResult: OrtResult,
            resolutionProvider: ResolutionProvider,
            copyrightGarbage: CopyrightGarbage,
            outputStream: OutputStream,
            postProcessingScript: String?
    ) {
        val tabularScanRecord = ReportTableModelMapper(resolutionProvider).mapToReportTableModel(ortResult)
        val workbook = XSSFWorkbook()

        defaultStyle = workbook.createCellStyle().apply {
            setVerticalAlignment(VerticalAlignment.TOP)
            wrapText = true

            setBorder(BorderStyle.THIN)
            setBorderColor(borderColor)
        }

        defaultFont = workbook.createFont().apply {
            fontHeightInPoints = 11
        }

        headerFont = workbook.createFont().apply {
            fontHeightInPoints = 11
            bold = true
        }

        excludedFont = workbook.createFont().apply {
            fontHeightInPoints = 11
            setColor(excludedFontColor)
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

        excludedStyle = workbook.createCellStyle().apply {
            cloneStyleFrom(defaultStyle)
            setFillForegroundColor(excludedColor)
            setFillPattern(FillPatternType.SOLID_FOREGROUND)
        }

        creationHelper = workbook.creationHelper

        if (tabularScanRecord.metadata.isNotEmpty()) {
            createMetadataSheet(workbook, tabularScanRecord.metadata)
        }

        createSummarySheet(workbook, "Summary", "all", tabularScanRecord.summary, tabularScanRecord.vcsInfo,
                tabularScanRecord.extraColumns)
        tabularScanRecord.projectDependencies.forEach { project, table ->
            createProjectSheet(workbook, project.id.toCoordinates(), project.definitionFilePath, table,
                    project.vcsProcessed, tabularScanRecord.extraColumns)
        }

        workbook.write(outputStream)
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
                    if (value.isValidUri()) {
                        hyperlink = creationHelper.createHyperlink(HyperlinkType.URL).apply {
                            address = value
                        }
                    }
                }
            }
        }

        repeat(2) { sheet.autoSizeColumn(it) }
    }

    private fun createSummarySheet(workbook: XSSFWorkbook, name: String, file: String, table: SummaryTable,
                                   vcsInfo: VcsInfo, extraColumns: List<String>) {
        val sheetName = createUniqueSheetName(workbook, name)

        val sheet = workbook.createSheet(sheetName)

        val headerRows = createHeader(sheet, name, null, file, vcsInfo, extraColumns)
        var currentRow = headerRows

        table.rows.forEach { row ->
            val isExcluded = row.scopes.all { (id, scopes) ->
                table.projectExcludes[id] != null || (scopes.isNotEmpty() && scopes.all { it.value.isNotEmpty() })
            }

            val font = if (isExcluded) excludedFont else defaultFont

            val cellStyle = when {
                isExcluded -> excludedStyle
                row.analyzerIssues.containsUnresolved() || row.scanIssues.containsUnresolved() -> errorStyle
                row.declaredLicenses.isEmpty() && row.detectedLicenses.isEmpty() -> warningStyle
                else -> successStyle
            }

            val scopesText = XSSFRichTextString()

            row.scopes.entries.sortedWith(compareBy({
                table.projectExcludes[it.key] != null || it.value.values.isNotEmpty()
            }, { it.key })).forEach { (id, scopes) ->
                val projectExclude = table.projectExcludes[id]

                if (projectExclude != null) {
                    scopesText.append("${id.toCoordinates()} " +
                            "(Excluded: ${projectExclude.reason} - ${projectExclude.comment})\n",
                            excludedFont)
                } else {
                    scopesText.append("${id.toCoordinates()}\n", font)
                }

                scopes.entries.sortedWith(compareBy({ it.value.isNotEmpty() }, { it.key }))
                        .forEach { (scope, excludes) ->
                            scopesText.append("  $scope\n",
                                    if (projectExclude != null || excludes.isNotEmpty()) excludedFont else font)

                            excludes.forEach { exclude ->
                                scopesText.append("    Excluded: ${exclude.reason} - ${exclude.comment}\n",
                                        excludedFont)
                            }
                        }
            }

            val scopesLines = row.scopes.size + row.scopes.toList().sumBy { it.second.size } +
                    row.scopes.flatMap { it.value.values }.size

            val analyzerIssuesText = buildString {
                row.analyzerIssues.forEach { (id, issues) ->
                    append("${id.toCoordinates()}\n")

                    issues.forEach { issue ->
                        append("  ${issue.description}\n")
                    }
                }
            }

            val analyzerIssuesLines = row.analyzerIssues.size + row.analyzerIssues.flatMap { it.value }.size

            val scanIssuesText = buildString {
                row.scanIssues.forEach { (id, issues) ->
                    append("${id.toCoordinates()}\n")

                    issues.forEach { issue ->
                        append("  ${issue.description}\n")
                    }
                }
            }

            val scanIssuesLines = row.scanIssues.size + row.scanIssues.flatMap { it.value }.size

            sheet.createRow(currentRow).apply {
                createCell(this, 0, row.id.toCoordinates(), font, cellStyle)
                createCell(this, 1, scopesText, cellStyle)
                createCell(this, 2, row.declaredLicenses.joinToString(" \n"), font, cellStyle)
                createCell(this, 3, row.detectedLicenses.joinToString(" \n"), font, cellStyle)
                createCell(this, 4, analyzerIssuesText, font, cellStyle)
                createCell(this, 5, scanIssuesText, font, cellStyle)

                val maxLines = listOf(scopesLines, row.declaredLicenses.size, row.detectedLicenses.size,
                        analyzerIssuesLines, scanIssuesLines).max() ?: 1
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

        val headerRows = createHeader(sheet, name, table.exclude, file, vcsInfo, extraColumns)
        var currentRow = headerRows

        table.rows.forEach { row ->
            val isExcluded = table.exclude != null ||
                    (row.scopes.values.let { it.isNotEmpty() && it.all { it.isNotEmpty() } })

            val font = if (isExcluded) excludedFont else defaultFont

            val cellStyle = when {
                isExcluded -> excludedStyle
                row.analyzerIssues.containsUnresolved() || row.scanIssues.containsUnresolved() -> errorStyle
                row.declaredLicenses.isEmpty() && row.detectedLicenses.isEmpty() -> warningStyle
                else -> successStyle
            }

            val scopesText = XSSFRichTextString()

            row.scopes.entries.sortedWith(compareBy({ it.value.isNotEmpty() }, { it.key }))
                    .forEach { (scope, excludes) ->
                        scopesText.append("$scope\n", if (excludes.isNotEmpty()) excludedFont else font)

                        excludes.forEach { exclude ->
                            scopesText.append("  Excluded: ${exclude.reason} - ${exclude.comment}\n", excludedFont)
                        }
                    }

            val scopesLines = row.scopes.size + row.scopes.flatMap { it.value }.size

            sheet.createRow(currentRow).apply {
                createCell(this, 0, row.id.toCoordinates(), font, cellStyle)
                createCell(this, 1, scopesText, cellStyle)
                createCell(this, 2, row.declaredLicenses.joinToString(" \n"), font, cellStyle)
                createCell(this, 3, row.detectedLicenses.joinToString(" \n"), font, cellStyle)
                createCell(this, 4, row.analyzerIssues.joinToString(" \n") { it.description }, font, cellStyle)
                createCell(this, 5, row.scanIssues.joinToString(" \n") { it.description }, font, cellStyle)

                val maxLines = listOf(scopesLines, row.declaredLicenses.size, row.detectedLicenses.size,
                        row.analyzerIssues.size, row.scanIssues.size).max() ?: 1
                heightInPoints = maxLines * getSheet().defaultRowHeightInPoints
            }
            ++currentRow
        }

        sheet.finalize(headerRows, currentRow, defaultColumns + extraColumns.size)
    }

    private fun createHeader(sheet: XSSFSheet, name: String, exclude: ProjectExclude?, file: String, vcsInfo: VcsInfo,
                             extraColumns: List<String>): Int {
        val columns = defaultColumns + extraColumns.size

        sheet.createRow(0).apply {
            val text = exclude?.let { "$name - This project is excluded: ${exclude.reason} - ${exclude.comment}" }
                    ?: name

            CellUtil.createCell(this, 0, "Project:", headerStyle)
            CellUtil.createCell(this, 1, text, headerStyle)
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
            CellUtil.createCell(this, 4, "Analyzer Issues", headerStyle)
            CellUtil.createCell(this, 5, "Scan Issues", headerStyle)

            extraColumns.forEachIndexed { index, column ->
                CellUtil.createCell(this, 6 + index, column, headerStyle)
            }
        }

        return ++rows
    }

    private fun createCell(row: Row, column: Int, value: XSSFRichTextString, style: CellStyle?): Cell? {
        val cell = CellUtil.getCell(row, column)

        cell.setCellValue(value.limit())

        if (style != null) {
            cell.cellStyle = style
        }

        return cell
    }

    private fun createCell(row: Row, column: Int, value: String, font: XSSFFont, cellStyle: CellStyle) =
            createCell(row, column, XSSFRichTextString(value.limit()).apply { applyFont(font) }, cellStyle)
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
private const val MAX_EXCEL_CELL_CONTENT_LENGTH = 32767

private fun RichTextString.limit(maxLength: Int = MAX_EXCEL_CELL_CONTENT_LENGTH) =
        takeIf { it.string == null || it.length() <= maxLength }
                // There is no easy way to get a substring of a RichTextString, so convert to plain text here.
                ?: XSSFRichTextString("${string.take(maxLength - ELLIPSIS.length)}$ELLIPSIS")

private fun String.limit(maxLength: Int = MAX_EXCEL_CELL_CONTENT_LENGTH) =
        takeIf { it.length <= maxLength } ?: "${take(maxLength - ELLIPSIS.length)}$ELLIPSIS"
