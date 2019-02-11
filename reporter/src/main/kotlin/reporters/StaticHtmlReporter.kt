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

import com.here.ort.model.OrtResult
import com.here.ort.model.Project
import com.here.ort.model.Severity
import com.here.ort.model.config.CopyrightGarbage
import com.here.ort.reporter.Reporter
import com.here.ort.reporter.ResolutionProvider
import com.here.ort.reporter.reporters.ReportTableModel.IssueTable
import com.here.ort.reporter.reporters.ReportTableModel.ProjectTable
import com.here.ort.reporter.reporters.ReportTableModel.ResolvableIssue
import com.here.ort.utils.isValidUrl
import com.here.ort.utils.normalizeLineBreaks

import java.io.OutputStream

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

        .ort-report-table tr.ort-hint {
          background: #f7f5ff;
          color: #1c0859;
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
          width: 30px;
          white-space: nowrap;
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

    override val reporterName = "StaticHtml"
    override val defaultFilename = "scan-report.html"

    override fun generateReport(
            ortResult: OrtResult,
            resolutionProvider: ResolutionProvider,
            copyrightGarbage: CopyrightGarbage,
            outputStream: OutputStream,
            postProcessingScript: String?
    ) {
        val tabularScanRecord = ReportTableModelMapper(resolutionProvider).mapToReportTableModel(ortResult)
        val html = renderHtml(tabularScanRecord)

        outputStream.bufferedWriter().use {
            it.write(html)
        }
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

                    reportTableModel.evaluatorIssues?.let {
                        evaluatorTable(it)
                    }

                    if (reportTableModel.issueSummary.rows.count() > 0) {
                        issueTable(reportTableModel.issueSummary)
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
            reportTableModel.evaluatorIssues?.let {
                li { a("#policy-violation-summary") { +"Rule Violation Summary (${it.size} violations)" } }
            }

            val numberOfIssues = reportTableModel.issueSummary.rows.count()
            if (numberOfIssues > 0) {
                li { a("#issue-summary") { +"Issue Summary ($numberOfIssues issues)" } }
            }

            reportTableModel.projectDependencies.forEach { project, projectTable ->
                li {
                    a("#${project.id.toCoordinates()}") {
                        +"${project.id.toCoordinates()}"

                        projectTable.exclude?.let { exclude ->
                            +" "
                            div("ort-reason") { +"Excluded: ${exclude.reason} - ${exclude.comment}" }
                        }
                    }
                }
            }
        }
    }

    private fun DIV.evaluatorTable(ruleViolations: List<ResolvableIssue>) {
        h2 {
            id = "policy-violation-summary"
            +"Rule Violation Summary (${ruleViolations.size} violations)"
        }

        if (ruleViolations.isEmpty()) {
            +"No rule violations found."
        } else {
            table("ort-report-table ort-violations") {
                thead {
                    tr {
                        th { +"#" }
                        th { +"Source" }
                        th { +"Issue" }
                    }
                }

                tbody {
                    ruleViolations.forEachIndexed { rowIndex, ruleViolation ->
                        evaluatorRow(rowIndex + 1, ruleViolation)
                    }
                }
            }
        }
    }

    private fun TBODY.evaluatorRow(rowIndex: Int, ruleViolation: ResolvableIssue) {
        val cssClass = if (ruleViolation.isResolved) {
            "ort-resolved"
        } else {
            when (ruleViolation.severity) {
                Severity.ERROR -> "ort-error"
                Severity.WARNING -> "ort-warning"
                Severity.HINT -> "ort-hint"
            }
        }

        val rowId = "violation-$rowIndex"

        tr(cssClass) {
            id = rowId
            td {
                a {
                    href = "#$rowId"
                    +rowIndex.toString()
                }
            }
            td { +ruleViolation.source }
            td {
                p { +ruleViolation.description }
                p { +ruleViolation.resolutionDescription }
            }
        }
    }

    private fun DIV.issueTable(issueSummary: IssueTable) {
        h2 {
            id = "issue-summary"
            +"Issue Summary (${issueSummary.rows.count()} issues)"
        }

        p { +"Issues from excluded components are not shown in this summary." }

        h3 { +"Packages" }

        table("ort-report-table") {
            thead {
                tr {
                    th { +"#" }
                    th { +"Package" }
                    th { +"Analyzer Issues" }
                    th { +"Scanner Issues" }
                }
            }

            tbody {
                issueSummary.rows.forEachIndexed { rowIndex, issue ->
                    issueRow(rowIndex + 1, issue)
                }
            }
        }
    }

    private fun TBODY.issueRow(rowIndex: Int, row: ReportTableModel.IssueRow) {
        val rowId = "issue-$rowIndex"

        val worstSeverity = row.analyzerIssues.flatMap { it.value }.map { it.severity }.min() ?: Severity.ERROR

        val areAllResolved = row.analyzerIssues.isNotEmpty() && row.analyzerIssues.all { (_, issues) ->
            issues.all { it.isResolved }
        }

        val cssClass = if (areAllResolved) {
            "ort-resolved"
        } else {
            when (worstSeverity) {
                Severity.ERROR -> "ort-error"
                Severity.WARNING -> "ort-warning"
                Severity.HINT -> "ort-hint"
            }
        }

        tr(cssClass) {
            id = rowId
            td {
                a {
                    href = "#$rowId"
                    +rowIndex.toString()
                }
            }
            td { +"${row.id.toCoordinates()}" }

            td {
                row.analyzerIssues.forEach { id, issues ->
                    a("#${id.toCoordinates()}") { +"${id.toCoordinates()}" }

                    ul {
                        issues.forEach { issue ->
                            li {
                                issueDescription(issue)
                                p { +issue.resolutionDescription }
                            }
                        }
                    }
                }
            }

            td {
                row.scanIssues.forEach { id, issues ->
                    a("#${id.toCoordinates()}") { +"${id.toCoordinates()}" }

                    ul {
                        issues.forEach { issue ->
                            li {
                                issueDescription(issue)
                                p { +issue.resolutionDescription }
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
            id = "${project.id.toCoordinates()}"
            +"${project.id.toCoordinates()} (${project.definitionFilePath})"
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
                    th { +"#" }
                    th { +"Package" }
                    th { +"Scopes" }
                    th { +"Licenses" }
                    th { +"Analyzer Issues" }
                    th { +"Scanner Issues" }
                }
            }

            tbody {
                table.rows.forEachIndexed { rowIndex, pkg ->
                    projectRow(project.id.toCoordinates(), rowIndex + 1, pkg)
                }
            }
        }
    }

    private fun TBODY.projectRow(projectId: String, rowIndex: Int, row: ReportTableModel.DependencyRow) {
        // Only mark the row as excluded if all scopes the dependency appears in are excluded.
        val rowExcludedClass =
                if (row.scopes.isNotEmpty() && row.scopes.all { it.value.isNotEmpty() }) "ort-excluded" else ""

        val cssClass = when {
            row.analyzerIssues.containsUnresolved() || row.scanIssues.containsUnresolved() -> "ort-error"
            row.declaredLicenses.isEmpty() && row.detectedLicenses.isEmpty() -> "ort-warning"
            else -> "ort-success"
        }

        val rowId = "$projectId-pkg-$rowIndex"

        tr("$cssClass $rowExcludedClass") {
            id = rowId
            td {
                a {
                    href = "#$rowId"
                    +rowIndex.toString()
                }
            }
            td { +"${row.id.toCoordinates()}" }

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
                row.concludedLicense?.let {
                    em { +"Concluded License:" }
                    dl { dd { +"${row.concludedLicense}" } }
                }

                if (row.declaredLicenses.isNotEmpty()) {
                    em { +"Declared Licenses:" }
                    dl { dd { +row.declaredLicenses.joinToString { if (it.contains(",")) "\"$it\"" else it } } }
                }

                if (row.detectedLicenses.isNotEmpty()) {
                    em { +"Detected Licenses:" }
                    dl { dd { +row.detectedLicenses.joinToString { if (it.contains(",")) "\"$it\"" else it } } }
                }
            }

            td { issueList(row.analyzerIssues) }

            td { issueList(row.scanIssues) }
        }
    }

    private fun TD.issueList(issues: List<ResolvableIssue>) {
        ul {
            issues.forEach {
                li {
                    issueDescription(it)

                    if (it.isResolved) {
                        classes = setOf("ort-resolved")
                        p { +it.resolutionDescription }
                    }
                }
            }
        }
    }

    private fun LI.issueDescription(issue: ResolvableIssue) {
        p {
            var first = true
            issue.description.lines().forEach {
                if (first) first = false else br
                +it
            }
        }
    }
}
