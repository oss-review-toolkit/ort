/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.reporter.reporters

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser

import java.io.File
import java.time.Instant
import java.util.SortedMap

import javax.xml.parsers.DocumentBuilderFactory

import kotlinx.html.*
import kotlinx.html.dom.*

import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.licenses.ResolvedLicenseLocation
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.reporter.description
import org.ossreviewtoolkit.reporter.utils.ReportTableModel
import org.ossreviewtoolkit.reporter.utils.ReportTableModel.IssueTable
import org.ossreviewtoolkit.reporter.utils.ReportTableModel.ProjectTable
import org.ossreviewtoolkit.reporter.utils.ReportTableModel.ResolvableIssue
import org.ossreviewtoolkit.reporter.utils.ReportTableModelMapper
import org.ossreviewtoolkit.reporter.utils.SCOPE_EXCLUDE_LIST_COMPARATOR
import org.ossreviewtoolkit.reporter.utils.containsUnresolved
import org.ossreviewtoolkit.spdx.SpdxCompoundExpression
import org.ossreviewtoolkit.spdx.SpdxConstants
import org.ossreviewtoolkit.spdx.SpdxExpression
import org.ossreviewtoolkit.spdx.SpdxLicenseIdExpression
import org.ossreviewtoolkit.spdx.SpdxLicenseWithExceptionExpression
import org.ossreviewtoolkit.utils.Environment
import org.ossreviewtoolkit.utils.ORT_FULL_NAME
import org.ossreviewtoolkit.utils.isValidUri
import org.ossreviewtoolkit.utils.normalizeLineBreaks

@Suppress("LargeClass", "TooManyFunctions")
class StaticHtmlReporter : Reporter {
    override val reporterName = "StaticHtml"

    private val reportFilename = "scan-report.html"
    private val css = javaClass.getResource("/static-html-reporter.css").readText()
    private val licensesSha1 = mutableMapOf<String, String>()

