/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter.utils

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.IssueResolution
import org.ossreviewtoolkit.model.config.RuleViolationResolution
import org.ossreviewtoolkit.model.utils.ResolutionProvider
import org.ossreviewtoolkit.model.utils.collectLicenseFindings
import org.ossreviewtoolkit.reporter.model.DependencyTreeStatistics
import org.ossreviewtoolkit.reporter.model.IssueStatistics
import org.ossreviewtoolkit.reporter.model.LicenseStatistics
import org.ossreviewtoolkit.reporter.model.Statistics

/**
 * This class calculates [Statistics] for a given [OrtResult] and applicable [IssueResolution]s and applicable
 * [RuleViolationResolution]s.
 */
internal class StatisticsCalculator {
    /**
     * Return the [Statistics] for the given [ortResult].
     */
    fun getStatistics(ortResult: OrtResult, resolutionProvider: ResolutionProvider) = Statistics(
        openIssues = getOpenIssues(ortResult, resolutionProvider),
        openRuleViolations = getOpenRuleViolations(ortResult, resolutionProvider),
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
        licenses = getLicenseStatistics(ortResult)
    )

    private fun getOpenRuleViolations(ortResult: OrtResult, resolutionProvider: ResolutionProvider): IssueStatistics {
        val openPolicyViolations = ortResult
            .getRuleViolations()
            .filter { policyViolation -> resolutionProvider.getRuleViolationResolutionsFor(policyViolation).isEmpty() }

        return IssueStatistics(
            errors = openPolicyViolations.count { it.severity == Severity.ERROR },
            warnings = openPolicyViolations.count { it.severity == Severity.WARNING },
            hints = openPolicyViolations.count { it.severity == Severity.HINT }
        )
    }

    private fun getOpenIssues(ortResult: OrtResult, resolutionProvider: ResolutionProvider): IssueStatistics {
        val openIssues = ortResult
            .collectIssues()
            .filterNot { (id, _) -> ortResult.isExcluded(id) }
            .values
            .flatten()
            .filter { issue -> resolutionProvider.getIssueResolutionsFor(issue).isEmpty() }

        return IssueStatistics(
            errors = openIssues.count { it.severity == Severity.ERROR },
            warnings = openIssues.count { it.severity == Severity.WARNING },
            hints = openIssues.count { it.severity == Severity.HINT }
        )
    }

    private fun getTreeDepth(ortResult: OrtResult, ignoreExcluded: Boolean = false): Int =
        ortResult
            .getProjects()
            .filterNot { project -> ignoreExcluded && ortResult.isExcluded(project.id) }
            .flatMap { project -> project.scopes }
            .filterNot { scope -> ignoreExcluded && ortResult.repository.config.excludes.isScopeExcluded(scope) }
            .map { scope -> scope.getDependencyTreeDepth() }
            .max() ?: 0

    private fun getIncludedScopes(ortResult: OrtResult): Set<String> =
        ortResult
            .getProjects()
            .filterNot { project -> ortResult.isExcluded(project.id) }
            .flatMap { project -> project.scopes }
            .filterNot { scope -> ortResult.repository.config.excludes.isScopeExcluded(scope) }
            .map { scope -> scope.name }
            .toSet()

    private fun getExcludedScopes(ortResult: OrtResult): Set<String> =
        ortResult
            .getProjects()
            .flatMap { project -> project.scopes }
            .map { scope -> scope.name }
            .toSet()
            .let { allScopes ->
                allScopes - getIncludedScopes(ortResult)
            }

    private fun getLicenseStatistics(ortResult: OrtResult): LicenseStatistics {
        val declaredLicenses = sortedMapOf<String, Int>()

        ortResult.analyzer?.result?.let { analyzerResult ->
            analyzerResult.projects.forEach { project ->
                project.declaredLicensesProcessed.allLicenses.forEach { license ->
                    declaredLicenses[license] = declaredLicenses.getOrDefault(license, 0) + 1
                }
            }

            analyzerResult.packages.forEach { pkg ->
                pkg.pkg.declaredLicensesProcessed.allLicenses.forEach { license ->
                    declaredLicenses[license] = declaredLicenses.getOrDefault(license, 0) + 1
                }
            }
        }

        val packageLicenses = ortResult.collectLicenseFindings().mapValues { (_, findingsMap) ->
            findingsMap.mapTo(mutableSetOf()) { it.key.license.toString() }
        }

        val detectedLicenses = packageLicenses.flatMap { it.value }.groupingBy { it }.eachCount().toSortedMap()

        return LicenseStatistics(
            declared = declaredLicenses,
            detected = detectedLicenses
        )
    }
}
