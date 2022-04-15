/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter

import org.ossreviewtoolkit.model.DependencyNavigator
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.model.utils.ResolutionProvider
import org.ossreviewtoolkit.reporter.ReportTableModel.DependencyRow
import org.ossreviewtoolkit.reporter.ReportTableModel.IssueRow
import org.ossreviewtoolkit.reporter.ReportTableModel.IssueTable
import org.ossreviewtoolkit.reporter.ReportTableModel.ProjectTable
import org.ossreviewtoolkit.reporter.ReportTableModel.ResolvableIssue
import org.ossreviewtoolkit.reporter.ReportTableModel.ResolvableViolation
import org.ossreviewtoolkit.reporter.ReportTableModel.SummaryRow
import org.ossreviewtoolkit.reporter.ReportTableModel.SummaryTable

private val VIOLATION_COMPARATOR = compareBy<ResolvableViolation> { it.isResolved }
    .thenByDescending { it.violation.severity }
    .thenBy { it.violation.rule }
    .thenBy { it.violation.pkg }
    .thenBy { it.violation.license.toString() }
    .thenBy { it.violation.message }
    .thenBy { it.resolutionDescription }

private fun Collection<ResolvableIssue>.filterUnresolved() = filter { !it.isResolved }

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

/**
 * A mapper which converts an [OrtIssue] to a [ReportTableModel] view model.
 */
