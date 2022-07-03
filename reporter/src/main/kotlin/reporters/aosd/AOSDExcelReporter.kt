/*
 * Copyright (C) 2021 Porsche AG
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
package org.ossreviewtoolkit.reporter.reporters.aosd

import java.io.File
import java.util.Collections
import java.util.Locale
import java.util.SortedSet
import java.util.TreeSet

import org.apache.poi.common.usermodel.HyperlinkType
import org.apache.poi.ss.SpreadsheetVersion
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFFont
import org.apache.poi.xssf.usermodel.XSSFRow
import org.apache.poi.xssf.usermodel.XSSFWorkbook

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.model.licenses.ResolvedCopyrightSource
import org.ossreviewtoolkit.model.licenses.ResolvedLicenseInfo
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.ort.log

class AOSDExcelReporter : Reporter {
    companion object {
        const val WORKSHEET_NAME_LENGTH = 28

        private fun restrictWorksheetName(worksheetName: String) =
            if (worksheetName.length <= WORKSHEET_NAME_LENGTH) worksheetName else worksheetName.substring(
                0,
                WORKSHEET_NAME_LENGTH
            )

        private fun sanitizeWorkbookName(name: String) = name
            .replace('/', '_')
            .replace('\\', '_')
            .replace('*', '_')
            .replace('[', '_')
            .replace(']', '_')
            .replace(':', '_')
            .replace('?', '_')
    }

    override val reporterName = "AOSDExcel"
    private val reportFilename = "aosd-report.xlsx"
    private var tooLongLicenseTexts = mutableMapOf<String, String>()
    private var knownWorksheetNames = mutableMapOf<String, Int>()

    data class AOSDReporterPackage(
        val name: String,
        val version: String,
        val website: String,
        val licenseInfo: ResolvedLicenseInfo,
        val authors: SortedSet<String>
    )

    data class ProjectInfo(
        val projectId: Identifier,
        val licenseFindings: Map<Identifier, AOSDReporterPackage>
    ) : Comparable<ProjectInfo> {
        override fun compareTo(other: ProjectInfo): Int {
            return projectId.compareTo(other.projectId)
        }
    }

    enum class ExcelColumn {
        ID,
        SOFTWARE_NAME,
        SOFTWARE_VERSION,
        SOFTWARE_DOWNLOAD_LINK,
        LICENSE_SPDX,
        LICENSE_TEXT,
        USE_LINKAGE,
        USE_MODIFICATION,
        USE_START,
        USE_END,
        USE_COMMENT
    }

    override fun generateReport(input: ReporterInput, outputDir: File, options: Map<String, String>): List<File> {
        val workbook = XSSFWorkbook()

        getAllLicenseFindings(input)
            .forEach { projectInfo ->
                val recordsSheet = workbook.createSheet(generateWorksheetName(projectInfo.projectId))
                val licenseFindings: Map<Identifier, AOSDReporterPackage> = projectInfo.licenseFindings

                var rowNum = 0

                licenseFindings.forEach { (identifier, aosdReporterPackage) ->
                    val row = recordsSheet.createRow(rowNum + 1) // + 1 because of headers

                    row.createIDCell(rowNum)
                    row.createSoftwareNameCell(identifier)
                    row.createSoftwareVersionCell(identifier)
                    input.ortResult.analyzer?.also { analyzerRun ->
                        analyzerRun.result.packages.find { it.pkg.id == identifier }?.also { result ->
                            row.createSoftwareLinkCell(result.pkg, workbook)
                        }
                    }
                    row.createSPDXCell(aosdReporterPackage, workbook)
                    row.createLicenseTextCell(aosdReporterPackage, workbook, input)?.also {
                        it.also { tooLongLicenseTexts["${identifier.name}-${identifier.version}"] = it }
                    }
                    row.createUsageLinkageCell()
                    row.createUseModificationCell()

                    rowNum++
                }

                // After everything is filled we create the first row and autosize the columns
                val headerRow = recordsSheet.createRow(0)

                ExcelColumn.values().forEach {
                    val cell = headerRow.createCell(it.ordinal)
                    cell.setCellValue(it.name.lowercase())
                    recordsSheet.autoSizeColumn(it.ordinal)
                }
            }

        val outputFile = outputDir.resolve(reportFilename)

        outputFile.outputStream().use {
            workbook.write(it)
        }

        tooLongLicenseTexts.forEach { (key, value) ->
            fun extractName(path: String): String {
                val lindex = path.lastIndexOf(File.separatorChar)

                if (lindex > 0) {
                    return path.substring(lindex + 1)
                }

                return path
            }

            val licenseOutputFile = outputDir.resolve(extractName("$key-license-text.txt"))

            licenseOutputFile.writeText(value)
        }

        return listOf(outputFile)
    }

    private fun generateWorksheetName(projectId: Identifier): String {
        val projectName = restrictWorksheetName(sanitizeWorkbookName(projectId.name))

        val counter = knownWorksheetNames.compute(projectName) { _, v -> v?.let { it + 1 } ?: 1 }

        return String.format(Locale.GERMAN, "%s %02d", projectName, counter)
    }

    private fun getAllLicenseFindings(input: ReporterInput): SortedSet<ProjectInfo> {
        val projectInfoSet = TreeSet<ProjectInfo>()

        input.ortResult.getProjects()
            .forEach { project ->
                val dependencies = input.ortResult.collectDependencies(project.id)
                    .filter { !input.ortResult.isExcluded(it) }
                    .filter { input.ortResult.isPackage(it) }
                    .sortedBy { it.excelName() }

                if (dependencies.isNotEmpty()) {
                    val licenseFindings = dependencies.associateWith { id ->
                        // We know that a package exists for the reference.
                        log.info("Associate depencency for package $id")

                        val pkg = input.ortResult.getPackage(id)!!.pkg
                        val pkgUrl = pkg.homepageUrl.takeUnless { it.isEmpty() } ?: "https://github.com/404"
                        val licenseInfo = input.licenseInfoResolver.resolveLicenseInfo(pkg.id).filterExcluded()
                        val licenses = licenseInfo.filter(LicenseView.CONCLUDED_OR_DETECTED).filterExcluded()

                        AOSDReporterPackage(
                            name = pkg.id.name,
                            version = pkg.id.version,
                            website = pkgUrl,
                            licenseInfo = licenses,
                            authors = pkg.authors,
                        )
                    }

                    projectInfoSet.add(
                        ProjectInfo(
                            project.id, licenseFindings
                        )
                    )
                }
            }

        return Collections.unmodifiableSortedSet(projectInfoSet)
    }

    private fun XSSFRow.createIDCell(value: Int) {
        val cell = createCell(ExcelColumn.ID.ordinal)

        cell.setCellValue(value.toString())
    }

    private fun XSSFRow.createUseModificationCell() {
        val cell = createCell(ExcelColumn.USE_MODIFICATION.ordinal)

        cell.setCellValue(false)
    }

    private fun XSSFRow.createUsageLinkageCell() {
        val cell = createCell(ExcelColumn.USE_LINKAGE.ordinal)

        cell.setCellValue("yes, dynamically")
    }

    private fun XSSFRow.createLicenseTextCell(
        reporterPackage: AOSDReporterPackage,
        workbook: XSSFWorkbook,
        input: ReporterInput
    ): String? {
        val cell = createCell(ExcelColumn.LICENSE_TEXT.ordinal)
        val cellStyle = workbook.createCellStyle()
        var licenseString = ""

        cellStyle.wrapText = true
        cell.cellStyle = cellStyle

        reporterPackage.licenseInfo.licenses.forEach { it ->
            val simpleLicense = it.license.simpleLicense()
            val curatedCopyrightFindings = it.getResolvedCopyrights()
                .flatMap { it.findings }
                .filter { it.copyrightSource == ResolvedCopyrightSource.PROVIDED_BY_CURATION }
                .mapTo(mutableSetOf()) { it.statement }

            licenseString += "#$simpleLicense"
            licenseString += "\n\n"
            licenseString += if (curatedCopyrightFindings.isNotEmpty())
                curatedCopyrightFindings.joinToString("\n") else it.getResolvedCopyrights()
                .flatMap { it.findings }
                .filter { it.copyrightSource == ResolvedCopyrightSource.DETERMINED_BY_SCANNER }
                .mapTo(mutableSetOf()) { it.statement }
                .joinToString("\n")
            licenseString += "\n\n"

            if (input.licenseTextProvider.hasLicenseText(simpleLicense)) {
                licenseString += input.licenseTextProvider.getLicenseText(simpleLicense)
                licenseString += "\n\n"
            }

            if (reporterPackage.authors.isNotEmpty()) {
                licenseString += "#Authors:\n"
                licenseString += reporterPackage.authors.joinToString("\n")
                licenseString += "\n\n"
            }
        }

        licenseString = licenseString.trim()

        if (licenseString.length < SpreadsheetVersion.EXCEL2007.maxTextLength) {
            cell.setCellValue(licenseString)
        } else {
            return licenseString
        }

        return null
    }

    private fun XSSFRow.createSPDXCell(reporterPackage: AOSDReporterPackage, workbook: XSSFWorkbook) {
        val cell = createCell(ExcelColumn.LICENSE_SPDX.ordinal)
        if (reporterPackage.licenseInfo.licenses.isEmpty() || reporterPackage.licenseInfo.licenses.count() > 1) {
            val cellStyle = workbook.createCellStyle()
            cellStyle.fillPattern = FillPatternType.SOLID_FOREGROUND
            cellStyle.fillForegroundColor = IndexedColors.RED.index
            cell.cellStyle = cellStyle
        }
        cell.setCellValue(reporterPackage.licenseInfo.licenses.joinToString(", ") { it.license.simpleLicense() })
    }

    private fun XSSFRow.createSoftwareNameCell(component: Identifier) {
        val cell = createCell(ExcelColumn.SOFTWARE_NAME.ordinal)
        cell.setCellValue(component.excelName())
    }

    private fun Identifier.excelName(): String {
        if (namespace.isEmpty()) {
            return name
        }

        return listOf(namespace, name).joinToString("/")
    }

    private fun XSSFRow.createSoftwareVersionCell(component: Identifier) {
        val cell = createCell(ExcelColumn.SOFTWARE_VERSION.ordinal)
        cell.setCellValue(component.version)
    }

    private fun XSSFRow.createSoftwareLinkCell(pkg: org.ossreviewtoolkit.model.Package, workbook: XSSFWorkbook) {
        val createHelper = workbook.creationHelper
        val cell = createCell(ExcelColumn.SOFTWARE_DOWNLOAD_LINK.ordinal)
        val cellStyle = workbook.createCellStyle()
        val font = workbook.createFont()
        var url = ""

        font.underline = XSSFFont.U_SINGLE
        font.color = IndexedColors.BLUE.index
        cellStyle.setFont(font)

        for (newUrl in listOf(pkg.homepageUrl, pkg.sourceArtifact.url, pkg.binaryArtifact.url)) {
            url = newUrl
            if (url.isNotEmpty()) break
        }

        val link = createHelper.createHyperlink(HyperlinkType.URL)

        link.address = url
        cell.hyperlink = link
        cell.setCellValue(url)
        cell.cellStyle = cellStyle
    }
}
