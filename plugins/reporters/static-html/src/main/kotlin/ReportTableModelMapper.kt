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
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.model.utils.ResolutionProvider
import org.ossreviewtoolkit.plugins.reporters.statichtml.ReportTableModel.DependencyRow
import org.ossreviewtoolkit.plugins.reporters.statichtml.ReportTableModel.IssueRow
import org.ossreviewtoolkit.plugins.reporters.statichtml.ReportTableModel.IssueTable
import org.ossreviewtoolkit.plugins.reporters.statichtml.ReportTableModel.ProjectTable
import org.ossreviewtoolkit.plugins.reporters.statichtml.ReportTableModel.ResolvableIssue
import org.ossreviewtoolkit.plugins.reporters.statichtml.ReportTableModel.ResolvableViolation
import org.ossreviewtoolkit.reporter.HowToFixTextProvider

/**
 * A mapper which converts an [OrtResult] to a [ReportTableModel].
 */
object ReportTableModelMapper {
    fun map(
        ortResult: OrtResult,
        licenseInfoResolver: LicenseInfoResolver,
        resolutionProvider: ResolutionProvider,
        howToFixTextProvider: HowToFixTextProvider
    ): ReportTableModel {
        val issueSummaryRows = mutableMapOf<Identifier, IssueRow>()

        val analyzerResult = ortResult.analyzer?.result
        val excludes = ortResult.getExcludes()

        val projectTables = analyzerResult?.projects?.associateWith { project ->
            val scopesForDependencies = project.getScopesForDependencies(excludes, ortResult.dependencyNavigator)
            val pathExcludes = excludes.findPathExcludes(project, ortResult)

            val allIds = sortedSetOf(project.id)
            allIds += ortResult.dependencyNavigator.projectDependencies(project)

            val projectIssues = ortResult.dependencyNavigator.projectIssues(project)
            val tableRows = allIds.map { id ->
                val scanResults = ortResult.getScanResultsForId(id)

                val resolvedLicenseInfo = licenseInfoResolver.resolveLicenseInfo(id)

                val concludedLicense = resolvedLicenseInfo.licenseInfo.concludedLicenseInfo.concludedLicense
                val declaredLicenses = resolvedLicenseInfo.filter { LicenseSource.DECLARED in it.sources }
                    .sortedBy { it.license.toString() }
                val detectedLicenses = resolvedLicenseInfo.filter { LicenseSource.DETECTED in it.sources }
                    .sortedBy { it.license.toString() }

                val analyzerIssues = projectIssues[id].orEmpty() + analyzerResult.issues[id].orEmpty()

                val scanIssues = scanResults.flatMapTo(mutableSetOf()) {
                    it.summary.issues
                }

                val packageForId = ortResult.getPackage(id)?.metadata ?: ortResult.getProject(id)?.toPackage()

                val row = DependencyRow(
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
                    )?.sorted(),
                    analyzerIssues = analyzerIssues.map {
                        it.toResolvableIssue(resolutionProvider, howToFixTextProvider)
                    },
                    scanIssues = scanIssues.map { it.toResolvableIssue(resolutionProvider, howToFixTextProvider) }
                )

                val isRowExcluded = ortResult.isExcluded(row.id)
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

                row
            }

            ProjectTable(
                tableRows,
                ortResult.getDefinitionFilePathRelativeToAnalyzerRoot(project),
                pathExcludes
            )
        }.orEmpty().toSortedMap(compareBy { it.id })

        val issueSummaryTable = IssueTable(issueSummaryRows.values.sortedBy { it.id })

        // TODO: Use the prefixes up until the first '.' (which below get discarded) for some visual grouping in the
        //       report.
        val labels = ortResult.labels.mapKeys { it.key.substringAfter(".") }

        val ruleViolations = ortResult.getRuleViolations()
            .map { it.toResolvableViolation(resolutionProvider) }
            .sortedWith(VIOLATION_COMPARATOR)

        return ReportTableModel(
            ortResult.repository.vcsProcessed,
            ortResult.repository.config,
            ruleViolations,
            issueSummaryTable,
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

private fun Issue.toResolvableIssue(
    resolutionProvider: ResolutionProvider,
    howToFixTextProvider: HowToFixTextProvider
): ResolvableIssue {
    val resolutions = resolutionProvider.getIssueResolutionsFor(this)
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

private fun RuleViolation.toResolvableViolation(resolutionProvider: ResolutionProvider): ResolvableViolation {
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
