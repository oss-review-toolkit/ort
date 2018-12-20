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

import com.here.ort.model.OrtResult
import com.here.ort.model.Project
import com.here.ort.model.config.CopyrightGarbage
import com.here.ort.reporter.Reporter
import com.here.ort.reporter.ResolutionProvider
import com.here.ort.reporter.reporters.ReportTableModel.ErrorTable
import com.here.ort.reporter.reporters.ReportTableModel.ProjectTable
import com.here.ort.reporter.reporters.ReportTableModel.ResolvableIssue
import com.here.ort.utils.isValidUrl
import com.here.ort.utils.log
import com.here.ort.utils.normalizeLineBreaks

import java.io.File

import javax.xml.parsers.DocumentBuilderFactory

import kotlinx.html.*
import kotlinx.html.dom.*

class StaticHtmlReporter : Reporter() {

    private val css = """

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

        .ort-excluded {
          filter: opacity(50%);
        }

        table.ort-excluded tr.ort-excluded {
          filter: opacity(100%);
        }

        table tr.ort-excluded td li.ort-excluded {
          filter: opacity(100%);
        }

        table.ort-excluded tr.ort-excluded td li.ort-excluded {
          filter: opacity(100%);
        }

        .ort-report-table {
          border-spacing: 0;
          overflow: hidden;
          table-layout:fixed;
          text-overflow: ellipsis;
          width: 100%;
        }

        .ort-report-table tr:hover {
          background: rgba(34,36,38,.15);
        }

        .ort-report-table tr.ort-error {
          background: #fff6f6;
          color: #9f3a38;
        }

        .ort-report-table tr.ort-warning {
          background: #fffaf3;
          color: #573a08;
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

        .ort-report-table th {
          background-color: #f9fafb;
          border-left: 1px solid rgba(34,36,38,.15);
          border-top: 1px solid rgba(34,36,38,.15);
          overflow: hidden;
          padding: 5px 5px 5px .8em !important;
          text-align: left;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .ort-report-table th:first-child {
          border-top-left-radius: .28rem;
          border-left: 1px solid rgba(34,36,38,.15);
          border-top: 1px solid rgba(34,36,38,.15);
        }

        .ort-report-table th:last-child {
          border-top-right-radius: .28rem;
          border-right: 1px solid rgba(34,36,38,.15);
          border-top: 1px solid rgba(34,36,38,.15);
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

        .ort-report-table td li div.ort-reason {
            border-radius: 3px;
            background: #EEE;
            padding: 2px;
            font-size: 12px;
            display: inline;
        }

        .ort-report-table td:last-child {
          border-right: 1px solid rgba(34,36,38,.15);
        }

        .ort-report-table.ort-violations tr.ort-resolved {
          background: #fcfff5;
          color: #2c662d;
        }

        .ort-report-table.ort-packages tr.ort-error {
          color: black;
        }

        .ort-report-table.ort-packages tr.ort-error td:nth-child(5),
        .ort-report-table.ort-packages tr.ort-error td:nth-child(6) {
          color: #9f3a38;
        }

        .ort-report-table li.ort-resolved {
          color: #2c662d;
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

        """.trimIndent()

    override fun generateReport(
            ortResult: OrtResult,
            resolutionProvider: ResolutionProvider,
            copyrightGarbage: CopyrightGarbage,
            outputDir: File,
            postProcessingScript: String?
    ): File {
        val tabularScanRecord = ReportTableModelMapper(resolutionProvider).mapToReportTableModel(ortResult)
        val html = renderHtml(tabularScanRecord)

        val outputFile = File(outputDir, "scan-report.html")

        log.info { "Writing static HTML report to '${outputFile.absolutePath}'." }

        outputFile.writeText(html)

        return outputFile
    }

