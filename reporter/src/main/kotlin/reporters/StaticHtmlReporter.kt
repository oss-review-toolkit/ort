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

import com.here.ort.model.OrtIssue
import com.here.ort.model.OrtResult
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.CopyrightGarbage
import com.here.ort.model.config.ScopeExclude
import com.here.ort.reporter.Reporter
import com.here.ort.reporter.ResolutionProvider
import com.here.ort.reporter.reporters.ReportTableModel.ErrorTable
import com.here.ort.reporter.reporters.ReportTableModel.ProjectTable

import com.here.ort.utils.isValidUrl
import com.here.ort.utils.log

import java.io.File
import java.util.SortedMap

class StaticHtmlReporter : Reporter() {
    override fun generateReport(
            ortResult: OrtResult,
            resolutionProvider: ResolutionProvider,
            copyrightGarbage: CopyrightGarbage,
            outputDir: File,
            postProcessingScript: String?
    ): File {
        val tabularScanRecord = ReportTableModelMapper(resolutionProvider).mapToReportTableModel(ortResult)

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

                  ul {
                      list-style: none;
                      margin: 0;
                      padding: 0;
                  }

                  #report-container {
                    background-color: #fff;
                    border: 1px solid rgba(34,36,38,.15);
                    border-radius: .28rem;
                    padding: 0em 1em 0.5em 1em;
                    margin: 1em 2em 1em 2em;
                  }

