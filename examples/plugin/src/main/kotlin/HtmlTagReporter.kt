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

package com.here.ort.examples.plugin

import com.here.ort.model.OrtResult
import com.here.ort.model.VcsInfo
import com.here.ort.reporter.Reporter

import java.io.File

class HtmlTagReporter : Reporter() {
    override fun generateReport(ortResult: OrtResult, outputDir: File) {
        val outputFile = File(outputDir, "html-tags.html")

        val allHtmlTags = mutableMapOf<String, Int>()
        ortResult.scanner?.results?.scanResults?.forEach { container ->
            container.results.forEach { scanResult ->
                @Suppress("UNCHECKED_CAST")
                scanResult.summary.data["htmlTags"]?.let {
                    val htmlTags = it as Map<String, Int>
                    htmlTags.forEach { extension, count ->
                        allHtmlTags[extension] = allHtmlTags.getOrDefault(extension, 0) + count
                    }
                }
            }
        }

        val html = generateHtml(ortResult.repository.vcsProcessed, allHtmlTags)

        outputFile.writeText(html)
    }

    private fun generateHtml(vcs: VcsInfo, fileExtensions: Map<String, Int>): String {
        var html = """
            <html>
            <head>
            <style>
            table {
                border-collapse: collapse;
                border: 1px solid Gainsboro;

            }
            tr:nth-child(even) {
                background-color: WhiteSmoke;
            }
            tr:hover {
                background-color: Silver;
            }
            th {
                text-align: left;
            }
            td {
                text-align: left;
            }
            </style>
            </head>
            <body>
            <h1>HTML Tags Summary</h1>
            <table>
            <tr><th>Tag</th><th>Count</th></tr>
            <p>A summary of the HTML tags found in '${vcs.url}' revision '${vcs.revision}' and all linked HTML pages,
            sorted by number of occurrences.</p>
            """.trimIndent()

        fileExtensions.toList().sortedBy { (_, count) ->
            -count
        }.toMap().forEach { extension, count ->
            html += "<tr><td>$extension</td><td>$count</td></tr>"
        }

        html += "</table></body></html>"

        return html
    }
}
