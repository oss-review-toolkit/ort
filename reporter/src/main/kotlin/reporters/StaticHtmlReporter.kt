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

import com.here.ort.downloader.VcsHost
import com.here.ort.model.Environment
import com.here.ort.model.OrtResult
import com.here.ort.model.Project
import com.here.ort.model.Severity
import com.here.ort.model.config.CopyrightGarbage
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.licenses.LicenseConfiguration
import com.here.ort.model.yamlMapper
import com.here.ort.reporter.LicenseTextProvider
import com.here.ort.reporter.Reporter
import com.here.ort.reporter.ResolutionProvider
import com.here.ort.reporter.reporters.ReportTableModel.IssueTable
import com.here.ort.reporter.reporters.ReportTableModel.ProjectTable
import com.here.ort.reporter.reporters.ReportTableModel.ResolvableIssue
import com.here.ort.utils.isValidUrl
import com.here.ort.utils.normalizeLineBreaks
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser

import java.io.OutputStream
import java.time.Instant

import javax.xml.parsers.DocumentBuilderFactory

import kotlinx.html.*
import kotlinx.html.dom.*

@Suppress("LargeClass")
class StaticHtmlReporter : Reporter() {
    private val css = javaClass.getResource("/static-html-reporter.css").readText()

    override val reporterName = "StaticHtml"
    override val defaultFilename = "scan-report.html"