class ReportTableModelMapper(
    private val resolutionProvider: ResolutionProvider,
    private val howToFixTextProvider: HowToFixTextProvider
) {
    private fun OrtIssue.toResolvableIssue(): ResolvableIssue {
        val resolutions = resolutionProvider.getIssueResolutionsFor(this)
        return ResolvableIssue(
            source = this@toResolvableIssue.source,
            description = this@toResolvableIssue.toString(),
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

    private fun RuleViolation.toResolvableViolation(): ResolvableViolation {
        val resolutions = resolutionProvider.getRuleViolationResolutionsFor(this)
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

    fun mapToReportTableModel(
        ortResult: OrtResult,
        licenseInfoResolver: LicenseInfoResolver
    ): ReportTableModel {
        val issueSummaryRows = mutableMapOf<Identifier, IssueRow>()
        val summaryRows = mutableMapOf<Identifier, SummaryRow>()

        val analyzerResult = ortResult.analyzer?.result
        val scanRecord = ortResult.scanner?.results
        val excludes = ortResult.getExcludes()

        val projectTables = analyzerResult?.projects?.associateWith { project ->
            val scopesForDependencies = project.getScopesForDependencies(excludes, ortResult.dependencyNavigator)
            val pathExcludes = excludes.findPathExcludes(project, ortResult)

            val allIds = sortedSetOf(project.id)
            allIds += ortResult.dependencyNavigator.projectDependencies(project)

            val projectIssues = ortResult.dependencyNavigator.projectIssues(project)
            val tableRows = allIds.map { id ->
                val scanResult = scanRecord?.scanResults?.get(id)

                val resolvedLicenseInfo = licenseInfoResolver.resolveLicenseInfo(id)

                val concludedLicense = resolvedLicenseInfo.licenseInfo.concludedLicenseInfo.concludedLicense
                val declaredLicenses = resolvedLicenseInfo.filter { LicenseSource.DECLARED in it.sources }
                    .sortedBy { it.license.toString() }
                val detectedLicenses = resolvedLicenseInfo.filter { LicenseSource.DETECTED in it.sources }
                    .sortedBy { it.license.toString() }

                val analyzerIssues = projectIssues[id].orEmpty() + analyzerResult.issues[id].orEmpty()

                val scanIssues = scanResult?.flatMapTo(mutableSetOf()) {
                    it.summary.issues
                }.orEmpty()

                val packageForId = ortResult.getPackage(id)?.pkg ?: ortResult.getProject(id)?.toPackage()

                DependencyRow(
                    id = id,
                    sourceArtifact = packageForId?.sourceArtifact.orEmpty(),
                    vcsInfo = packageForId?.vcsProcessed.orEmpty(),
                    scopes = scopesForDependencies[id].orEmpty().toSortedMap(),
                    concludedLicense = concludedLicense,
                    declaredLicenses = declaredLicenses,
                    detectedLicenses = detectedLicenses,
                    effectiveLicense = resolvedLicenseInfo.filterExcluded().effectiveLicense(
                        LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED,
                        ortResult.getPackageLicenseChoices(id),
                        ortResult.getRepositoryLicenseChoices()
                    )?.sort(),
                    analyzerIssues = analyzerIssues.map { it.toResolvableIssue() },
                    scanIssues = scanIssues.map { it.toResolvableIssue() }
                ).also { row ->
                    val isRowExcluded = pathExcludes.isNotEmpty()
                            || (row.scopes.isNotEmpty() && row.scopes.all { it.value.isNotEmpty() })

                    val nonExcludedAnalyzerIssues = if (isRowExcluded) emptyList() else row.analyzerIssues
                    val nonExcludedScanIssues = if (isRowExcluded) emptyList() else row.scanIssues

                    val summaryRow = SummaryRow(
                        id = row.id,
                        scopes = sortedMapOf(project.id to row.scopes),
                        concludedLicenses = row.concludedLicense?.let { setOf(it) }.orEmpty(),
                        declaredLicenses = row.declaredLicenses.mapTo(sortedSetOf()) { it.license.toString() },
                        detectedLicenses = row.detectedLicenses.mapTo(sortedSetOf()) { it.license.toString() },
                        analyzerIssues = if (nonExcludedAnalyzerIssues.isNotEmpty()) {
                            sortedMapOf(project.id to nonExcludedAnalyzerIssues)
                        } else {
                            sortedMapOf()
                        },
                        scanIssues = if (nonExcludedScanIssues.isNotEmpty()) {
                            sortedMapOf(project.id to nonExcludedScanIssues)
                        } else {
                            sortedMapOf()
                        }
                    )

                    summaryRows[row.id] = summaryRows[row.id]?.merge(summaryRow) ?: summaryRow

                    val unresolvedAnalyzerIssues = row.analyzerIssues.filterUnresolved()
                    val unresolvedScanIssues = row.scanIssues.filterUnresolved()

                    if ((unresolvedAnalyzerIssues.isNotEmpty() || unresolvedScanIssues.isNotEmpty())
                        && !isRowExcluded
                    ) {
                        val issueRow = IssueRow(
                            id = row.id,
                            analyzerIssues = if (unresolvedAnalyzerIssues.isNotEmpty()) {
                                sortedMapOf(project.id to unresolvedAnalyzerIssues)
                            } else {
                                sortedMapOf()
                            },
                            scanIssues = if (unresolvedScanIssues.isNotEmpty()) {
                                sortedMapOf(project.id to unresolvedScanIssues)
                            } else {
                                sortedMapOf()
                            }
                        )

                        issueSummaryRows[row.id] = issueSummaryRows[issueRow.id]?.merge(issueRow) ?: issueRow
                    }
                }
            }

            ProjectTable(
                tableRows,
                ortResult.getDefinitionFilePathRelativeToAnalyzerRoot(project),
                pathExcludes
            )
        }.orEmpty().toSortedMap()

        val issueSummaryTable = IssueTable(issueSummaryRows.values.toList().sortedBy { it.id })

        val summaryTable = SummaryTable(
            // Sort excluded rows to the end of the list.
            summaryRows.values.toList().sortedWith(compareBy({ ortResult.isExcluded(it.id) }, { it.id }))
        )

        // TODO: Use the prefixes up until the first '.' (which below get discarded) for some visual grouping in the
        // report.
        val labels = ortResult.labels.mapKeys { it.key.substringAfter(".") }

        val ruleViolations = ortResult.getRuleViolations()
            .map { it.toResolvableViolation() }
            .sortedWith(VIOLATION_COMPARATOR)

        return ReportTableModel(
            ortResult.repository.vcsProcessed,
            ortResult.repository.config,
            ruleViolations,
            issueSummaryTable,
            summaryTable,
            projectTables,
            labels
        )
    }
}
