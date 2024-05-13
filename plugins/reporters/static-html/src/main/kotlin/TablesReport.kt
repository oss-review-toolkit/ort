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

internal data class TablesReport(
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
    val ruleViolations: List<TablesReportViolation>?,

    /**
     * An [IssueTable] containing all analyzer issues.
     */
    val analyzerIssues: IssueTable,

    /**
     * An [IssueTable] containing all scanner issues.
     */
    val scannerIssues: IssueTable,

    /**
     * An [IssueTable] containing all advisor issues.
     */
    val advisorIssues: IssueTable,

    /**
     * The [ProjectTable]s containing the dependencies for each [Project].
     */
    val projects: List<ProjectTable>,

    /**
     * The labels from [OrtResult.labels].
     */
    val labels: Map<String, String>
)

internal data class IssueTable(
    val type: Type,
    val rows: List<Row>
) {
    val errorCount = rows.count { it.issue.severity == Severity.ERROR }
    val warningCount = rows.count { it.issue.severity == Severity.WARNING }
    val hintCount = rows.count { it.issue.severity == Severity.HINT }

    enum class Type {
        ANALYZER,
        SCANNER,
        ADVISOR
    }

    data class Row(
        /**
         * The issue of this row represents of the given [type][type].
         */
        val issue: TablesReportIssue,

        /**
         * The identifier of the package the issue corresponds to.
         */
        val id: Identifier
    )
}

internal data class ProjectTable(
    /**
     * The identifier of the project.
     */
    val id: Identifier,

    /**
     * The (processed) VCS info of the project.
     */
    val vcs: VcsInfo,

    /**
     * The dependencies of this project.
     */
    val rows: List<Row>,

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

    data class Row(
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
        val scopes: List<Scope>,

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
        val analyzerIssues: List<TablesReportIssue>,

        /**
         * All scan issues related to this package.
         */
        val scanIssues: List<TablesReportIssue>
    ) {
        /**
         * Return true if and only if this [Row] is excluded by any [ScopeExclude]s
         */
        fun isExcluded(): Boolean = scopes.isNotEmpty() && scopes.all { it.isExcluded() }
    }

    data class Scope(
        /**
         * The name of the scope.
         */
        val name: String,

        /**
         * The excludes matching this scope.
         */
        val excludes: List<ScopeExclude>
    ) {
        /**
         * Return true if an only if this scope is matched by any [ScopeExclude]'s.
         */
        fun isExcluded(): Boolean = excludes.isNotEmpty()
    }
}

internal data class TablesReportIssue(
    val source: String,
    val description: String,
    val resolutionDescription: String,
    val isResolved: Boolean,
    val severity: Severity,
    val howToFix: String
)

internal data class TablesReportViolation(
    val violation: RuleViolation,
    val resolutionDescription: String,
    val isResolved: Boolean
)
