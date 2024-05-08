/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.reporters.statichtml

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser

import java.io.File
import java.time.Instant

import javax.xml.parsers.DocumentBuilderFactory

import kotlinx.html.*
import kotlinx.html.dom.*

import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.PackageProvider
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PluginConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.licenses.ResolvedLicenseLocation
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.isValidUri
import org.ossreviewtoolkit.utils.common.joinNonBlank
import org.ossreviewtoolkit.utils.common.normalizeLineBreaks
import org.ossreviewtoolkit.utils.common.titlecase
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.ort.ORT_FULL_NAME
import org.ossreviewtoolkit.utils.spdx.SpdxCompoundExpression
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseIdExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseWithExceptionExpression

private const val RULE_VIOLATION_TABLE_ID = "rule-violation-summary"

@Suppress("LargeClass", "TooManyFunctions")
class StaticHtmlReporter : Reporter {
    override val type = "StaticHtml"

    private val reportFilename = "scan-report.html"
    private val css = javaClass.getResource("/static-html-reporter.css").readText()
    private val licensesSha1 = mutableMapOf<String, String>()

    override fun generateReport(input: ReporterInput, outputDir: File, config: PluginConfiguration): List<File> {
        val tablesReport = TablesReportModelMapper.map(input)

        val html = renderHtml(tablesReport)
        val outputFile = outputDir.resolve(reportFilename)

        outputFile.bufferedWriter().use {
            it.write(html)
        }

        return listOf(outputFile)
    }