    private fun renderHtml(reportTableModel: ReportTableModel): String {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()

        document.append.html {
            lang = "en"

            head {
                meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
                title("Scan Report")
                style {
                    unsafe {
                        +css
                    }
                }
            }
            body {
                div {
                    id = "report-container"

                    div("ort-report-label") {
                        +"Scan Report"
                    }

                    if (reportTableModel.metadata.isNotEmpty()) {
                        metadataTable(reportTableModel.metadata)
                    }

                    index(reportTableModel)

                    reportTableModel.evaluatorErrors?.let {
                        evaluatorTable(it)
                    }

                    if (reportTableModel.errorSummary.rows.count() > 0) {
                        errorTable(reportTableModel.errorSummary)
                    }

                    reportTableModel.projectDependencies.forEach { project, table ->
                        projectTable(project, table)
                    }
                }
            }
        }

        return document.serialize().normalizeLineBreaks()
    }

    private fun DIV.metadataTable(metadata: Map<String, String>) {
        h2 { +"Metadata" }
        table("ort-report-metadata") {
            tbody { metadata.forEach { key, value -> metadataRow(key, value) } }
        }
    }

    private fun TBODY.metadataRow(key: String, value: String) {
        tr {
            td { +key }
            td { if (value.isValidUrl()) a(value) { +value } else +value }
        }
    }

    private fun DIV.index(reportTableModel: ReportTableModel) {
        h2 { +"Index" }

        ul {
            reportTableModel.evaluatorErrors?.let {
                li { a("#policy-violation-summary") { +"Rule Violation Summary (${it.size} violations)" } }
            }

            val numberOfErrors = reportTableModel.errorSummary.rows.count()
            if (numberOfErrors > 0) {
                li { a("#error-summary") { +"Error Summary ($numberOfErrors errors)" } }
            }

            reportTableModel.projectDependencies.forEach { project, projectTable ->
                li {
                    a("#${project.id}") {
                        +"${project.id}"

                        projectTable.exclude?.let { exclude ->
                            +" "
                            div("ort-reason") { +"Excluded: ${exclude.reason} - ${exclude.comment}" }
                        }
                    }
                }
            }
        }
    }

    private fun DIV.evaluatorTable(evaluatorErrors: List<ResolvableIssue>) {
        h2 {
            id = "policy-violation-summary"
            +"Rule Violation Summary (${evaluatorErrors.size} violations)"
        }

        if (evaluatorErrors.isEmpty()) {
            +"No issues found."
        } else {
            table("ort-report-table ort-violations") {
                thead {
                    tr {
                        th { +"Source" }
                        th { +"Error" }
                    }
                }

                tbody { evaluatorErrors.forEach { evaluatorRow(it) } }
            }
        }
    }

    private fun TBODY.evaluatorRow(error: ResolvableIssue) {
        val cssClass = when {
            error.isResolved -> "ort-resolved"
            else -> "ort-error"
        }

        tr(cssClass) {
            td { +error.source }
            td {
                p { +error.description }
                p { +error.resolutionDescription }
            }
        }
    }

    private fun DIV.errorTable(errorSummary: ErrorTable) {
        h2 {
            id = "error-summary"
            +"Error Summary (${errorSummary.rows.count()} errors)"
        }

        p { +"Errors from excluded components are not shown in this summary." }

        h3 { +"Packages" }

        table("ort-report-table ort-errors") {
            thead {
                tr {
                    th { +"Package" }
                    th { +"Analyzer Errors" }
                    th { +"Scanner Errors" }
                }
            }

            tbody { errorSummary.rows.forEach { errorRow(it) } }
        }
    }

