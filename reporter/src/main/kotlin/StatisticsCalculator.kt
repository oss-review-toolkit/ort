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

package org.ossreviewtoolkit.reporter

import java.time.Duration
import java.time.Instant

import kotlin.time.toKotlinDuration

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.IssueResolution
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.config.RuleViolationResolution
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.model.licenses.ResolvedLicenseInfo

/**
 * This class calculates [Statistics] for a given [OrtResult] and the applicable [IssueResolution]s and
 * [RuleViolationResolution]s.
 */
object StatisticsCalculator {
    /**
     * Return the [Statistics] for the given [ortResult].
     */
    fun getStatistics(ortResult: OrtResult, licenseInfoResolver: LicenseInfoResolver, ortConfig: OrtConfiguration) =
        Statistics(
            repositoryConfiguration = getRepositoryConfigurationStatistics(ortResult),
            openIssues = getOpenIssues(ortResult, ortConfig),
            openRuleViolations = getOpenRuleViolations(ortResult, ortConfig),
            openVulnerabilities = getOpenVulnerabilities(ortResult),
            dependencyTree = DependencyTreeStatistics(
                includedProjects = ortResult.getProjects().count { !ortResult.isExcluded(it.id) },
                excludedProjects = ortResult.getProjects().count { ortResult.isExcluded(it.id) },
                includedPackages = ortResult.getPackages().count { !ortResult.isExcluded(it.metadata.id) },
                excludesPackages = ortResult.getPackages().count { ortResult.isExcluded(it.metadata.id) },
                totalTreeDepth = getTreeDepth(ortResult),
                includedTreeDepth = getTreeDepth(ortResult = ortResult, ignoreExcluded = true),
                includedScopes = getIncludedScopes(ortResult),
                excludedScopes = getExcludedScopes(ortResult)
            ),
            licenses = getLicenseStatistics(ortResult, licenseInfoResolver),
            executionDurationInSeconds = getExecutionDurationInSeconds(ortResult)
        )

    private fun getOpenRuleViolations(ortResult: OrtResult, ortConfig: OrtConfiguration): IssueStatistics {
        val openRuleViolations = ortResult.getRuleViolations(omitResolved = true)

        return IssueStatistics(
            errors = openRuleViolations.count { it.severity == Severity.ERROR },
            warnings = openRuleViolations.count { it.severity == Severity.WARNING },
            hints = openRuleViolations.count { it.severity == Severity.HINT },
            severe = openRuleViolations.count { it.severity >= ortConfig.severeRuleViolationThreshold }
        )
    }

    private fun getOpenIssues(ortResult: OrtResult, ortConfig: OrtConfiguration): IssueStatistics {
        val openIssues = ortResult.getOpenIssues(Severity.HINT)

        return IssueStatistics(
            errors = openIssues.count { it.severity == Severity.ERROR },
            warnings = openIssues.count { it.severity == Severity.WARNING },
            hints = openIssues.count { it.severity == Severity.HINT },
            severe = openIssues.count { it.severity >= ortConfig.severeIssueThreshold }
        )
    }

    private fun getOpenVulnerabilities(ortResult: OrtResult): Int =
        ortResult.getVulnerabilities(omitExcluded = true, omitResolved = true).values.flatten().size

    private fun getTreeDepth(ortResult: OrtResult, ignoreExcluded: Boolean = false): Int =
        ortResult
            .getProjects()
            .filterNot { project -> ignoreExcluded && ortResult.isExcluded(project.id) }
            .flatMap { project ->
                val scopes = ortResult.dependencyNavigator.scopeNames(project)
                MutableList(scopes.size) { project }.zip(scopes)
            }.filterNot { (_, scope) -> ignoreExcluded && ortResult.repository.config.excludes.isScopeExcluded(scope) }
            .maxOfOrNull { (project, scope) -> ortResult.dependencyNavigator.dependencyTreeDepth(project, scope) } ?: 0

    private fun getIncludedScopes(ortResult: OrtResult): Set<String> =
        ortResult
            .getProjects()
            .filterNot { project -> ortResult.isExcluded(project.id) }
            .flatMap { project -> ortResult.dependencyNavigator.scopeNames(project) }
            .filterNot { scope -> ortResult.repository.config.excludes.isScopeExcluded(scope) }
            .toSet()

    private fun getExcludedScopes(ortResult: OrtResult): Set<String> =
        ortResult
            .getProjects()
            .flatMap { project -> ortResult.dependencyNavigator.scopeNames(project) }
            .toSet()
            .let { allScopes ->
                allScopes - getIncludedScopes(ortResult)
            }

    private fun getLicenseStatistics(
        ortResult: OrtResult,
        licenseInfoResolver: LicenseInfoResolver
    ): LicenseStatistics {
        fun Set<Identifier>.countLicenses(
            transform: ResolvedLicenseInfo.() -> ResolvedLicenseInfo = { this }
        ): Map<String, Int> =
            flatMap { id ->
                val resolvedLicenseInfo = licenseInfoResolver.resolveLicenseInfo(id)
                transform(resolvedLicenseInfo).map { it.license.toString() }
            }.groupingBy { it }.eachCount()

        val ids = ortResult.getProjectsAndPackages()

        return LicenseStatistics(
            declared = ids.countLicenses { filter(LicenseView.ONLY_DECLARED) },
            detected = ids.countLicenses { filter(LicenseView.ONLY_DETECTED) },
            effective = ortResult.getProjectsAndPackages(omitExcluded = true).countLicenses {
                filterExcluded()
                    .filter(LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED)
                    .applyChoices(ortResult.getPackageLicenseChoices(id))
                    .applyChoices(ortResult.getRepositoryLicenseChoices())
            }
        )
    }

    private fun getExecutionDurationInSeconds(ortResult: OrtResult): Long =
        with(ortResult) {
            listOfNotNull(
                analyzer?.let { secondsBetween(it.startTime, it.endTime) },
                scanner?.let { secondsBetween(it.startTime, it.endTime) },
                advisor?.let { secondsBetween(it.startTime, it.endTime) },
                evaluator?.let { secondsBetween(it.startTime, it.endTime) }
            ).sum()
        }

    private fun getRepositoryConfigurationStatistics(ortResult: OrtResult): RepositoryConfigurationStatistics {
        val config = ortResult.repository.config

        return RepositoryConfigurationStatistics(
            pathExcludes = config.excludes.paths.size,
            scopeExcludes = config.excludes.scopes.size,
            licenseChoices = config.licenseChoices.let {
                it.packageLicenseChoices.size + it.repositoryLicenseChoices.size
            },
            licenseFindingCurations = config.curations.licenseFindings.size,
            issueResolutions = config.resolutions.issues.size,
            ruleViolationResolutions = config.resolutions.ruleViolations.size,
            vulnerabilityResolutions = config.resolutions.vulnerabilities.size
        )
    }
}

private fun secondsBetween(startInclusive: Instant, endInclusive: Instant) =
    Duration.between(startInclusive, endInclusive).toKotlinDuration().inWholeSeconds