    private fun renderHtml(tablesReport: TablesReport): String {
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

                    div {
                        id = "report-top-label"
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
                        with(tablesReport.vcsInfo) {
                            +"Scanned revision $revision of $type repository $url"
                        }
                    }

                    if (tablesReport.labels.isNotEmpty()) {
                        labelsTable(tablesReport.labels)
                    }

                    index(tablesReport)

                    tablesReport.ruleViolations?.let {
                        ruleViolationTable(it)
                    }

                    if (tablesReport.analyzerIssues.rows.isNotEmpty()) {
                        issueTable(tablesReport.analyzerIssues)
                    }

                    if (tablesReport.scannerIssues.rows.isNotEmpty()) {
                        issueTable(tablesReport.scannerIssues)
                    }

                    if (tablesReport.advisorIssues.rows.isNotEmpty()) {
                        issueTable(tablesReport.advisorIssues)
                    }

                    tablesReport.projects.forEach { table ->
                        projectTable(table)
                    }

                    repositoryConfiguration(tablesReport.config)
                }
            }
        }

        return document.serialize().normalizeLineBreaks()
    }

    private fun getRuleViolationSummaryString(ruleViolations: List<TablesReportViolation>): String {
        val violations = ruleViolations.filterNot { it.isResolved }.groupBy { it.violation.severity }
        val errorCount = violations[Severity.ERROR].orEmpty().size
        val warningCount = violations[Severity.WARNING].orEmpty().size
        val hintCount = violations[Severity.HINT].orEmpty().size

        return "Rule Violation Summary ($errorCount errors, $warningCount warnings, $hintCount hints to resolve)"
    }

    private fun DIV.labelsTable(labels: Map<String, String>) {
        h2 { +"Labels" }
        table("report-key-value-table") {
            tbody { labels.forEach { (key, value) -> labelRow(key, value) } }
        }
    }

    private fun TBODY.labelRow(key: String, value: String) {
        tr {
            td { +key }
            td { if (value.isValidUri()) a(value) { +value } else +value }
        }
    }

    private fun DIV.index(tablesReport: TablesReport) {
        h2 { +"Index" }

        ul {
            tablesReport.ruleViolations?.let { ruleViolations ->
                li {
                    a("#$RULE_VIOLATION_TABLE_ID") {
                        +getRuleViolationSummaryString(ruleViolations)
                    }
                }
            }

            if (tablesReport.analyzerIssues.rows.isNotEmpty()) {
                li {
                    a("#${tablesReport.analyzerIssues.id()}") {
                        +tablesReport.analyzerIssues.title()
                    }
                }
            }

            if (tablesReport.scannerIssues.rows.isNotEmpty()) {
                li {
                    a("#${tablesReport.scannerIssues.id()}") {
                        +tablesReport.scannerIssues.title()
                    }
                }
            }

            if (tablesReport.advisorIssues.rows.isNotEmpty()) {
                li {
                    a("#${tablesReport.advisorIssues.id()}") {
                        +tablesReport.advisorIssues.title()
                    }
                }
            }

            tablesReport.projects.forEach { projectTable ->
                li {
                    a("#${projectTable.id.toCoordinates()}") {
                        +projectTable.id.toCoordinates()

                        if (projectTable.isExcluded()) {
                            projectTable.pathExcludes.forEach { exclude ->
                                +" "
                                div("reason") { +"Excluded: ${exclude.description}" }
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

    private fun DIV.ruleViolationTable(ruleViolations: List<TablesReportViolation>) {
        h2 {
            id = RULE_VIOLATION_TABLE_ID
            +getRuleViolationSummaryString(ruleViolations)
        }

        if (ruleViolations.isEmpty()) {
            +"No rule violations found."
        } else {
            table("report-table report-rule-violation-table") {
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
                        ruleViolationRow(rowIndex + 1, ruleViolation)
                    }
                }
            }
        }
    }

    private fun TBODY.ruleViolationRow(rowIndex: Int, ruleViolation: TablesReportViolation) {
        val cssClass = if (ruleViolation.isResolved) {
            "resolved"
        } else {
            when (ruleViolation.violation.severity) {
                Severity.ERROR -> "error"
                Severity.WARNING -> "warning"
                Severity.HINT -> "hint"
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
            id = issueSummary.id()
            +issueSummary.title()
        }

        p { +"Issues from excluded components are not shown in this summary." }

        table("report-table") {
            thead {
                tr {
                    th { +"#" }
                    th { +"Package" }
                    th { +"Message" }
                }
            }

            tbody {
                issueSummary.rows.forEachIndexed { rowIndex, issue ->
                    issueRow(issueSummary.rowId(rowIndex + 1), rowIndex + 1, issue)
                }
            }
        }
    }

    private fun TBODY.issueRow(rowId: String, rowIndex: Int, row: IssueTable.Row) {
        val cssClass = when (row.issue.severity) {
            Severity.ERROR -> "error"
            Severity.WARNING -> "warning"
            Severity.HINT -> "hint"
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
                p { issueDescription(row.issue) }

                if (row.issue.howToFix.isNotBlank()) {
                    details {
                        unsafe { +"<summary>How to fix</summary>" }
                        markdown(row.issue.howToFix)
                    }
                }
            }
        }
    }

    private fun DIV.projectTable(table: ProjectTable) {
        val excludedClass = "excluded".takeIf { table.isExcluded() }.orEmpty()

        h2 {
            id = table.id.toCoordinates()
            +"${table.id.toCoordinates()} (${table.fullDefinitionFilePath})"
        }

        if (table.isExcluded()) {
            h3 { +"Project is Excluded" }
            p { +"The project is excluded for the following reason(s):" }
        }

        table.pathExcludes.forEach { exclude ->
            p {
                div("reason") { +exclude.description }
            }
        }

        table.vcs.let { vcsInfo ->
            h3(excludedClass) { +"VCS Information" }

            table("report-key-value-table $excludedClass") {
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

        table("report-table report-project-table $excludedClass") {
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
                table.rows.forEachIndexed { rowIndex, row ->
                    projectRow(table.id.toCoordinates(), rowIndex + 1, row)
                }
            }
        }
    }

    private fun TBODY.projectRow(projectId: String, rowIndex: Int, row: ProjectTable.Row) {
        // Only mark the row as excluded if all scopes the dependency appears in are excluded.
        val rowExcludedClass =
            if (row.scopes.isNotEmpty() && row.scopes.all { it.excludes.isNotEmpty() }) "excluded" else ""

        val cssClass = when {
            row.analyzerIssues.containsUnresolved() || row.scanIssues.containsUnresolved() -> "error"
            row.declaredLicenses.isEmpty() && row.detectedLicenses.isEmpty() -> "warning"
            else -> "success"
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
                        row.scopes.forEach { scope ->
                            val excludedClass = if (scope.excludes.isNotEmpty()) "excluded" else ""
                            li(excludedClass) {
                                +scope.name
                                if (scope.excludes.isNotEmpty()) {
                                    +" "
                                    div("reason") {
                                        +"Excluded: "
                                        +scope.excludes.joinToString { it.description }
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
                    dl { dd { div { licensesLink(it) } } }
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
                                val firstFinding = license.locations.find { it.matchingPathExcludes.isEmpty() }
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
                                    div("excluded") {
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

                row.effectiveLicense?.let {
                    em { +"Effective License:" }
                    dl { dd { div { licensesLink(it) } } }
                }
            }

            td { issueList(row.analyzerIssues) }

            td { issueList(row.scanIssues) }
        }
    }

    private fun TD.issueList(issues: List<TablesReportIssue>) {
        ul {
            issues.forEach {
                li {
                    p { issueDescription(it) }

                    if (it.isResolved) {
                        classes = setOf("resolved")
                        p { +it.resolutionDescription }
                    }
                }
            }
        }
    }

    private fun P.issueDescription(issue: TablesReportIssue) {
        var first = true
        issue.description.lines().forEach {
            if (first) first = false else br
            +it
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
            HashAlgorithm.SHA1GIT.calculate(licenseResourcePath) ?: license
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
                +" ${SpdxExpression.WITH} "
                licenseLink(expression.exception)
            }
            is SpdxCompoundExpression -> {
                expression.children.forEachIndexed { index, child ->
                    if (index > 0) +" ${expression.operator} "
                    licensesLink(child)
                }
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
        "exception" in license -> "/exceptions/$license"
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
            if (PackageProvider.get(it.sourceArtifact.url) == PackageProvider.MAVEN_CENTRAL) {
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

private val PathExclude.description: String get() = joinNonBlank(reason.toString(), comment)

private val ScopeExclude.description: String get() = joinNonBlank(reason.toString(), comment)

private fun IssueTable.title(): String =
    "${type.name.titlecase()} Issue Summary ($errorCount errors, $warningCount warnings, $hintCount hints to resolve)"

private fun IssueTable.id(): String = "${type.name.lowercase()}-issue-summary"

private fun IssueTable.rowId(index: Int): String = "${id()}-$index"

private fun Collection<TablesReportIssue>.containsUnresolved() = any { !it.isResolved }
