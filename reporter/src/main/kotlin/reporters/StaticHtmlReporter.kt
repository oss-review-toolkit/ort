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
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Scan Report</title>
                <style>
                  body {
                    background-color: #f7f7f7;
                    font-family: "HelveticaNeue-Light", "Helvetica Neue Light", "Helvetica Neue", Helvetica, Arial,
                                 "Lucida Grande", sans-serif;
                    font-weight: 300;
                    font-size: 14px;
                  }

                  a, a:visited {
                    color: black;
                  }

                  #report-container {
                    background-color: #fff;
                    border: 1px solid rgba(34,36,38,.15);
                    border-radius: .28rem;
                    padding: 0em 1em 0.5em 1em;
                    margin: 1em 2em 1em 2em;
                  }

                  .report-label {
                    background-color: #f9fafb;
                    border-left: 1px solid rgba(34,36,38,.15);
                    border-right: 1px solid rgba(34,36,38,.15);
                    border-bottom: 1px solid rgba(34,36,38,.15);
                    border-top: none;
                    border-bottom-left-radius: .28rem;
                    border-bottom-right-radius: .28rem;
                    border-collapse: separate;
                    border-spacing: 0;
                    color: rgba(34,36,38,.7);
                    font-size: 18px;
                    font-weight: 700;
                    padding: 0.4em 0.4em 0.4em 0.4em;
                    margin-bottom: 1em;
                    top: -10px;
                    width: 110px;
                  }

                  .report-metadata {
                    font-size: 12px;
                    border-spacing: 0;
                    table-layout:fixed;
                  }

                  .report-metadata tr {
                  }

                  .report-metadata td {
                    border-bottom: 1px solid rgba(34,36,38,.15);
                    overflow: hidden; 
                    padding: 5px 20px 5px 0px;
                    text-overflow: ellipsis; 
                    word-wrap: break-word;
                  }

                  .report-metadata tr:first-child td {
                    border-top: 1px solid rgba(34,36,38,.15);
                  }

                  .report-packages {
                    border-spacing: 0;
                    width: 100%;
                    table-layout:fixed;
                  }

                  .report-packages th {
                    background-color: #f9fafb;
                    padding: 5px 5px 5px .8em !important;
                    text-align: left;
                  }

                  .report-packages th:first-child {
                    border-top-left-radius: .28rem;
                    border-left: 1px solid rgba(34,36,38,.15);
                    border-top: 1px solid rgba(34,36,38,.15);
                  }

                  .report-packages th {
                    border-left: 1px solid rgba(34,36,38,.15);
                    border-top: 1px solid rgba(34,36,38,.15);
                    overflow: hidden;
                    white-space: nowrap;
                    text-overflow: ellipsis;
                  }

                  .report-packages th:last-child {
                    border-top-right-radius: .28rem;
                    border-right: 1px solid rgba(34,36,38,.15);
                    border-top: 1px solid rgba(34,36,38,.15);
                  }

                  .report-packages {
                    overflow: hidden;
                    text-overflow: ellipsis;
                  }

                  .report-packages td {
                    border-left: 1px solid rgba(34,36,38,.15);
                    border-top: 1px solid rgba(34,36,38,.15);
                    padding: 8px;
                    vertical-align: top;
                    overflow: hidden; 
                    text-overflow: ellipsis; 
                    word-wrap: break-word;
                  }

                  .report-packages td:last-child {
                    border-right: 1px solid rgba(34,36,38,.15);
                  }

                  .report-packages tr:last-child td {
                    border-bottom: 1px solid rgba(34,36,38,.15);
                  }

                  .report-packages tr:last-child td:first-child {
                    border-bottom-left-radius: .28rem;
                  }

                  .report-packages tr:last-child td:last-child {
                    border-bottom-right-radius: .28rem;
                  }

                  .report-packages tr.error {
                    background: #fff6f6;
                    color: #9f3a38;
                  }

                  .report-packages tr.warning {
                    background: #fffaf3;
                    color: #573a08;
                  }

                  .report-packages tr.ok {
                    background: #fcfff5;
                    color: #2c662d;
                  }

                  .report-packages tr:hover {
                    background: rgba(34,36,38,.15);
                  }

                  @media all and (max-width: 1000px) {
                      .report-packages th:nth-child(2), .report-packages td:nth-child(2) {
                          display:none;
                          width:0;
                          height:0;
                          opacity:0;
                          visibility: collapse;
                      }
                  }

                  @media all and (max-width: 900px) {
                      .report-packages th:nth-child(3), .report-packages td:nth-child(3) {
                          display:none;
                          width:0;
                          height:0;
                          opacity:0;
                          visibility: collapse;
                      } 
                  }

                  @media all and (max-width: 800px) {
                      .report-packages th:nth-child(5),
                      .report-packages td:nth-child(5),
                      .report-packages th:nth-child(6),
                      .report-packages td:nth-child(6) {
                          display:none;
                          width:0;
                          height:0;
                          opacity:0;
                          visibility: collapse;
                      }

                      .report-packages th:nth-child(4) {
                        border-top-right-radius: .28rem;
                        border-right: 1px solid rgba(34,36,38,.15);
                      }

                      .report-packages td:nth-child(4) {
                        border-right: 1px solid rgba(34,36,38,.15);
                      }

                      .report-packages tr:last-child td:nth-child(4) {
                        border-bottom-right-radius: .28rem;
                      }
                  }

                  @media all and (max-width: 500px) {
                      .report-packages th:nth-child(4),
                      .report-packages td:nth-child(4) {
                          display:none;
                          width:0;
                          height:0;
                          opacity:0;
                          visibility: collapse;
                      }

                      .report-packages th:first-child {
                        border-top-right-radius: .28rem;
                        border-right: 1px solid rgba(34,36,38,.15);
                      }

                      .report-packages td:first-child {
                        border-right: 1px solid rgba(34,36,38,.15);
                      }

                      .report-packages tr:last-child td:first-child {
                        border-bottom-right-radius: .28rem;
                      }
                  }
                </style>
            </head>
            <body>
                <div id="report-container">
                  <div class="report-label">Scan Report</div>
                ${createContent(tabularScanRecord)}
                </div>
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
                    append("<table class=\"report-metadata\"><tbody>")
                    tabularScanRecord.metadata.forEach { (key, value) ->
                        append("""
                        <tr>
                            <td>$key</td>
                            <td>${if (value.isValidUrl()) "<a href=\"$value\">$value</a>" else value}</td>
                        </tr>
                        """.trimIndent())
                    }
                    append("</tbody></table>")
                }

                append("<h2>Index</h2>")
                append("<ul>")
                append("<li><a href=\"#error-summary\">Error Summary</a></li>")
                append("<li><a href=\"#summary\">Summary</a></li>")
                tabularScanRecord.projectDependencies.keys.forEachIndexed { index, project ->
                    append("<li><a href=\"#$index\">${project.id}</a></li>")
                }
                append("</ul>")

                append(createTable("Error Summary", null, tabularScanRecord.errorSummary,
                        "error-summary"))
                append(createTable("Summary", tabularScanRecord.vcsInfo, tabularScanRecord.summary, "summary"))

                var index = 0
                tabularScanRecord.projectDependencies.forEach { project, entry ->
                    append(createTable("${project.id} (${project.definitionFilePath})", project.vcsProcessed, entry,
                            index.toString()))
                    ++index
                }
            }

    private fun createTable(title: String, vcsInfo: VcsInfo?, summary: TableReporter.Table, anchor: String) =
            buildString {
                append("<h2><a id=\"$anchor\"></a>$title</h2>")

                if (vcsInfo != null) {
                    append("""
                        <h3>VCS Information</h3>
                        <table class="report-metadata">
                        <tbody>
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
                        </tbody>
                        </table>""".trimIndent())
                }

                append("""
                    <h3>Packages</h3>
                    <table class="report-packages">
                    <thead>
                    <tr>
                        <th>Package</th>
                        <th>Scopes</th>
                        <th>Declared Licenses</th>
                        <th>Detected Licenses</th>
                        <th>Analyzer Errors</th>
                        <th>Scanner Errors</th>
                    </tr>
                    </thead>
                    <tbody>
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
                            <td><ul>
                                ${entry.analyzerErrors.joinToString(separator = "\n") {
                                    "<li>${it.replace("\n", "<br/>")}</li>"
                                }}
                            </ul></td>
                            <td><ul>
                                ${entry.scanErrors.joinToString(separator = "\n") {
                                    "<li>${it.replace("\n", "<br/>")}</li>"
                                }}
                            </ul></td>
                        </tr>""".trimIndent())
                }

                append("</tbody></table>")
            }
}
