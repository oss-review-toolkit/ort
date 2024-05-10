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

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.plugins.reporters.statichtml.ProjectTable.Row
import org.ossreviewtoolkit.reporter.HowToFixTextProvider
import org.ossreviewtoolkit.reporter.ReporterInput

/**
 * A mapper which converts an [OrtResult] to a [TablesReport].
 */
internal object TablesReportModelMapper {
    fun map(input: ReporterInput): TablesReport {
        val labels = input.ortResult.labels.mapKeys { it.key.substringAfter(".") }

        val ruleViolations = input.ortResult.getRuleViolations()
            .map { it.toTableReportViolation(input.ortResult) }
            .sortedWith(VIOLATION_COMPARATOR)

        val projectTables = input.ortResult.getProjects()
            .map { getProjectTable(input, it) }
            .sortedBy { it.id }

        return TablesReport(
            input.ortResult.repository.vcsProcessed,
            input.ortResult.repository.config,
            ruleViolations,
            getAnalyzerIssueSummaryTable(input),
            getScannerIssueSummaryTable(input),
            getAdvisorIssueSummaryTable(input),
            projectTables,
            labels
        )
    }
}

private val VIOLATION_COMPARATOR = compareBy<TablesReportViolation> { it.isResolved }
    .thenByDescending { it.violation.severity }
    .thenBy { it.violation.rule }
    .thenBy { it.violation.pkg }
    .thenBy { it.violation.license.toString() }
    .thenBy { it.violation.message }
    .thenBy { it.resolutionDescription }

private fun OrtResult.getScopesForDependencies(project: Project): Map<Identifier, Map<String, List<ScopeExclude>>> {
    val result = mutableMapOf<Identifier, MutableMap<String, List<ScopeExclude>>>()
    val excludes = getExcludes()

    dependencyNavigator.scopeDependencies(project).forEach { (scopeName, dependencies) ->
        dependencies.forEach { dependency ->
            result.getOrPut(dependency) { mutableMapOf() }
                .getOrPut(scopeName) { excludes.findScopeExcludes(scopeName) }
        }
    }

    return result
}

private fun Issue.toTableReportIssue(
    ortResult: OrtResult,
    howToFixTextProvider: HowToFixTextProvider,
    isExcluded: Boolean
): TablesReportIssue {
    val resolutions = ortResult.getResolutionsFor(this)
    return TablesReportIssue(
        source = source,
        description = toString(),
        resolutionDescription = buildString {
            if (resolutions.isNotEmpty()) {
                append(
                    resolutions.joinToString(prefix = "\nResolved by: ") {
                        "${it.reason} - ${it.comment}"
                    }
                )
            }
        },
        isExcluded = isExcluded,
        isResolved = resolutions.isNotEmpty(),
        severity = severity,
        howToFix = howToFixTextProvider.getHowToFixText(this).orEmpty()
    )
}

private fun RuleViolation.toTableReportViolation(ortResult: OrtResult): TablesReportViolation {
    val resolutions = ortResult.getResolutionsFor(this)
    return TablesReportViolation(
        violation = this,
        resolutionDescription = buildString {
            if (resolutions.isNotEmpty()) {
                append(
                    resolutions.joinToString(prefix = "\nResolved by: ") {
                        "${it.reason} - ${it.comment}"
                    }
                )
            }
        },
        isResolved = resolutions.isNotEmpty()
    )
}