    override fun generateReport(
        outputStream: OutputStream,
        ortResult: OrtResult,
        resolutionProvider: ResolutionProvider,
        licenseTextProvider: LicenseTextProvider,
        copyrightGarbage: CopyrightGarbage,
        licenseConfiguration: LicenseConfiguration,
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
                        +"\n"
                        +css
                    }
                }
                style {
                    unsafe {
                        +"\n"
                        +"<![CDATA[${javaClass.getResource("/prismjs/prism.css").readText()}]]>"
                    }
                }
            }
            body {
                script(type = ScriptType.textJavaScript) {
                    unsafe {
                        +"\n"
                        +"<![CDATA[${javaClass.getResource("/prismjs/prism.js").readText()}]]>"
                    }
                }

                div {
                    id = "report-container"

                    div("ort-report-label") {
                        +"Scan Report"
                    }

                    div {
                        +"Created by "
                        strong { +"ORT" }
                        +", the "
                        a {
                            href = "http://oss-review-toolkit.org/"
                            +"OSS Review Toolkit"
                        }
                        +", version ${Environment().ortVersion} on ${Instant.now()}."
                    }

                    if (reportTableModel.metadata.isNotEmpty()) {
                        metadataTable(reportTableModel.metadata)
                    }

                    index(reportTableModel)

                    reportTableModel.evaluatorIssues?.let {
                        evaluatorTable(it)
                    }

                    if (reportTableModel.issueSummary.rows.isNotEmpty()) {
                        issueTable(reportTableModel.issueSummary)
                    }

                    reportTableModel.projectDependencies.forEach { (project, table) ->
                        projectTable(project, table)
                    }

                    repositoryConfiguration(reportTableModel.config)
                }
            }
        }

        return document.serialize().normalizeLineBreaks()
    }

    private fun DIV.metadataTable(metadata: Map<String, String>) {
        h2 { +"Metadata" }
        table("ort-report-metadata") {
            tbody { metadata.forEach { (key, value) -> metadataRow(key, value) } }
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
            reportTableModel.evaluatorIssues?.let { ruleViolations ->
                val issues = ruleViolations.filterNot { it.isResolved }.groupBy { it.violation.severity }
                val errorCount = issues[Severity.ERROR].orEmpty().size
                val warningCount = issues[Severity.WARNING].orEmpty().size
                val hintCount = issues[Severity.HINT].orEmpty().size

                li {
                    a("#rule-violation-summary") {
                        +("Rule Violation Summary ($errorCount errors, $warningCount warnings, $hintCount hints to " +
                                "resolve)")
                    }
                }
            }

            if (reportTableModel.issueSummary.rows.isNotEmpty()) {
                li {
                    a("#issue-summary") {
                        with(reportTableModel.issueSummary) {
                            +"Issue Summary ($errorCount errors, $warningCount warnings, $hintCount hints to resolve)"
                        }
                    }
                }
            }

            reportTableModel.projectDependencies.forEach { (project, projectTable) ->
                li {
                    a("#${project.id.toCoordinates()}") {
                        +project.id.toCoordinates()

                        if (projectTable.isExcluded()) {
                            projectTable.pathExcludes.forEach { exclude ->
                                +" "
                                div("ort-reason") { +"Excluded: ${exclude.reason} - ${exclude.comment}" }
                            }
                        }
                    }
                }
            }

            li {
                a("#repository-configuration") {
                    +"Repository Configuration"
                }
            }
        }
    }

    private fun DIV.evaluatorTable(ruleViolations: List<ReportTableModel.ResolvableViolation>) {
        val issues = ruleViolations.filterNot { it.isResolved }.groupBy { it.violation.severity }
        val errorCount = issues[Severity.ERROR].orEmpty().size
        val warningCount = issues[Severity.WARNING].orEmpty().size
        val hintCount = issues[Severity.HINT].orEmpty().size

        h2 {
            id = "rule-violation-summary"
            +"Rule Violation Summary ($errorCount errors, $warningCount warnings, $hintCount hints to resolve)"
        }

        if (ruleViolations.isEmpty()) {
            +"No rule violations found."
        } else {
            table("ort-report-table ort-violations") {
                thead {
                    tr {
                        th { +"#" }
                        th { +"Rule" }
                        th { +"Package" }
                        th { +"License" }
                        th { +"Message" }
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

    private fun TBODY.evaluatorRow(rowIndex: Int, ruleViolation: ReportTableModel.ResolvableViolation) {
        val cssClass = if (ruleViolation.isResolved) {
            "ort-resolved"
        } else {
            when (ruleViolation.violation.severity) {
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
            td { +ruleViolation.violation.rule }
            td { +ruleViolation.violation.pkg.toCoordinates() }
            td {
                +if (ruleViolation.violation.license != null) {
                    "${ruleViolation.violation.licenseSource}: ${ruleViolation.violation.license}"
                } else {
                    "-"
                }
            }
            td {
                p { +ruleViolation.violation.message }
                if (ruleViolation.isResolved) {
                    p { +ruleViolation.resolutionDescription }
                } else {
                    details {
                        unsafe { +"<summary>How to fix</summary>" }
                        markdown(ruleViolation.violation.howToFix)
                    }
                }
            }
        }
    }

    private fun DIV.issueTable(issueSummary: IssueTable) {
        h2 {
            id = "issue-summary"
            with(issueSummary) {
                +"Issue Summary ($errorCount errors, $warningCount warnings, $hintCount hints to resolve)"
            }
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
            td { +row.id.toCoordinates() }

            td {
                row.analyzerIssues.forEach { (id, issues) ->
                    a("#${id.toCoordinates()}") { +id.toCoordinates() }

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
                row.scanIssues.forEach { (id, issues) ->
                    a("#${id.toCoordinates()}") { +id.toCoordinates() }

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
        val excludedClass = "ort-excluded".takeIf { table.isExcluded() }.orEmpty()

        h2 {
            id = project.id.toCoordinates()
            +"${project.id.toCoordinates()} (${table.fullDefinitionFilePath})"
        }

        if (table.isExcluded()) {
            h3 { +"Project is Excluded" }
            p { +"The project is excluded for the following reason(s):" }
        }

        table.pathExcludes.forEach { exclude ->
            p {
                div("ort-reason") { +"${exclude.reason} - ${exclude.comment}" }
            }
        }

        project.vcsProcessed.let { vcsInfo ->
            h3(excludedClass) { +"VCS Information" }

            table("ort-report-metadata $excludedClass") {
                tbody {
                    tr {
                        td { +"Type" }
                        td { +vcsInfo.type.toString() }
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
            td { +row.id.toCoordinates() }

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
                    dl {
                        dd {
                            row.detectedLicenses.forEach { (finding, excludes) ->
                                val firstFinding = finding.locations.first()
                                val permalink = VcsHost.toPermalink(
                                    row.vcsInfo.copy(path = "${row.vcsInfo.path}/${firstFinding.path}"),
                                    firstFinding.startLine, firstFinding.endLine
                                )

                                if (excludes.isEmpty()) {
                                    div {
                                        +finding.license
                                        if (permalink != null) {
                                            val count = finding.locations.count()
                                            if (count > 1) {
                                                +" (exemplary "
                                                a {
                                                    href = permalink
                                                    +"link"
                                                }
                                                +" to the first of $count locations)"
                                            } else {
                                                +" ("
                                                a {
                                                    href = permalink
                                                    +"link"
                                                }
                                                +" to the location)"
                                            }
                                        }
                                    }
                                } else {
                                    div("ort-excluded") {
                                        +"${finding.license} (Excluded: "
                                        +excludes.joinToString { "${it.reason} - ${it.comment}" }
                                        +")"
                                    }
                                }
                            }
                        }
                    }
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

    private fun DIV.repositoryConfiguration(config: RepositoryConfiguration) {
        h2 {
            id = "repository-configuration"
            +"Repository Configuration"
        }

        pre {
            code("language-yaml") {
                +"\n"
                +yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config)
            }
        }
    }

    private fun HTMLTag.markdown(markdown: String) {
        val markdownParser = Parser.builder().build()
        val document = markdownParser.parse(markdown)
        val renderer = HtmlRenderer.builder().build()
        unsafe { +renderer.render(document) }
    }
}
