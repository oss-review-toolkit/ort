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

import org.ossreviewtoolkit.model.DependencyNavigator
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.plugins.reporters.statichtml.ReportTableModel.DependencyRow
import org.ossreviewtoolkit.plugins.reporters.statichtml.ReportTableModel.IssueTable
import org.ossreviewtoolkit.plugins.reporters.statichtml.ReportTableModel.ProjectTable
import org.ossreviewtoolkit.plugins.reporters.statichtml.ReportTableModel.ResolvableIssue
import org.ossreviewtoolkit.plugins.reporters.statichtml.ReportTableModel.ResolvableViolation
import org.ossreviewtoolkit.reporter.HowToFixTextProvider
import org.ossreviewtoolkit.reporter.ReporterInput

/**
 * A mapper which converts an [OrtResult] to a [ReportTableModel].
 */
internal object ReportTableModelMapper {
    fun map(input: ReporterInput): ReportTableModel {
        val analyzerResult = input.ortResult.analyzer?.result
        val excludes = input.ortResult.getExcludes()

        val projectTables = analyzerResult?.projects?.associateWith { project ->
            val scopesForDependencies = project.getScopesForDependencies(excludes, input.ortResult.dependencyNavigator)
            val pathExcludes = excludes.findPathExcludes(project, input.ortResult)

            val allIds = sortedSetOf(project.id)
            allIds += input.ortResult.dependencyNavigator.projectDependencies(project)

            val projectIssues = input.ortResult.dependencyNavigator.projectIssues(project)
            val tableRows = allIds.map { id ->
                val scanResults = input.ortResult.getScanResultsForId(id)

                val resolvedLicenseInfo = input.licenseInfoResolver.resolveLicenseInfo(id)

                val concludedLicense = resolvedLicenseInfo.licenseInfo.concludedLicenseInfo.concludedLicense
                val declaredLicenses = resolvedLicenseInfo.filter { LicenseSource.DECLARED in it.sources }
                    .sortedBy { it.license.toString() }
                val detectedLicenses = resolvedLicenseInfo.filter { LicenseSource.DETECTED in it.sources }
                    .sortedBy { it.license.toString() }

                val analyzerIssues = projectIssues[id].orEmpty() + analyzerResult.issues[id].orEmpty()

                val scanIssues = scanResults.flatMapTo(mutableSetOf()) {
                    it.summary.issues
                }

                val pkg = input.ortResult.getPackageOrProject(id)?.metadata

                DependencyRow(
                    id = id,
                    sourceArtifact = pkg?.sourceArtifact.orEmpty(),
                    vcsInfo = pkg?.vcsProcessed.orEmpty(),
                    scopes = scopesForDependencies[id].orEmpty().toSortedMap(),
                    concludedLicense = concludedLicense,
                    declaredLicenses = declaredLicenses,
                    detectedLicenses = detectedLicenses,
                    effectiveLicense = resolvedLicenseInfo.filterExcluded().effectiveLicense(
                        LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED,
                        input.ortResult.getPackageLicenseChoices(id),
                        input.ortResult.getRepositoryLicenseChoices()
                    )?.sorted(),
                    analyzerIssues = analyzerIssues.map {
                        it.toResolvableIssue(input.ortResult, input.howToFixTextProvider)
                    },
                    scanIssues = scanIssues.map {
                        it.toResolvableIssue(input.ortResult, input.howToFixTextProvider)
                    }
                )
            }

            ProjectTable(
                tableRows,
                input.ortResult.getDefinitionFilePathRelativeToAnalyzerRoot(project),
                pathExcludes
            )
        }.orEmpty().toSortedMap(compareBy { it.id })

        // TODO: Use the prefixes up until the first '.' (which below get discarded) for some visual grouping in the
        //       report.
        val labels = input.ortResult.labels.mapKeys { it.key.substringAfter(".") }

        val ruleViolations = input.ortResult.getRuleViolations()
            .map { it.toResolvableViolation(input.ortResult) }
            .sortedWith(VIOLATION_COMPARATOR)

        return ReportTableModel(
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

private val VIOLATION_COMPARATOR = compareBy<ResolvableViolation> { it.isResolved }
    .thenByDescending { it.violation.severity }
    .thenBy { it.violation.rule }
    .thenBy { it.violation.pkg }
    .thenBy { it.violation.license.toString() }
    .thenBy { it.violation.message }
    .thenBy { it.resolutionDescription }

private fun Project.getScopesForDependencies(
    excludes: Excludes,
    navigator: DependencyNavigator
): Map<Identifier, Map<String, List<ScopeExclude>>> {
    val result = mutableMapOf<Identifier, MutableMap<String, List<ScopeExclude>>>()

    navigator.scopeDependencies(this).forEach { (scopeName, dependencies) ->
        dependencies.forEach { dependency ->
            result.getOrPut(dependency) { mutableMapOf() }
                .getOrPut(scopeName) { excludes.findScopeExcludes(scopeName) }
        }
    }

    return result
}

private fun Issue.toResolvableIssue(ortResult: OrtResult, howToFixTextProvider: HowToFixTextProvider): ResolvableIssue {
    val resolutions = ortResult.getResolutionsFor(this)
    return ResolvableIssue(
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
        isResolved = resolutions.isNotEmpty(),
        severity = severity,
        howToFix = howToFixTextProvider.getHowToFixText(this).orEmpty()
    )
}

private fun RuleViolation.toResolvableViolation(ortResult: OrtResult): ResolvableViolation {
    val resolutions = ortResult.getResolutionsFor(this)
    return ResolvableViolation(
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
            val resolvableIssue = issue.toResolvableIssue(input.ortResult, input.howToFixTextProvider)
            ReportTableModel.IssueRow(resolvableIssue, id)
        }
    }.sortedWith(compareByDescending<ReportTableModel.IssueRow> { it.issue.severity }.thenBy { it.id })

    return IssueTable(type, rows)
}