                  .ort-report-label {
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

                  .ort-report-metadata {
                    font-size: 12px;
                    border-spacing: 0;
                    table-layout:fixed;
                  }

                  .ort-report-metadata td {
                    border-bottom: 1px solid rgba(34,36,38,.15);
                    overflow: hidden;
                    padding: 5px 20px 5px 0px;
                    text-overflow: ellipsis;
                    word-wrap: break-word;
                  }

                  .ort-report-metadata tr:first-child td {
                    border-top: 1px solid rgba(34,36,38,.15);
                  }

                  .ort-report-table {
                    border-spacing: 0;
                    width: 100%;
                    table-layout:fixed;
                  }

                  .ort-report-table th {
                    background-color: #f9fafb;
                    padding: 5px 5px 5px .8em !important;
                    text-align: left;
                  }

                  .ort-report-table th:first-child {
                    border-top-left-radius: .28rem;
                    border-left: 1px solid rgba(34,36,38,.15);
                    border-top: 1px solid rgba(34,36,38,.15);
                  }

                  .ort-report-table th {
                    border-left: 1px solid rgba(34,36,38,.15);
                    border-top: 1px solid rgba(34,36,38,.15);
                    overflow: hidden;
                    white-space: nowrap;
                    text-overflow: ellipsis;
                  }

                  .ort-report-table th:last-child {
                    border-top-right-radius: .28rem;
                    border-right: 1px solid rgba(34,36,38,.15);
                    border-top: 1px solid rgba(34,36,38,.15);
                  }

                  .ort-report-table {
                    overflow: hidden;
                    text-overflow: ellipsis;
                  }

                  .ort-report-table td {
                    border-left: 1px solid rgba(34,36,38,.15);
                    border-top: 1px solid rgba(34,36,38,.15);
                    padding: 8px;
                    vertical-align: top;
                    overflow: hidden; 
                    text-overflow: ellipsis; 
                    word-wrap: break-word;
                  }

                  .ort-report-table td:last-child {
                    border-right: 1px solid rgba(34,36,38,.15);
                  }

                  .ort-report-table tr:last-child td {
                    border-bottom: 1px solid rgba(34,36,38,.15);
                  }

                  .ort-report-table tr:last-child td:first-child {
                    border-bottom-left-radius: .28rem;
                  }

                  .ort-report-table tr:last-child td:last-child {
                    border-bottom-right-radius: .28rem;
                  }

                  .ort-report-table tr.ort-error {
                    background: #fff6f6;
                    color: #9f3a38;
                  }

                  .ort-report-table tr.ort-warning {
                    background: #fffaf3;
                    color: #573a08;
                  }

                  .ort-report-table tr.ort-ok {
                    background: #fcfff5;
                    color: #2c662d;
                  }

                  .ort-report-table tr:hover {
                    background: rgba(34,36,38,.15);
                  }

                  .ort-excluded {
                    filter: opacity(50%);
                  }

                  .ort-reason {
                      border-radius: 3px;
                      background: #EEE;
                      padding: 2px;
                      font-size: 12px;
                      display: inline;
                  }

                  @media all and (max-width: 1000px) {
                      .ort-report-table th:nth-child(2), .ort-report-table td:nth-child(2) {
                          display:none;
                          width:0;
                          height:0;
                          opacity:0;
                          visibility: collapse;
                      }
                  }

                  @media all and (max-width: 900px) {
                      .ort-report-table th:nth-child(3), .ort-report-table td:nth-child(3) {
                          display:none;
                          width:0;
                          height:0;
                          opacity:0;
                          visibility: collapse;
                      }
                  }

                  @media all and (max-width: 800px) {
                      .ort-report-table th:nth-child(5),
                      .ort-report-table td:nth-child(5),
                      .ort-report-table th:nth-child(6),
                      .ort-report-table td:nth-child(6) {
                          display:none;
                          width:0;
                          height:0;
                          opacity:0;
                          visibility: collapse;
                      }

                      .ort-report-table th:nth-child(4) {
                        border-top-right-radius: .28rem;
                        border-right: 1px solid rgba(34,36,38,.15);
                      }

                      .ort-report-table td:nth-child(4) {
                        border-right: 1px solid rgba(34,36,38,.15);
                      }

                      .ort-report-table tr:last-child td:nth-child(4) {
                        border-bottom-right-radius: .28rem;
                      }
                  }

                  @media all and (max-width: 500px) {
                      .ort-report-table th:nth-child(4),
                      .ort-report-table td:nth-child(4) {
                          display:none;
                          width:0;
                          height:0;
                          opacity:0;
                          visibility: collapse;
                      }

                      .ort-report-table th:first-child {
                        border-top-right-radius: .28rem;
                        border-right: 1px solid rgba(34,36,38,.15);
                      }

                      .ort-report-table td:first-child {
                        border-right: 1px solid rgba(34,36,38,.15);
                      }

                      .ort-report-table tr:last-child td:first-child {
                        border-bottom-right-radius: .28rem;
                      }
                  }
                </style>
            </head>
            <body>
            <div id="report-container">
                  <div class="ort-report-label">Scan Report</div>
                ${createContent(tabularScanRecord)}
                </div>
            </body>
            </html>

            """.trimIndent()

        val outputFile = File(outputDir, "scan-report.html")

        log.info { "Writing static HTML report to '${outputFile.absolutePath}'." }

        outputFile.writeText(html)

        return outputFile
    }

    private fun createContent(tabularScanRecord: ReportTableModel) =
            buildString {
                if (tabularScanRecord.metadata.isNotEmpty()) {
                    append("<h2>Metadata</h2>")
                    append("<table class=\"ort-report-metadata\"><tbody>")
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

                val numberOfErrors = tabularScanRecord.errorSummary.rows.count()

                append("<h2>Index</h2>")
                append("<ul>")
                tabularScanRecord.evaluatorErrors?.let { evaluatorErrors ->
                    append("<li><a href=\"#license-check-results\">" +
                            "License Check Results (${evaluatorErrors.size} errors)</a></li>")
                }
                if (numberOfErrors > 0) {
                    append("<li><a href=\"#error-summary\">Error Summary ($numberOfErrors)</a></li>")
                }
                tabularScanRecord.projectDependencies.forEach { (project, projectTable) ->
                    append("<li><a href=\"#${project.id}\">${project.id}")
                    projectTable.exclude?.let { exclude ->
                        append(" <div class=\"ort-reason\">Excluded: ${exclude.reason} - ${exclude.comment}</div>")
                    }
                    append("</a></li>")
                }
                append("</ul>")

                tabularScanRecord.evaluatorErrors?.let { evaluatorErrors ->
                    append(createEvaluatorTable(evaluatorErrors))
                }

                if (numberOfErrors > 0) {
                    append(createErrorTable("Error Summary ($numberOfErrors)", tabularScanRecord.errorSummary,
                            "error-summary"))
                }

                tabularScanRecord.projectDependencies.forEach { project, table ->
                    append(createTable("${project.id} (${project.definitionFilePath})", project.vcsProcessed, table,
                            project.id.toString()))
                }
            }

    private fun createEvaluatorTable(evaluatorErrors: List<OrtIssue>) =
            buildString {
                append("<h2><a id=\"license-check-results\"></a>License Check Results</h2>")

                if (evaluatorErrors.isEmpty()) {
                    append("No issues found.")
                } else {
                    append("""
                        <table class="ort-report-table ort-violations">
                        <thead>
                        <tr>
                            <th>Source</th>
                            <th>Error</th>
                        </tr>
                        </thead>
                        <tbody>
                        """.trimIndent())

                    evaluatorErrors.forEach { error ->
                        append("""
                            <tr class="ort-error">
                                <td>${error.source}</td>
                                <td>${error.message}</td>
                            </tr>
                            """.trimIndent())
                    }

                    append("""
                        </tbody>
                        </table>
                        """.trimIndent())
                }
            }

    private fun createErrorTable(title: String, errors: ErrorTable, anchor: String) =
            buildString {
                append("""
                    <h2><a id="$anchor"></a>$title</h2>
                    <p>Errors from excluded components are not shown in this summary.</p>
                    <h3>Packages</h3>
                    <table class="ort-report-table ort-errors">
                    <thead>
                    <tr>
                        <th>Package</th>
                        <th>Analyzer Errors</th>
                        <th>Scanner Errors</th>
                    </tr>
                    </thead>
                    <tbody>
                    """.trimIndent())

                errors.rows.forEach { row ->
                    val cssClass = "ort-error"

                    append("""
                        <tr class="$cssClass">
                            <td>${row.id}</td>
                            <td>""".trimIndent())

                    row.analyzerErrors.forEach { id, errors ->
                        append("""
                                <a href="#$id">$id</a>
                                <ul>
                                    ${errors.joinToString("\n") {
                            "<li>${it.description.replace("\n", "<br/>")}</li>"
                        }}
                                </ul>""".trimIndent())
                    }

                    append("""
                            </td>
                            <td>""".trimIndent())

                    row.scanErrors.forEach { id, errors ->
                        append("""
                                <a href="#$id">$id</a>
                                <ul>
                                    ${errors.joinToString("\n") {
                            "<li>${it.description.replace("\n", "<br/>")}</li>"
                        }}
                                </ul>""".trimIndent())
                    }

                    append("""
                            </td>
                        </tr>""".trimIndent())
                }

                append("</tbody></table>")
            }

    private fun createTable(title: String, vcsInfo: VcsInfo?, summary: ProjectTable, anchor: String) =
            buildString {
                val excludedClass = if (summary.exclude != null) " ort-excluded" else ""

                append("<h2><a id=\"$anchor\"></a>$title</h2>")

                summary.exclude?.let { exclude ->
                    append("""
                        <h3>Project is Excluded</h3>
                        <p>
                            The project is excluded for the following reason:<br/>
                            <div class="reason">${exclude.reason} - ${exclude.comment}</div>
                        </p>""".trimIndent())
                }

                if (vcsInfo != null) {
                    append("""
                        <h3 class="$excludedClass">VCS Information</h3>
                        <table class="ort-report-metadata$excludedClass">
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
                    <h3 class="$excludedClass">Packages</h3>
                    <table class="ort-report-table ort-packages$excludedClass">
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

                summary.rows.forEach { row ->
                    // Only mark the row as excluded if all scopes the dependency appears in are excluded.
                    val rowExcludedClass = if (row.scopes.isNotEmpty() && row.scopes.all { it.value.isNotEmpty() })
                        " ort-excluded" else ""

                    val cssClass = when {
                        row.analyzerErrors.containsUnresolved() || row.scanErrors.containsUnresolved() -> "ort-error"
                        row.declaredLicenses.isEmpty() && row.detectedLicenses.isEmpty() -> "ort-warning"
                        else -> "ort-success"
                    }

                    append("""
                        <tr class="$cssClass$rowExcludedClass">
                            <td>${row.id}</td>
                            <td>${formatScopes(row.scopes)}</td>
                            <td><ul>
                                ${row.declaredLicenses.joinToString("\n") {
                                    "<li>$it</li>"
                                }}
                            </ul></td>
                            <td><ul>
                                ${row.detectedLicenses.joinToString("\n") {
                                    "<li>$it</li>"
                                }}
                            </ul></td>
                            <td><ul>
                                ${row.analyzerErrors.joinToString("\n") {
                                    "<li>${it.description.replace("\n", "<br/>")}</li>"
                                }}
                            </ul></td>
                            <td><ul>
                                ${row.scanErrors.joinToString("\n") {
                                    "<li>${it.description.replace("\n", "<br/>")}</li>"
                                }}
                            </ul></td>
                        </tr>""".trimIndent())
                }

                append("</tbody></table>")
            }

    private fun formatScopes(scopes: SortedMap<String, List<ScopeExclude>>) =
            scopes.entries.asSequence().sortedWith(compareBy({ it.value.isNotEmpty() }, { it.key }))
                    .joinToString(separator = "", prefix = "<ul>", postfix = "</ul>") {
                        val excludedClass = if (it.value.isNotEmpty()) "ort-excluded" else ""
                        "<li class=\"$excludedClass\">${it.key}${formatScopeExcludes(it.value)}</li>"
                    }

    private fun formatScopeExcludes(scopeExcludes: List<ScopeExclude>) =
            buildString {
                if (scopeExcludes.isNotEmpty()) {
                    append(" <div class=\"ort-reason\">Excluded: ")
                    append(scopeExcludes.joinToString { "${it.reason} - ${it.comment}" })
                    append("</div>")
                }
            }
}