    private fun TBODY.errorRow(row: ReportTableModel.ErrorRow) {
        tr("ort-error") {
            td { +"${row.id}" }

            td {
                row.analyzerErrors.forEach { id, errors ->
                    a("#$id") { +"$id" }

                    ul {
                        errors.forEach { error ->
                            li {
                                p { unsafe { +error.description.replace("\n", "<br/>") } }
                                p { +error.resolutionDescription }
                            }
                        }
                    }
                }
            }

            td {
                row.scanErrors.forEach { id, errors ->
                    a("#$id") { +"$id" }

                    ul {
                        errors.forEach { error ->
                            li {
                                p { unsafe { +error.description.replace("\n", "<br/>") } }
                                p { +error.resolutionDescription }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun DIV.projectTable(project: Project, table: ProjectTable) {
        val excludedClass = if (table.exclude != null) "ort-excluded" else ""

        h2 {
            id = "${project.id}"
            +"${project.id} (${project.definitionFilePath})"
        }

        table.exclude?.let { exclude ->
            h3 { +"Project is Excluded" }
            p {
                +"The project is excluded for the following reason:"
                br
                div("reason") { +"${exclude.reason} - ${exclude.comment}" }
            }
        }

        project.vcsProcessed.let { vcsInfo ->
            h3(excludedClass) { +"VCS Information" }

            table("ort-report-metadata $excludedClass") {
                tbody {
                    tr {
                        td { +"Type" }
                        td { +vcsInfo.type }
                    }
                    tr {
                        td { +"URL" }
                        td { +vcsInfo.url }
                    }
                    tr {
                        td { +"Path" }
                        td { +vcsInfo.path }
                    }
                    tr {
                        td { +"Revision" }
                        td { +vcsInfo.revision }
                    }
                }
            }
        }

        h3(excludedClass) { +"Packages" }

        table("ort-report-table ort-packages $excludedClass") {
            thead {
                tr {
                    th { +"Package" }
                    th { +"Scopes" }
                    th { +"Licenses" }
                    th { +"Analyzer Errors" }
                    th { +"Scanner Errors" }
                }
            }

            tbody { table.rows.forEach { projectRow(it) } }
        }
    }

    private fun TBODY.projectRow(row: ReportTableModel.DependencyRow) {
        // Only mark the row as excluded if all scopes the dependency appears in are excluded.
        val rowExcludedClass =
                if (row.scopes.isNotEmpty() && row.scopes.all { it.value.isNotEmpty() }) "ort-excluded" else ""

        val cssClass = when {
            row.analyzerErrors.containsUnresolved() || row.scanErrors.containsUnresolved() -> "ort-error"
            row.declaredLicenses.isEmpty() && row.detectedLicenses.isEmpty() -> "ort-warning"
            else -> "ort-success"
        }

        tr("$cssClass $rowExcludedClass") {
            td { +"${row.id}" }

            td {
                if (row.scopes.isNotEmpty()) {
                    ul {
                        row.scopes.entries.sortedWith(compareBy({ it.value.isNotEmpty() }, { it.key })).forEach {
                            val excludedClass = if (it.value.isNotEmpty()) "ort-excluded" else ""
                            li(excludedClass) {
                                +it.key
                                if (it.value.isNotEmpty()) {
                                    +" "
                                    div("ort-reason") {
                                        +"Excluded: "
                                        +it.value.joinToString { "${it.reason} - ${it.comment}" }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            td {
                dl {
                    row.concludedLicense?.let {
                        dt { em { +"Concluded License:"}}
                        dd { +"${row.concludedLicense}"}
                    }

                    if (row.declaredLicenses.isNotEmpty()) {
                        dt { em { +"Declared Licenses:" } }
                        dd { +row.declaredLicenses.joinToString { if (it.contains(",")) "\"$it\"" else it } }
                    }

                    if (row.detectedLicenses.isNotEmpty()) {
                        dt { em { +"Declared Licenses:" } }
                        dd { +row.detectedLicenses.joinToString { if (it.contains(",")) "\"$it\"" else it } }
                    }
                }
            }

            td { errorList(row.analyzerErrors) }

            td { errorList(row.scanErrors) }
        }
    }

    private fun TD.errorList(errors: List<ResolvableIssue>) {
        ul {
            errors.forEach {
                li {
                    p { unsafe { +it.description.replace("\n", "<br/>") } }

                    if (it.isResolved) {
                        classes = setOf("ort-resolved")
                        p { +it.resolutionDescription }
                    }
                }
            }
        }
    }
}
