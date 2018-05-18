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

import com.here.ort.model.VcsInfo
import com.here.ort.utils.isValidUrl

import java.io.File

class StaticHtmlReporter : TableReporter() {
    override fun generateReport(tabularScanRecord: TabularScanRecord, outputDir: File) {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Scan Report</title>
                <style>
                    body {
                        font-family: "Trebuchet MS", Arial, Helvetica, sans-serif;
                    }

                    table {
                        border-collapse: collapse;
                    }

                    table.dependencies {
                        width: 100%;
                    }

                    th, td {
                        border: 1px solid black;
                        padding: 8px;
                        vertical-align: top;
                    }

                    th {
                        background-color: SteelBlue;
                        color: White;
                        padding-top: 12px;
                        padding-bottom: 12px;
                        text-align: left;
                    }

                    .error {
                        background-color: LightCoral;
                    }

                    .warning {
                        background-color: LightYellow;
                    }

                    .success {
                        background-color: LightBlue;
                    }

                    .data tr:nth-child(even) {
                        background-color: WhiteSmoke;
                    }

                    tr:hover {
                        outline: 2px solid SteelBlue;
                        outline-offset: -1px;
                    }
                </style>
            </head>
            <body>
                <h1>Scan Report</h1>
                ${createContent(tabularScanRecord)}
            </body>
            </html>
            """.trimIndent()

        val outputFile = File(outputDir, "scan-report.html")
        println("Writing static HTML report to '${outputFile.absolutePath}'.")
        outputFile.writeText(html)
    }

    private fun createContent(tabularScanRecord: TabularScanRecord) =
            buildString {
                if (tabularScanRecord.metadata.isNotEmpty()) {
                    append("<h2>Metadata</h2>")
                    append("<table class=\"data\">")
                    tabularScanRecord.metadata.forEach { (key, value) ->
                        append("""
                        <tr>
                            <td>$key</td>
                            <td>${if (value.isValidUrl()) "<a href=\"$value\">$value</a>" else value}</td>
                        </tr>
                        """.trimIndent())
                    }
                    append("</table>")
                }
                append(createTable("Summary", tabularScanRecord.vcsInfo, tabularScanRecord.summary))
                tabularScanRecord.projectDependencies.forEach { project, entry ->
                    append(createTable("${project.id} (${project.definitionFilePath})", project.vcsProcessed, entry))
                }
            }

    private fun createTable(title: String, vcsInfo: VcsInfo, summary: TableReporter.Table) =
            buildString {
                append("""
                    <h2>$title</h2>
                    <h3>VCS Info</h3>
                    <table class="data">
                        <tr>
                            <td>Type</td>
                            <td>${vcsInfo.type}</td>
                        </tr>
                        <tr>
                            <td>URL</td>
                            <td>${vcsInfo.url}</td>
                        </tr>
                        <tr>
                            <td>Path</td>
                            <td>${vcsInfo.path}</td>
                        </tr>
                        <tr>
                            <td>Revision</td>
                            <td>${vcsInfo.revision}</td>
                        </tr>
                    </table>
                    <h3>Dependencies</h3>
                    <table class="dependencies">
                    <tr>
                        <th>Package</th>
                        <th>Scopes</th>
                        <th>Declared Licenses</th>
                        <th>Detected Licenses</th>
                        <th>Analyzer Errors</th>
                        <th>Scanner Errors</th>
                    </tr>
                    """.trimIndent())

                summary.entries.forEach { entry ->
                    val cssClass = when {
                        entry.analyzerErrors.isNotEmpty() || entry.scanErrors.isNotEmpty() -> "error"
                        entry.declaredLicenses.isEmpty() && entry.detectedLicenses.isEmpty() -> "warning"
                        else -> "success"
                    }

                    append("""
                        <tr class="$cssClass">
                            <td>${entry.id}</td>
                            <td>${entry.scopes.joinToString(separator = "<br/>")}</td>
                            <td>${entry.declaredLicenses.joinToString(separator = "<br/>")}</td>
                            <td>${entry.detectedLicenses.joinToString(separator = "<br/>")}</td>
                            <td>${entry.analyzerErrors.joinToString(separator = "<br/>")}</td>
                            <td>${entry.scanErrors.joinToString(separator = "<br/>")}</td>
                        </tr>""".trimIndent())
                }

                append("</table>")
            }
}
