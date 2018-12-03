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

import com.here.ort.model.Identifier
import com.here.ort.model.OrtIssue
import com.here.ort.model.OrtResult
import com.here.ort.model.config.ScopeExclude
import com.here.ort.model.getAllDetectedLicenses
import com.here.ort.reporter.ResolutionProvider
import com.here.ort.reporter.reporters.ReportTableModel.DependencyRow
import com.here.ort.reporter.reporters.ReportTableModel.ErrorRow
import com.here.ort.reporter.reporters.ReportTableModel.ErrorTable
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
        val resolutions = resolutionProvider.getResolutionsFor(this)
        return ResolvableIssue(
                description = this@toResolvableIssue.toString(),
                resolutionDescription = buildString {
                    if (resolutions.isNotEmpty()) {
                        append(resolutions.joinToString(
                                prefix = "\nResolved by: ") { "${it.reason} - ${it.comment}" }
                        )
                    }
                },
                isResolved = resolutions.isNotEmpty()
        )
    }

    fun mapToReportTableModel(
            ortResult: OrtResult
    ): ReportTableModel {
        val errorSummaryRows = mutableMapOf<Identifier, ErrorRow>()
        val summaryRows = mutableMapOf<Identifier, SummaryRow>()

        requireNotNull(ortResult.analyzer?.result) {
            "The provided ORT result does not contain an analyzer result."
        }

        val analyzerResult = ortResult.analyzer!!.result

        requireNotNull(ortResult.scanner?.results) {
            "The provided ORT result does not contain any scan results."
        }

        val scanRecord = ortResult.scanner!!.results

        val projectTables = analyzerResult.projects.associate { project ->
            val projectExclude = ortResult.repository.config.excludes?.findProjectExclude(project)?.let { exclude ->
                // Only add the project exclude to the model if the whole project is excluded. If only parts of the
                // project are excluded this information will be stored in the rows of the affected dependencies.
                exclude.takeIf { it.isWholeProjectExcluded }
            }

            val allIds = sortedSetOf(project.id)
            project.collectDependencies().mapTo(allIds) { it.id }

            val tableRows = allIds.map { id ->
                val scanResult = scanRecord.scanResults.find { it.id == id }

                val scopes = project.scopes.filter { id in it }.let { scopes ->
                    ortResult.repository.config.excludes
                            ?.scopeExcludesByName(project, scopes)?.toSortedMap()
                            ?: scopes.associateTo(sortedMapOf()) { Pair(it.name, emptyList<ScopeExclude>()) }
                }

                val declaredLicenses = ortResult.getDeclaredLicensesForId(id)
                val detectedLicenses = scanResult.getAllDetectedLicenses()

                val analyzerErrors = project.collectErrors(id).toMutableList()
                analyzerResult.errors[id]?.let {
                    analyzerErrors += it
                }

                val scanErrors = scanResult?.results?.flatMap {
                    it.summary.errors
                }?.distinct() ?: emptyList()

                DependencyRow(
                        id = id,
                        scopes = scopes,
                        declaredLicenses = declaredLicenses,
                        detectedLicenses = detectedLicenses,
                        analyzerErrors = analyzerErrors.map { it.toResolvableIssue() },
                        scanErrors = scanErrors.map { it.toResolvableIssue() }
                ).also { row ->
                    val isRowExcluded = projectExclude != null
                            || (scopes.isNotEmpty() && scopes.all { it.value.isNotEmpty() })

                    val nonExcludedAnalyzerErrors = if (isRowExcluded) emptyList() else row.analyzerErrors
                    val nonExcludedScanErrors = if (isRowExcluded) emptyList() else row.scanErrors

                    val summaryRow = SummaryRow(
                            id = row.id,
                            scopes = sortedMapOf(project.id to row.scopes),
                            declaredLicenses = row.declaredLicenses,
                            detectedLicenses = row.detectedLicenses,
                            analyzerErrors = if (nonExcludedAnalyzerErrors.isNotEmpty())
                                sortedMapOf(project.id to nonExcludedAnalyzerErrors) else sortedMapOf(),
                            scanErrors = if (nonExcludedScanErrors.isNotEmpty())
                                sortedMapOf(project.id to nonExcludedScanErrors) else sortedMapOf()
                    )

                    summaryRows[row.id] = summaryRows[row.id]?.merge(summaryRow) ?: summaryRow

                    val unresolvedAnalyzerErrors = row.analyzerErrors.filterUnresolved()
                    val unresolvedScanErrors = row.scanErrors.filterUnresolved()

                    if ((unresolvedAnalyzerErrors.isNotEmpty() || unresolvedScanErrors.isNotEmpty())
                            && !isRowExcluded) {
                        val errorRow = ErrorRow(
                                id = row.id,
                                analyzerErrors = if (unresolvedAnalyzerErrors.isNotEmpty())
                                    sortedMapOf(project.id to unresolvedAnalyzerErrors) else sortedMapOf(),
                                scanErrors = if (unresolvedScanErrors.isNotEmpty())
                                    sortedMapOf(project.id to unresolvedScanErrors) else sortedMapOf()
                        )

                        errorSummaryRows[row.id] = errorSummaryRows[errorRow.id]?.merge(errorRow) ?: errorRow
                    }
                }
            }

            Pair(project, ProjectTable(tableRows, projectExclude))
        }.toSortedMap()

        val errorSummaryTable = ErrorTable(errorSummaryRows.values.toList().sortedBy { it.id })

        val projectExcludes = ortResult.analyzer?.result?.projects?.let { projects ->
            ortResult.repository.config.excludes?.projectExcludesById(projects)
        } ?: emptyMap()

        val summaryTable = SummaryTable(summaryRows.values.toList().sortedWith(compareBy({
            // Sort excluded rows to the end of the list.
            val allScopes = it.scopes.flatMap { it.value.keys }
            if (allScopes.isEmpty()) {
                // $it is an excluded project.
                projectExcludes[it.id] != null
            } else {
                // $it is an excluded dependency.
                it.scopes.all {
                    projectExcludes[it.key] != null || it.value.values.all { excludes -> excludes.isNotEmpty() }
                }
            }
        }, { it.id })), projectExcludes)

        val metadata = mutableMapOf<String, String>()
        (ortResult.data["job_parameters"] as? Map<*, *>)?.let {
            it.entries.associateTo(metadata) { (key, value) -> key.toString() to value.toString() }
        }
        (ortResult.data["process_parameters"] as? Map<*, *>)?.let {
            it.entries.associateTo(metadata) { (key, value) -> key.toString() to value.toString() }
        }

        val extraColumns = (scanRecord.data["excel_report_extra_columns"] as? List<*>)?.let { extraColumns ->
            extraColumns.map { it.toString() }
        }.orEmpty()

        return ReportTableModel(
                        ortResult.repository.vcsProcessed,
                        ortResult.evaluator?.errors,
                        errorSummaryTable,
                        summaryTable,
                        projectTables,
                        metadata,
                        extraColumns
        )
    }
}
