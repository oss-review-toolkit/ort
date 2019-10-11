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

import com.here.ort.model.Identifier
import com.here.ort.model.OrtIssue
import com.here.ort.model.OrtResult
import com.here.ort.model.RuleViolation
import com.here.ort.reporter.ResolutionProvider
import com.here.ort.reporter.reporters.ReportTableModel.DependencyRow
import com.here.ort.reporter.reporters.ReportTableModel.IssueRow
import com.here.ort.reporter.reporters.ReportTableModel.IssueTable
import com.here.ort.reporter.reporters.ReportTableModel.ProjectTable
import com.here.ort.reporter.reporters.ReportTableModel.ResolvableIssue
import com.here.ort.reporter.reporters.ReportTableModel.SummaryRow
import com.here.ort.reporter.reporters.ReportTableModel.SummaryTable

private fun Collection<ResolvableIssue>.filterUnresolved() = filter { !it.isResolved }

/**
 * A mapper which converts an [OrtIssue] to a [ReportTableModel] view model.
 */
class ReportTableModelMapper(private val resolutionProvider: ResolutionProvider) {
    private fun OrtIssue.toResolvableIssue(): ResolvableIssue {
        val resolutions = resolutionProvider.getErrorResolutionsFor(this)
        return ResolvableIssue(
            source = this@toResolvableIssue.source,
            description = this@toResolvableIssue.toString(),
            resolutionDescription = buildString {
                if (resolutions.isNotEmpty()) {
                    append(resolutions.joinToString(prefix = "\nResolved by: ") {
                        "${it.reason} - ${it.comment}"
                    })
                }
            },
            isResolved = resolutions.isNotEmpty(),
            severity = severity
        )
    }

    private fun RuleViolation.toResolvableEvaluatorIssue(): ReportTableModel.ResolvableViolation {
        val resolutions = resolutionProvider.getRuleViolationResolutionsFor(this)
        return ReportTableModel.ResolvableViolation(
            violation = this,
            resolutionDescription = buildString {
                if (resolutions.isNotEmpty()) {
                    append(resolutions.joinToString(prefix = "\nResolved by: ") {
                        "${it.reason} - ${it.comment}"
                    })
                }
            },
            isResolved = resolutions.isNotEmpty()
        )
    }

    fun mapToReportTableModel(
        ortResult: OrtResult
    ): ReportTableModel {
        val issueSummaryRows = mutableMapOf<Identifier, IssueRow>()
        val summaryRows = mutableMapOf<Identifier, SummaryRow>()

        requireNotNull(ortResult.analyzer?.result) {
            "The provided ORT result does not contain an analyzer result."
        }

        val analyzerResult = ortResult.analyzer!!.result
        val excludes = ortResult.getExcludes()

        val scanRecord = ortResult.scanner?.results
        val licenseFindings = ortResult.collectLicenseFindings()

        val projectTables = analyzerResult.projects.associateWith { project ->
            val pathExcludes = excludes.findPathExcludes(project, ortResult)

            val allIds = sortedSetOf(project.id)
            project.collectDependencies().mapTo(allIds) { it.id }

            val tableRows = allIds.map { id ->
                val scanResult = scanRecord?.scanResults?.find { it.id == id }

                val scopes = project.scopes.filter { id in it }.let { scopes ->
                    excludes.scopeExcludesByName(scopes).toSortedMap()
                }

                val concludedLicense = ortResult.getConcludedLicensesForId(id)
                val declaredLicenses = ortResult.getDeclaredLicensesForId(id)
                val detectedLicenses = licenseFindings[id]?.toSortedMap(compareBy { it.license }) ?: sortedMapOf()

                val analyzerIssues = project.collectErrors(id).toMutableList()
                analyzerResult.errors[id]?.let {
                    analyzerIssues += it
                }

                val scanIssues = scanResult?.results?.flatMap {
                    it.summary.errors
                }?.distinct() ?: emptyList()

                DependencyRow(
                    id = id,
                    vcsInfo = ortResult.getUncuratedPackageById(id)!!.vcsProcessed,
                    scopes = scopes,
                    concludedLicense = concludedLicense,
                    declaredLicenses = declaredLicenses,
                    detectedLicenses = detectedLicenses,
                    analyzerIssues = analyzerIssues.map { it.toResolvableIssue() },
                    scanIssues = scanIssues.map { it.toResolvableIssue() }
                ).also { row ->
                    val isRowExcluded = pathExcludes.isNotEmpty()
                            || (scopes.isNotEmpty() && scopes.all { it.value.isNotEmpty() })

                    val nonExcludedAnalyzerIssues = if (isRowExcluded) emptyList() else row.analyzerIssues
                    val nonExcludedScanIssues = if (isRowExcluded) emptyList() else row.scanIssues

                    val summaryRow = SummaryRow(
                        id = row.id,
                        scopes = sortedMapOf(project.id to row.scopes),
                        concludedLicenses = row.concludedLicense?.let { setOf(it) } ?: emptySet(),
                        declaredLicenses = row.declaredLicenses,
                        detectedLicenses = row.detectedLicenses.mapTo(sortedSetOf()) { it.key.license },
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
        }.toSortedMap()

        val issueSummaryTable = IssueTable(issueSummaryRows.values.toList().sortedBy { it.id })

        val summaryTable = SummaryTable(
            // Sort excluded rows to the end of the list.
            summaryRows.values.toList().sortedWith(compareBy({ ortResult.isExcluded(it.id) }, { it.id }))
        )

        val metadata = mutableMapOf<String, String>()
        (ortResult.data["job_parameters"] as? Map<*, *>)?.let {
            it.entries.associateTo(metadata) { (key, value) -> key.toString() to value.toString() }
        }
        (ortResult.data["process_parameters"] as? Map<*, *>)?.let {
            it.entries.associateTo(metadata) { (key, value) -> key.toString() to value.toString() }
        }

        val extraColumns = (ortResult.data["excel_report_extra_columns"] as? List<*>)?.let { extraColumns ->
            extraColumns.map { it.toString() }
        }.orEmpty()

        val ruleViolations = ortResult.evaluator?.let {
            it.violations.map { it.toResolvableEvaluatorIssue() }
        }?.sortedWith(compareBy(
            { it.isResolved },
            { it.violation.severity },
            { it.violation.rule },
            { it.violation.pkg },
            { it.violation.license },
            { it.violation.message },
            { it.resolutionDescription }
        )) ?: emptyList()

        return ReportTableModel(
            ortResult.repository.vcsProcessed,
            ortResult.repository.config,
            ruleViolations,
            issueSummaryTable,
            summaryTable,
            projectTables,
            metadata,
            extraColumns
        )
    }
}
