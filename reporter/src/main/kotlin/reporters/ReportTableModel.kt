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
import com.here.ort.model.LicenseFindings
import com.here.ort.model.OrtResult
import com.here.ort.model.Project
import com.here.ort.model.RuleViolation
import com.here.ort.model.ScanResult
import com.here.ort.model.Severity
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.PathExclude
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.config.ScopeExclude
import com.here.ort.spdx.SpdxExpression
import com.here.ort.utils.zipWithDefault

import java.util.SortedMap
import java.util.SortedSet

fun Collection<ReportTableModel.ResolvableIssue>.containsUnresolved() = any { !it.isResolved }

fun <K> Map<K, Collection<ReportTableModel.ResolvableIssue>>.containsUnresolved() =
    any { it.value.containsUnresolved() }

data class ReportTableModel(
    /**
     * The [VcsInfo] for the scanned project.
     */
    val vcsInfo: VcsInfo,

    /**
     * The [RepositoryConfiguration] of the scanned project.
     */
    val config: RepositoryConfiguration,

    /**
     * A list containing all evaluator issues. `null` if no evaluator result is available.
     */
    val evaluatorIssues: List<ResolvableViolation>?,

    /**
     * A [IssueTable] containing all dependencies that caused issues.
     */
    val issueSummary: IssueTable,

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
) {
    data class ProjectTable(
        /**
         * The dependencies of this project.
         */
        val rows: List<DependencyRow>,

        /**
         * The path to the directory containing the definition file of the project, relative to the analyzer root,
         * see [OrtResult.getDefinitionFilePathRelativeToAnalyzerRoot].
         */
        val fullDefinitionFilePath: String,

        /**
         * Information about if and why the project is excluded by [PathExclude]s.
         */
        val pathExcludes: List<PathExclude>
    ) {
        fun isExcluded() = pathExcludes.isNotEmpty()
    }

    data class DependencyRow(
        /**
         * The identifier of the package.
         */
        val id: Identifier,

        /**
         * The VCS information of the package.
         */
        val vcsInfo: VcsInfo,

        /**
         * The scopes the package is used in.
         */
        val scopes: SortedMap<String, List<ScopeExclude>>,

        /**
         * The concluded license of the package.
         */
        val concludedLicense: SpdxExpression?,

        /**
         * The licenses declared by the package.
         */
        val declaredLicenses: SortedSet<String>,

        /**
         * The detected licenses aggregated from all [ScanResult]s for this package.
         */
        val detectedLicenses: SortedMap<LicenseFindings, List<PathExclude>>,

        /**
         * All analyzer issues related to this package.
         */
        val analyzerIssues: List<ResolvableIssue>,

        /**
         * All scan issues related to this package.
         */
        val scanIssues: List<ResolvableIssue>
    )

    data class SummaryTable(
        val rows: List<SummaryRow>
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
         * The concluded licenses of the package.
         */
        val concludedLicenses: Set<SpdxExpression>,

        /**
         * The licenses declared by the package.
         */
        val declaredLicenses: SortedSet<String>,

        /**
         * The detected licenses aggregated from all [ScanResult]s for this package.
         */
        val detectedLicenses: SortedSet<String>,

        /**
         * All analyzer issues related to this package, grouped by the [Identifier] of the [Project] they appear in.
         */
        val analyzerIssues: SortedMap<Identifier, List<ResolvableIssue>>,

        /**
         * All scan issues related to this package, grouped by the [Identifier] of the [Project] they appear in.
         */
        val scanIssues: SortedMap<Identifier, List<ResolvableIssue>>
    ) {
        fun merge(other: SummaryRow): SummaryRow {
            fun <T> plus(left: List<T>, right: List<T>) = left + right

            return SummaryRow(
                id = id,
                scopes = scopes.zipWithDefault(other.scopes, sortedMapOf()) { left, right ->
                    left.zipWithDefault(right, emptyList(), ::plus).toSortedMap()
                }.toSortedMap(),
                concludedLicenses = (concludedLicenses + other.concludedLicenses),
                declaredLicenses = (declaredLicenses + other.declaredLicenses).toSortedSet(),
                detectedLicenses = (detectedLicenses + other.detectedLicenses).toSortedSet(),
                analyzerIssues = analyzerIssues.zipWithDefault(other.analyzerIssues, emptyList(), ::plus)
                    .toSortedMap(),
                scanIssues = scanIssues.zipWithDefault(other.scanIssues, emptyList(), ::plus).toSortedMap()
            )
        }
    }

    data class IssueTable(
        val rows: List<IssueRow>
    ) {
        val unresolvedIssues = rows.flatMap {
            it.analyzerIssues.flatMap { (_, issues) -> issues } + it.scanIssues.flatMap { (_, issues) -> issues }
        }.filterNot { it.isResolved }.groupBy { it.severity }

        val errorCount = unresolvedIssues[Severity.ERROR].orEmpty().size
        val warningCount = unresolvedIssues[Severity.WARNING].orEmpty().size
        val hintCount = unresolvedIssues[Severity.HINT].orEmpty().size
    }

    data class IssueRow(
        /**
         * The identifier of the package.
         */
        val id: Identifier,

        /**
         * All analyzer issues related to this package, grouped by the [Identifier] of the [Project] they appear in.
         */
        val analyzerIssues: SortedMap<Identifier, List<ResolvableIssue>>,

        /**
         * All scan issues related to this package, grouped by the [Identifier] of the [Project] they appear in.
         */
        val scanIssues: SortedMap<Identifier, List<ResolvableIssue>>
    ) {
        fun merge(other: IssueRow): IssueRow {
            val plus = { left: List<ResolvableIssue>, right: List<ResolvableIssue> -> left + right }

            return IssueRow(
                id = id,
                analyzerIssues = analyzerIssues.zipWithDefault(other.analyzerIssues, emptyList(), plus)
                    .toSortedMap(),
                scanIssues = scanIssues.zipWithDefault(other.scanIssues, emptyList(), plus).toSortedMap()
            )
        }
    }

    data class ResolvableIssue(
        val source: String,
        val description: String,
        val resolutionDescription: String,
        val isResolved: Boolean,
        val severity: Severity
    )

    data class ResolvableViolation(
        val violation: RuleViolation,
        val resolutionDescription: String,
        val isResolved: Boolean
    )
}
