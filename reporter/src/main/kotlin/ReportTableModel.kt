/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import java.util.SortedMap
import java.util.SortedSet

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.licenses.ResolvedLicense
import org.ossreviewtoolkit.utils.common.zipWithCollections
import org.ossreviewtoolkit.utils.common.zipWithDefault
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

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
     * A list containing all evaluator rule violations. `null` if no evaluator result is available.
     */
    val ruleViolations: List<ResolvableViolation>?,

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
     * The labels from [OrtResult.labels].
     */
    val labels: Map<String, String>
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
         * The remote artifact where the source package can be downloaded.
         */
        val sourceArtifact: RemoteArtifact,

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
        val declaredLicenses: List<ResolvedLicense>,

        /**
         * The detected licenses aggregated from all [ScanResult]s for this package.
         */
        val detectedLicenses: List<ResolvedLicense>,

        /**
         * The effective license of the package derived from the licenses of the license sources chosen by a
         * LicenseView, with optional choices applied.
         */
        val effectiveLicense: SpdxExpression?,

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
        fun merge(other: SummaryRow) =
            SummaryRow(
                id = id,
                scopes = scopes.zipWithDefault(other.scopes, sortedMapOf()) { left, right ->
                    left.zipWithCollections(right).toSortedMap()
                }.toSortedMap(),
                concludedLicenses = (concludedLicenses + other.concludedLicenses),
                declaredLicenses = (declaredLicenses + other.declaredLicenses).toSortedSet(),
                detectedLicenses = (detectedLicenses + other.detectedLicenses).toSortedSet(),
                analyzerIssues = analyzerIssues.zipWithCollections(other.analyzerIssues).toSortedMap(),
                scanIssues = scanIssues.zipWithCollections(other.scanIssues).toSortedMap()
            )
    }

    data class IssueTable(
        val rows: List<IssueRow>
    ) {
        val errorCount: Int
        val warningCount: Int
        val hintCount: Int

        init {
            val unresolvedIssues = rows.flatMap {
                it.analyzerIssues.flatMap { (_, issues) -> issues } +
                        it.scanIssues.flatMap { (_, issues) -> issues }
            }.filterNot { it.isResolved }.groupBy { it.severity }

            errorCount = unresolvedIssues[Severity.ERROR].orEmpty().size
            warningCount = unresolvedIssues[Severity.WARNING].orEmpty().size
            hintCount = unresolvedIssues[Severity.HINT].orEmpty().size
        }
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
        fun merge(other: IssueRow): IssueRow =
            IssueRow(
                id = id,
                analyzerIssues = analyzerIssues.zipWithCollections(other.analyzerIssues).toSortedMap(),
                scanIssues = scanIssues.zipWithCollections(other.scanIssues).toSortedMap()
            )
    }

    data class ResolvableIssue(
        val source: String,
        val description: String,
        val resolutionDescription: String,
        val isResolved: Boolean,
        val severity: Severity,
        val howToFix: String
    )

    data class ResolvableViolation(
        val violation: RuleViolation,
        val resolutionDescription: String,
        val isResolved: Boolean
    )
}
