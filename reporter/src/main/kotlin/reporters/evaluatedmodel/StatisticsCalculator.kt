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

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.IssueResolution
import org.ossreviewtoolkit.model.config.RuleViolationResolution
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.model.utils.ResolutionProvider

/**
 * This class calculates [Statistics] for a given [OrtResult] and the applicable [IssueResolution]s and
 * [RuleViolationResolution]s.
 */
internal class StatisticsCalculator {
    /**
     * Return the [Statistics] for the given [ortResult].
     */
    fun getStatistics(
        ortResult: OrtResult,
        resolutionProvider: ResolutionProvider,
        licenseInfoResolver: LicenseInfoResolver
    ) = Statistics(
        repositoryConfiguration = getRepositoryConfigurationStatistics(ortResult),
        openIssues = getOpenIssues(ortResult, resolutionProvider),
        openRuleViolations = getOpenRuleViolations(ortResult, resolutionProvider),
        openVulnerabilities = getOpenVulnerabilities(ortResult, resolutionProvider),
        dependencyTree = DependencyTreeStatistics(
            includedProjects = ortResult.getProjects().count { !ortResult.isExcluded(it.id) },
            excludedProjects = ortResult.getProjects().count { ortResult.isExcluded(it.id) },
            includedPackages = ortResult.getPackages().count { !ortResult.isExcluded(it.pkg.id) },
            excludesPackages = ortResult.getPackages().count { ortResult.isExcluded(it.pkg.id) },
            totalTreeDepth = getTreeDepth(ortResult),
            includedTreeDepth = getTreeDepth(ortResult = ortResult, ignoreExcluded = true),
            includedScopes = getIncludedScopes(ortResult).toSortedSet(),
            excludedScopes = getExcludedScopes(ortResult).toSortedSet()
        ),
        licenses = getLicenseStatistics(ortResult, licenseInfoResolver)
    )

    private fun getOpenRuleViolations(ortResult: OrtResult, resolutionProvider: ResolutionProvider): IssueStatistics {
        val openRuleViolations = ortResult.getRuleViolations().filterNot { resolutionProvider.isResolved(it) }

        return IssueStatistics(
            errors = openRuleViolations.count { it.severity == Severity.ERROR },
            warnings = openRuleViolations.count { it.severity == Severity.WARNING },
            hints = openRuleViolations.count { it.severity == Severity.HINT }
        )
    }

    private fun getOpenIssues(ortResult: OrtResult, resolutionProvider: ResolutionProvider): IssueStatistics {
        val openIssues = ortResult.getOpenIssues(Severity.HINT).filterNot { resolutionProvider.isResolved(it) }

        return IssueStatistics(
            errors = openIssues.count { it.severity == Severity.ERROR },
            warnings = openIssues.count { it.severity == Severity.WARNING },
            hints = openIssues.count { it.severity == Severity.HINT }
        )
    }

    private fun getOpenVulnerabilities(ortResult: OrtResult, resolutionProvider: ResolutionProvider): Int =
        ortResult.getVulnerabilities(omitExcluded = true).values.flatten()
            .filterNot { resolutionProvider.isResolved(it) }.size

    private fun getTreeDepth(ortResult: OrtResult, ignoreExcluded: Boolean = false): Int =
        ortResult
            .getProjects()
            .filterNot { project -> ignoreExcluded && ortResult.isExcluded(project.id) }
            .flatMap { project ->
                val scopes = ortResult.dependencyNavigator.scopeNames(project)
                MutableList(scopes.size) { project }.zip(scopes)
            }.filterNot { (_, scope) -> ignoreExcluded && ortResult.repository.config.excludes.isScopeExcluded(scope) }
            .map { (project, scope) -> ortResult.dependencyNavigator.dependencyTreeDepth(project, scope) }
            .maxOrNull() ?: 0

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
        val ids = ortResult.collectProjectsAndPackages()

        fun countLicenses(view: LicenseView): SortedMap<String, Int> =
            ids.flatMap { id ->
                licenseInfoResolver.resolveLicenseInfo(id).filter(view).map { it.license.toString() }
            }.groupingBy { it }.eachCount().toSortedMap()

        val declaredLicenses = countLicenses(LicenseView.ONLY_DECLARED)
        val detectedLicenses = countLicenses(LicenseView.ONLY_DETECTED)

        return LicenseStatistics(
            declared = declaredLicenses,
            detected = detectedLicenses
        )
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
