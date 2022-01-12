/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter.reporters.evaluatedmodel

import java.util.SortedMap
import java.util.SortedSet

import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.Vulnerability
import org.ossreviewtoolkit.model.config.IssueResolution
import org.ossreviewtoolkit.model.config.LicenseChoices
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.RuleViolationResolution
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.config.VulnerabilityResolution

/**
 * A class containing statistics for an [OrtResult].
 */
data class Statistics(
    /**
     * Statistics for the repository configuration.
     */
    val repositoryConfiguration: RepositoryConfigurationStatistics,

    /**
     * The number of [OrtIssue]s by severity which are not resolved and not excluded.
     */
    val openIssues: IssueStatistics,

    /**
     * The number of [RuleViolation]s by severity which are not resolved.
     */
    val openRuleViolations: IssueStatistics,

    /**
     * The number of [Vulnerabilities][Vulnerability] which are not resolved and not excluded.
     */
    val openVulnerabilities: Int,

    /**
     * Statistics for the dependency tree.
     */
    val dependencyTree: DependencyTreeStatistics,

    /**
     * Statistics of used licenses.
     */
    val licenses: LicenseStatistics
)

/**
 * A class containing the amount of issues per severity.
 */
data class IssueStatistics(
    /**
     * The number of issues with [Severity.ERROR] as severity.
     */
    val errors: Int,

    /**
     * The number of issues with [Severity.WARNING] as severity.
     */
    val warnings: Int,

    /**
     * The number of issues with [Severity.HINT] as severity.
     */
    val hints: Int
)

/**
 * A class containing statistics about the dependency trees.
 */
data class DependencyTreeStatistics(
    /**
     * The number of included [Project]s.
     */
    val includedProjects: Int,

    /**
     * The number of excluded [Project]s.
     */
    val excludedProjects: Int,

    /**
     * The number of included [Package]s.
     */
    val includedPackages: Int,

    /**
     * The number of excluded [Package]s.
     */
    val excludesPackages: Int,

    /**
     * The total depth of the deepest tree in the forest.
     */
    val totalTreeDepth: Int,

    /**
     * The depth of the deepest tree in the forest disregarding excluded tree nodes.
     */
    val includedTreeDepth: Int,

    /**
     * The set of scope names which have at least one not excluded corresponding [Scope].
     */
    val includedScopes: SortedSet<String>,

    /**
     * The set of scope names which do not have a single not excluded corresponding [Scope].
     */
    val excludedScopes: SortedSet<String>
)

/**
 * A class containing statistics about licenses.
 */
data class LicenseStatistics(
    /**
     * All declared licenses, mapped to the number of [Project]s and [Package]s they are declared in.
     */
    val declared: SortedMap<String, Int>,

    /**
     * All detected licenses, mapped to the number of [Project]s and [Package]s they were detected in.
     */
    val detected: SortedMap<String, Int>
)

/**
 * A class containing statistics about the repository configuration
 */
data class RepositoryConfigurationStatistics(
    /**
     * The number of [PathExclude]s.
     */
    val pathExcludes: Int,

    /**
     * The number of [ScopeExclude]s.
     */
    val scopeExcludes: Int,

    /**
     * The number of [LicenseChoices].
     */
    val licenseChoices: Int,

    /**
     * The number of [LicenseFindingCuration]s.
     */
    val licenseFindingCurations: Int,

    /**
     * The number of [IssueResolution]s.
     */
    val issueResolutions: Int,

    /**
     * The number of [RuleViolationResolution]s.
     */
    val ruleViolationResolutions: Int,

    /**
     * The number of [VulnerabilityResolution]s.
     */
    val vulnerabilityResolutions: Int
)
