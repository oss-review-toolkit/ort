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

import java.util.SortedMap

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
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

internal fun Collection<ReportTableModel.ResolvableIssue>.containsUnresolved() = any { !it.isResolved }

internal data class ReportTableModel(
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
     * An [IssueTable] containing all dependencies that caused issues.
     */
    val analyzerIssueSummary: IssueTable,

    /**
     * A [IssueTable] containing all scanner issues.
     */
    val scannerIssueSummary: IssueTable,

    /**
     * A [IssueTable] containing all advisor issues.
     */
    val advisorIssueSummary: IssueTable,

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

    data class IssueTable(
        val type: Type,
        val rows: List<IssueRow>
    ) {
        val errorCount = rows.count { it.issue.severity == Severity.ERROR }
        val warningCount = rows.count { it.issue.severity == Severity.WARNING }
        val hintCount = rows.count { it.issue.severity == Severity.HINT }

        enum class Type {
            ANALYZER,
            SCANNER,
            ADVISOR
        }
    }

    data class IssueRow(
        /**
         * All analyzer issues related to this package, grouped by the [Identifier] of the [Project] they appear in.
         */
        val issue: ResolvableIssue,

        /**
         * The identifier of the package the issue corresponds to.
         */
        val id: Identifier
    )

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
