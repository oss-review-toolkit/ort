/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.annotation.JsonSerialize

import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.IssueResolution
import org.ossreviewtoolkit.model.config.LicenseChoices
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.RuleViolationResolution
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.config.VulnerabilityResolution
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability
import org.ossreviewtoolkit.utils.ort.StringSortedSetConverter

/**
 * A class containing statistics for an [OrtResult]. All properties are calculated lazily on first access so that
 * expensive calculations, like for [dependencyTree], are only performed if a property is actually accessed, e.g. by
 * a reporter template.
 */
class Statistics(
    private val ortResult: OrtResult,
    private val licenseInfoResolver: LicenseInfoResolver,
    private val ortConfig: OrtConfiguration
) {
    /**
     * Statistics for the repository configuration.
     */
    val repositoryConfiguration: RepositoryConfigurationStatistics
        by lazy { StatisticsCalculator.getRepositoryConfigurationStatistics(ortResult) }

    /**
     * Statistics for the resolved configuration.
     */
    val resolvedConfiguration: ResolvedConfigurationStatistics
        by lazy { StatisticsCalculator.getResolvedConfigurationStatistics(ortResult) }

    /**
     * The number of [Issue]s by severity which are not resolved and not excluded.
     */
    val openIssues: IssueStatistics by lazy { StatisticsCalculator.getOpenIssues(ortResult, ortConfig) }

    /**
     * The number of [RuleViolation]s by severity which are not resolved.
     */
    val openRuleViolations: IssueStatistics
        by lazy { StatisticsCalculator.getOpenRuleViolations(ortResult, ortConfig) }

    /**
     * The number of [Vulnerabilities][Vulnerability] which are not resolved and not excluded.
     */
    val openVulnerabilities: Int by lazy { StatisticsCalculator.getOpenVulnerabilities(ortResult) }

    /**
     * Statistics for the dependency tree.
     */
    val dependencyTree: DependencyTreeStatistics
        by lazy { StatisticsCalculator.getDependencyTreeStatistics(ortResult) }

    /**
     * Statistics of used licenses.
     */
    val licenses: LicenseStatistics
        by lazy { StatisticsCalculator.getLicenseStatistics(ortResult, licenseInfoResolver) }

    /**
     * The sum of the execution time of all stages in seconds.
     */
    val executionDurationInSeconds: Long by lazy { StatisticsCalculator.getExecutionDurationInSeconds(ortResult) }
}

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
    val hints: Int,

    /**
     * The number of severe issues with regard to the configured threshold.
     */
    val severe: Int
)

/**
 * A class containing statistics about the dependency trees.
 */
class DependencyTreeStatistics(
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
     * The set of scope names which have at least one not excluded corresponding [Scope].
     */
    @JsonSerialize(converter = StringSortedSetConverter::class)
    val includedScopes: Set<String>,

    /**
     * The set of scope names which do not have a single not excluded corresponding [Scope].
     */
    @JsonSerialize(converter = StringSortedSetConverter::class)
    val excludedScopes: Set<String>,

    private val ortResult: OrtResult
) {
    /**
     * The total depth of the deepest tree in the forest.
     */
    val totalTreeDepth: Int by lazy { StatisticsCalculator.getTreeDepth(ortResult) }

    /**
     * The depth of the deepest tree in the forest disregarding excluded tree nodes.
     */
    val includedTreeDepth: Int by lazy { StatisticsCalculator.getTreeDepth(ortResult, ignoreExcluded = true) }
}

/**
 * A class containing statistics about licenses.
 */
data class LicenseStatistics(
    /**
     * All declared licenses, mapped to the number of [Project]s and [Package]s they are declared in.
     */
    @JsonPropertyOrder(alphabetic = true)
    val declared: Map<String, Int>,

    /**
     * All detected licenses, mapped to the number of [Project]s and [Package]s they were detected in.
     */
    @JsonPropertyOrder(alphabetic = true)
    val detected: Map<String, Int>,

    /**
     * All effective licenses, mapped to the number of non-excluded [Project]s and [Package]s they apply to.
     */
    @JsonPropertyOrder(alphabetic = true)
    val effective: Map<String, Int>
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
     * The number of [PackageConfiguration]s.
     */
    val packageConfigurations: Int,

    /**
     * The number of [PackageCuration]s.
     */
    val packageCurations: Int,

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

/**
 * A class containing statistics about the resolved configuration
 */
data class ResolvedConfigurationStatistics(
    /**
     * The number of [LicenseChoices].
     */
    val licenseChoices: Int,

    /**
     * The number of [PackageConfiguration]s.
     */
    val packageConfigurations: Int,

    /**
     * The number of [PackageCuration]s.
     */
    val packageCurations: Int,

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
