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

import com.here.ort.model.Error
import com.here.ort.model.Identifier
import com.here.ort.model.OrtResult
import com.here.ort.model.Project
import com.here.ort.model.ScanRecord
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.CopyrightBlacklist
import com.here.ort.model.getAllDetectedLicenses
import com.here.ort.model.config.ErrorResolution
import com.here.ort.model.config.ProjectExclude
import com.here.ort.model.config.ScopeExclude
import com.here.ort.reporter.Reporter
import com.here.ort.reporter.ResolutionProvider
import com.here.ort.utils.zipWithDefault

import java.io.File
import java.util.SortedMap
import java.util.SortedSet

/**
 * An abstract [Reporter] that converts the [ScanRecord] to a table representation.
 */
abstract class TableReporter : Reporter() {
    data class TabularScanRecord(
            /**
             * The [VcsInfo] for the scanned project.
             */
            val vcsInfo: VcsInfo,

            /**
             * A list containing all evaluator errors. `null` if no evaluator result is available.
             */
            val evaluatorErrors: List<Error>?,

            /**
             * A [ErrorTable] containing all dependencies that caused errors.
             */
            val errorSummary: ErrorTable,

            /**
             * A [SummaryTable] containing the dependencies of all [Project]s.
             */
            val summary: SummaryTable,

            /**
             * The [ProjectTable]s containing the dependencies for each [Project].
             */
            val projectDependencies: SortedMap<Project, ProjectTable>,

            /**
             * Additional metadata read from the [OrtResult.data] field.
             */
            val metadata: Map<String, String>,

            /**
             * Extra columns that shall be added to the results table by the implementing reporter.
             */
            val extraColumns: List<String>
    )

    data class ProjectTable(
            /**
             * The dependencies of this project.
             */
            val rows: List<DependencyRow>,

            /**
             * Information about if and why the project is excluded.
             */
            val exclude: ProjectExclude? = null
    )

    data class DependencyRow(
            /**
             * The identifier of the package.
             */
            val id: Identifier,

            /**
             * The scopes the package is used in.
             */
            val scopes: SortedMap<String, List<ScopeExclude>>,

            /**
             * The licenses declared by the package.
             */
            val declaredLicenses: SortedSet<String>,

            /**
             * The detected licenses aggregated from all [ScanResult]s for this package.
             */
            val detectedLicenses: SortedSet<String>,

            /**
             * All analyzer errors related to this package.
             */
            val analyzerErrors: List<ResolvableError>,

            /**
             * All scan errors related to this package.
             */
            val scanErrors: List<ResolvableError>
    ) {
        fun merge(other: DependencyRow) =
                DependencyRow(
                        id = id,
                        scopes = scopes.zipWithDefault(other.scopes, emptyList()) { a, b -> a + b }.toSortedMap(),
                        declaredLicenses = (declaredLicenses + other.declaredLicenses).toSortedSet(),
                        detectedLicenses = (detectedLicenses + other.detectedLicenses).toSortedSet(),
                        analyzerErrors = (analyzerErrors + other.analyzerErrors).distinct(),
                        scanErrors = (scanErrors + other.scanErrors).distinct()
                )
    }

    data class SummaryTable(
            val rows: List<SummaryRow>,
            val projectExcludes: Map<Identifier, ProjectExclude?>
    )