private fun getProjectTable(input: ReporterInput, project: Project): ProjectTable {
    val projectIssuesForId = input.ortResult.dependencyNavigator.projectIssues(project)
    val scannerIssuesForId = input.ortResult.getScannerIssues()
    val scopesForId = input.ortResult.getScopesForDependencies(project)
    val ids = input.ortResult.dependencyNavigator.projectDependencies(project) + project.id

    val rows = ids.map { id ->
        val resolvedLicenseInfo = input.licenseInfoResolver.resolveLicenseInfo(id)

        val concludedLicense = resolvedLicenseInfo.licenseInfo.concludedLicenseInfo.concludedLicense
            ?.sorted()
        val declaredLicenses = resolvedLicenseInfo.filter { LicenseSource.DECLARED in it.sources }
            .sortedBy { it.license.toString() }
        val detectedLicenses = resolvedLicenseInfo.filter { LicenseSource.DETECTED in it.sources }
            .sortedBy { it.license.toString() }
        val effectiveLicense = resolvedLicenseInfo.filterExcluded().effectiveLicense(
            LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED,
            input.ortResult.getPackageLicenseChoices(id),
            input.ortResult.getRepositoryLicenseChoices()
        )?.sorted()

        val issues = buildList {
            addAll(projectIssuesForId[id].orEmpty())
            addAll(input.ortResult.analyzer?.result?.issues?.get(id).orEmpty())
            addAll(scannerIssuesForId[id].orEmpty())
        }.map { issue ->
            val isRowExcluded = input.ortResult.isExcluded(id) ||
                (id != project.id && scopesForId[id].orEmpty().all { it.value.isNotEmpty() })

            issue.toTableReportIssue(
                input.ortResult,
                input.howToFixTextProvider,
                isRowExcluded || input.ortResult.isExcluded(issue, id)
            )
        }

        val (openIssues, excludedOrResolvedIssue) = issues.partition {
            !(it.isResolved || it.isExcluded)
        }

        val scopes = scopesForId[id].orEmpty().map { (name, excludes) ->
            ProjectTable.Scope(name, excludes)
        }.sortedWith(compareBy({ it.excludes.isNotEmpty() }, { it.name }))

        val pkg = input.ortResult.getPackageOrProject(id)?.metadata

        Row(
            id = id,
            sourceArtifact = pkg?.sourceArtifact.orEmpty(),
            vcsInfo = pkg?.vcsProcessed.orEmpty(),
            scopes = scopes,
            concludedLicense = concludedLicense,
            declaredLicenses = declaredLicenses,
            detectedLicenses = detectedLicenses,
            effectiveLicense = effectiveLicense,
            openIssues.sortedByDescending { it.severity },
            excludedOrResolvedIssue.sortedByDescending { it.isResolved }
        )
    }

    return ProjectTable(
        id = project.id,
        vcs = project.vcsProcessed,
        rows = rows.sortedWith(compareByDescending<Row> { it.id == project.id }.thenBy { it.id }),
        fullDefinitionFilePath = input.ortResult.getDefinitionFilePathRelativeToAnalyzerRoot(project),
        pathExcludes = input.ortResult.getExcludes().findPathExcludes(project, input.ortResult)
    )
}

private fun getAnalyzerIssueSummaryTable(input: ReporterInput): IssueTable =
    input.ortResult.getAnalyzerIssues(omitExcluded = true, omitResolved = true)
        .toIssueSummaryTable(IssueTable.Type.ANALYZER, input)

private fun getScannerIssueSummaryTable(input: ReporterInput): IssueTable =
    input.ortResult.getScannerIssues(omitExcluded = true, omitResolved = true)
        .toIssueSummaryTable(IssueTable.Type.SCANNER, input)

private fun getAdvisorIssueSummaryTable(input: ReporterInput): IssueTable =
    input.ortResult.getAdvisorIssues(omitExcluded = true, omitResolved = true)
        .toIssueSummaryTable(IssueTable.Type.ADVISOR, input)

private fun Map<Identifier, Set<Issue>>.toIssueSummaryTable(type: IssueTable.Type, input: ReporterInput): IssueTable {
    val rows = flatMap { (id, issues) ->
        issues.map { issue ->
            val resolvableIssue = issue.toTableReportIssue(input.ortResult, input.howToFixTextProvider, false)
            IssueTable.Row(resolvableIssue, id)
        }
    }.sortedWith(compareByDescending<IssueTable.Row> { it.issue.severity }.thenBy { it.id })

    return IssueTable(type, rows)
}