    override fun generateReport(
        input: ReporterInput,
        outputDir: File,
        options: Map<String, String>
    ): List<File> {
        val tabularScanRecord = ReportTableModelMapper(input.resolutionProvider, input.howToFixTextProvider)
            .mapToReportTableModel(
                input.ortResult,
                input.licenseInfoResolver
            )

        val html = renderHtml(tabularScanRecord)
        val outputFile = outputDir.resolve(reportFilename)

        outputFile.bufferedWriter().use {
            it.write(html)
        }

        return listOf(outputFile)
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
                            href = "https://oss-review-toolkit.org/"
                            +ORT_FULL_NAME
                        }
                        +", version ${Environment.ORT_VERSION} on ${Instant.now()}."
                    }

                    h2 { +"Project" }

                    div {
                        with(reportTableModel.vcsInfo) {
                            +"Scanned revision $revision of $type repository $url"
                        }
                    }

                    if (reportTableModel.labels.isNotEmpty()) {
                        labelsTable(reportTableModel.labels)
                    }

                    index(reportTableModel)

                    reportTableModel.ruleViolations?.let {
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

    private fun getRuleViolationSummaryString(ruleViolations: List<ReportTableModel.ResolvableViolation>): String {
        val violations = ruleViolations.filterNot { it.isResolved }.groupBy { it.violation.severity }
        val errorCount = violations[Severity.ERROR].orEmpty().size
        val warningCount = violations[Severity.WARNING].orEmpty().size
        val hintCount = violations[Severity.HINT].orEmpty().size

        return "Rule Violation Summary ($errorCount errors, $warningCount warnings, $hintCount hints to resolve)"
    }

    private fun DIV.labelsTable(labels: Map<String, String>) {
        h2 { +"Labels" }
        table("ort-report-labels") {
            tbody { labels.forEach { (key, value) -> labelRow(key, value) } }
        }
    }

    private fun TBODY.labelRow(key: String, value: String) {
        tr {
            td { +key }
            td { if (value.isValidUri()) a(value) { +value } else +value }
        }
    }

    private fun DIV.index(reportTableModel: ReportTableModel) {
        h2 { +"Index" }

        ul {
            reportTableModel.ruleViolations?.let { ruleViolations ->
                li {
                    a("#rule-violation-summary") {
                        +getRuleViolationSummaryString(ruleViolations)
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
                                div("ort-reason") { +"Excluded: ${exclude.description}" }
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
        h2 {
            id = "rule-violation-summary"
            +getRuleViolationSummaryString(ruleViolations)
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
            td { +(ruleViolation.violation.pkg?.toCoordinates() ?: "-") }
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

    private fun TR.listIssues(issues: SortedMap<Identifier, List<ResolvableIssue>>) {
        td {
            issues.forEach { (id, issues) ->
                a("#${id.toCoordinates()}") { +id.toCoordinates() }

                ul {
                    issues.forEach { issue ->
                        li {
                            issueDescription(issue)
                            p { +issue.resolutionDescription }
                        }

                        if (!issue.isResolved && issue.howToFix.isNotBlank()) {
                            details {
                                unsafe { +"<summary>How to fix</summary>" }
                                markdown(issue.howToFix)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun TBODY.issueRow(rowIndex: Int, row: ReportTableModel.IssueRow) {
        val rowId = "issue-$rowIndex"

        val issues = (row.analyzerIssues + row.scanIssues).flatMap { it.value }

        val worstSeverity = issues.filterNot { it.isResolved }.map { it.severity }.minOrNull() ?: Severity.ERROR

        val areAllResolved = issues.isNotEmpty() && issues.all { it.isResolved }

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

            listIssues(row.analyzerIssues)
            listIssues(row.scanIssues)
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
                div("ort-reason") { +exclude.description }
            }
        }

        project.vcsProcessed.let { vcsInfo ->
            h3(excludedClass) { +"VCS Information" }

            table("ort-report-labels $excludedClass") {
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
                val projectRow = table.rows.single { it.id == project.id }
                projectRow(project.id.toCoordinates(), 1, projectRow)
                (table.rows - projectRow).forEachIndexed { rowIndex, pkg ->
                    projectRow(project.id.toCoordinates(), rowIndex + 2, pkg)
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
                        row.scopes.entries.sortedWith(SCOPE_EXCLUDE_LIST_COMPARATOR)
                            .forEach { (scopeName, scopeExcludes) ->
                                val excludedClass = if (scopeExcludes.isNotEmpty()) "ort-excluded" else ""
                                li(excludedClass) {
                                    +scopeName
                                    if (scopeExcludes.isNotEmpty()) {
                                        +" "
                                        div("ort-reason") {
                                            +"Excluded: "
                                            +scopeExcludes.joinToString { it.description }
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
                    dl { dd { div { licensesLink(row.concludedLicense) } } }
                }

                if (row.declaredLicenses.isNotEmpty()) {
                    em { +"Declared Licenses:" }
                    dl { dd { row.declaredLicenses.forEach { div { licensesLink(it.license) } } } }
                }

                if (row.detectedLicenses.isNotEmpty()) {
                    // All license location from a package share the same provenance, so just picking the first is fine.
                    val firstLicenseLocation = row.detectedLicenses.first().locations.firstOrNull()

                    em {
                        +"Detected Licenses"
                        provenanceLink(firstLicenseLocation?.provenance)
                        +":"
                    }

                    dl {
                        dd {
                            row.detectedLicenses.forEach { license ->
                                val firstFinding = license.locations.firstOrNull { it.matchingPathExcludes.isEmpty() }
                                    ?: license.locations.firstOrNull()

                                val permalink = firstFinding?.permalink(row.id)
                                val pathExcludes = license.locations.flatMapTo(mutableSetOf()) {
                                    it.matchingPathExcludes
                                }

                                if (!license.isDetectedExcluded) {
                                    div {
                                        licensesLink(license.license)
                                        if (permalink != null) {
                                            val count = license.locations.count { it.matchingPathExcludes.isEmpty() }
                                            permalink(permalink, count)
                                        }
                                    }
                                } else {
                                    div("ort-excluded") {
                                        +"${license.license} (Excluded: "
                                        +pathExcludes.joinToString { it.description }
                                        +")"
                                        if (permalink != null) {
                                            permalink(permalink, license.locations.size)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (row.effectiveLicense != null) {
                    em { +"Effective License:" }
                    dl { dd { div { licensesLink(row.effectiveLicense) } } }
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

    private fun DIV.licenseLink(license: String) {
        val licenseResourcePath = getLicenseResourcePath(license)
        val sha1Git = licensesSha1.getOrPut(license) {
            HashAlgorithm.SHA1_GIT.calculate(licenseResourcePath) ?: license
        }

        if (sha1Git == license) {
            +license
        } else {
            // Software Heritage is able to identify textual content by providing a sha1_git, as explained here:
            // https://docs.softwareheritage.org/devel/swh-web/uri-scheme-browse-content.html
            a(href = "https://archive.softwareheritage.org/browse/content/sha1_git:$sha1Git") {
                +license
            }
        }
    }

    private fun DIV.licensesLink(expression: SpdxExpression) {
        when (expression) {
            is SpdxLicenseIdExpression -> {
                licenseLink(expression.toString())
            }
            is SpdxLicenseWithExceptionExpression -> {
                licenseLink(expression.simpleLicense())
                +" WITH "
                licenseLink(expression.exception)
            }
            is SpdxCompoundExpression -> {
                licensesLink(expression.left)
                +" ${expression.operator} "
                licensesLink(expression.right)
            }
            else -> {
                +expression.toString()
            }
        }
    }
}

private fun getLicenseResourcePath(license: String): String =
    when {
        license.startsWith(SpdxConstants.LICENSE_REF_PREFIX) -> "/licenserefs/$license"
        license.contains("exception") -> "/exceptions/$license"
        else -> "/licenses/$license"
    }

private fun EM.provenanceLink(provenance: Provenance?) {
    if (provenance is ArtifactProvenance) {
        +" (from "
        a(href = provenance.sourceArtifact.url) { +"artifact" }
        +")"
    } else if (provenance is RepositoryProvenance) {
        +" (from "
        a(href = provenance.vcsInfo.url) { +"VCS" }
        +")"
    }
}

private fun DIV.permalink(permalink: String, count: Int) {
    if (count > 1) {
        +" (exemplary "
        a(href = permalink) { +"link" }
        +" to the first of $count locations)"
    } else {
        +" ("
        a(href = permalink) { +"link" }
        +" to the location)"
    }
}

private fun ResolvedLicenseLocation.permalink(id: Identifier): String? {
    (provenance as? RepositoryProvenance)?.let {
        if (it.vcsInfo != VcsInfo.EMPTY) {
            return VcsHost.toPermalink(
                it.vcsInfo.copy(path = location.path),
                location.startLine, location.endLine
            )
        }
    }

    (provenance as? ArtifactProvenance)?.let {
        if (it.sourceArtifact != RemoteArtifact.EMPTY) {
            val mavenCentralPattern = Regex("https?://repo[^/]+maven[^/]+org/.*")
            if (it.sourceArtifact.url.matches(mavenCentralPattern)) {
                // At least for source artifacts on Maven Central, use the "proxy" from Sonatype which has the
                // Archive Browser plugin installed to link to the files with findings.
                return with(id) {
                    val group = namespace.replace('.', '/')
                    "https://repository.sonatype.org/" +
                            "service/local/repositories/central-proxy/" +
                            "archive/$group/$name/$version/$name-$version-sources.jar/" +
                            "!/${location.path}"
                }
            }
        }
    }

    return null
}