    data class SummaryRow(
            /**
             * The identifier of the package.
             */
            val id: Identifier,

            /**
             * The scopes the package is used in, grouped by the [Identifier] of the [Project] they appear in.
             */
            val scopes: SortedMap<Identifier, SortedMap<String, List<ScopeExclude>>>,

            /**
             * The licenses declared by the package.
             */
            val declaredLicenses: SortedSet<String>,

            /**
             * The detected licenses aggregated from all [ScanResult]s for this package.
             */
            val detectedLicenses: SortedSet<String>,

            /**
             * All analyzer errors related to this package, grouped by the [Identifier] of the [Project] they appear in.
             */
            val analyzerErrors: SortedMap<Identifier, List<ResolvableError>>,

            /**
             * All scan errors related to this package, grouped by the [Identifier] of the [Project] they appear in.
             */
            val scanErrors: SortedMap<Identifier, List<ResolvableError>>
    ) {
        fun merge(other: SummaryRow): SummaryRow {
            fun <T> plus(left: List<T>, right: List<T>) = left + right

            return SummaryRow(
                    id = id,
                    scopes = scopes.zipWithDefault(other.scopes, sortedMapOf()) { left, right ->
                        left.zipWithDefault(right, emptyList(), ::plus).toSortedMap()
                    }.toSortedMap(),
                    declaredLicenses = (declaredLicenses + other.declaredLicenses).toSortedSet(),
                    detectedLicenses = (detectedLicenses + other.detectedLicenses).toSortedSet(),
                    analyzerErrors = analyzerErrors.zipWithDefault(other.analyzerErrors, emptyList(), ::plus)
                            .toSortedMap(),
                    scanErrors = scanErrors.zipWithDefault(other.scanErrors, emptyList(), ::plus).toSortedMap()
            )
        }
    }

    data class ErrorTable(
            val rows: List<ErrorRow>
    )

    data class ErrorRow(
            /**
             * The identifier of the package.
             */
            val id: Identifier,

            /**
             * All analyzer errors related to this package, grouped by the [Identifier] of the [Project] they appear in.
             */
            val analyzerErrors: SortedMap<Identifier, List<ResolvableError>>,

            /**
             * All scan errors related to this package, grouped by the [Identifier] of the [Project] they appear in.
             */
            val scanErrors: SortedMap<Identifier, List<ResolvableError>>
    ) {
        fun merge(other: ErrorRow): ErrorRow {
            val plus = { left: List<ResolvableError>, right: List<ResolvableError> -> left + right }

            return ErrorRow(
                    id = id,
                    analyzerErrors = analyzerErrors.zipWithDefault(other.analyzerErrors, emptyList(), plus)
                            .toSortedMap(),
                    scanErrors = scanErrors.zipWithDefault(other.scanErrors, emptyList(), plus).toSortedMap()
            )
        }
    }

    data class ResolvableError(
        val error: Error,
        val resolutions: List<ErrorResolution>
    ) {
        override fun toString() =
                buildString {
                    append(error)
                    if (resolutions.isNotEmpty()) {
                        append(resolutions.joinToString(prefix = "\nResolved by: ") { "${it.reason} - ${it.comment}" })
                    }
                }
    }

    override fun generateReport(
            ortResult: OrtResult,
            resolutionProvider: ResolutionProvider,
            copyrightBlacklist: CopyrightBlacklist,
            outputDir: File,
            postProcessingScript: String?
    ): File {
        fun Error.toResolvableError(): TableReporter.ResolvableError {
            return ResolvableError(this, resolutionProvider.getResolutionsFor(this))
        }

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

            val tableRows = (listOf(project.id) + project.collectDependencyIds()).map { id ->
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
                        analyzerErrors = analyzerErrors.map { it.toResolvableError() },
                        scanErrors = scanErrors.map { it.toResolvableError() }
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

        return generateReport(
                TabularScanRecord(
                        ortResult.repository.vcsProcessed,
                        ortResult.evaluator?.errors,
                        errorSummaryTable,
                        summaryTable,
                        projectTables,
                        metadata,
                        extraColumns
                ),
                outputDir
        )
    }

    abstract fun generateReport(tabularScanRecord: TabularScanRecord, outputDir: File): File
}

fun Collection<TableReporter.ResolvableError>.filterUnresolved() = filter { it.resolutions.isEmpty() }

fun Collection<TableReporter.ResolvableError>.containsUnresolved() = any { it.resolutions.isEmpty() }

fun <K> Map<K, Collection<TableReporter.ResolvableError>>.containsUnresolved() = any { it.value.containsUnresolved() }
