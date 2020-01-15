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

package com.here.ort.model.utils

import com.here.ort.model.DependencyTreeStats
import com.here.ort.model.IssueStats
import com.here.ort.model.OrtResult
import com.here.ort.model.Severity
import com.here.ort.model.Stats

/**
 * This class calculates [Stats] for an [OrtResult].
 *
 * TODO: technical issue counts should adhere to excludes and resolutions.
 * Which makes this whole Stats approach questionable.
 */
class StatsCalculator {
    /**
     * Return the [Stats] for the given [ortResult].
     */
    fun getStats(ortResult: OrtResult) = Stats(
        technicalIssues = getTechnicalIssueStats(ortResult),
        policyViolations = getPolicyViolationStats(ortResult),
        dependencyTree = DependencyTreeStats(
            includedProjects = ortResult.getProjects().count { !ortResult.isExcluded(it.id) },
            excludedProjects = ortResult.getProjects().count { ortResult.isExcluded(it.id) },
            includedPackages = ortResult.getPackages().count { !ortResult.isExcluded(it.pkg.id) },
            excludesPackages = ortResult.getPackages().count { ortResult.isExcluded(it.pkg.id) },
            totalTreeDepth = getTreeDepth(ortResult),
            includedTreeDepth = getTreeDepth(ortResult = ortResult, ignoreExcluded = true),
            includedScopes = getIncludedScopes(ortResult).toSortedSet(),
            excludedScopes = getExcludedScopes(ortResult).toSortedSet()
        )
    )

    private fun getPolicyViolationStats(ortResult: OrtResult): IssueStats {
        val policyViolations = ortResult.getRuleViolations()

        return IssueStats(
            errors = policyViolations.count { it.severity == Severity.ERROR },
            warnings = policyViolations.count { it.severity == Severity.WARNING },
            hints = policyViolations.count { it.severity == Severity.HINT }
        )
    }

    private fun getTechnicalIssueStats(ortResult: OrtResult): IssueStats {
        val issues = ortResult
            .collectErrors()
            .filterNot { (id, _) -> ortResult.isExcluded(id) }
            .values
            .flatten()

        // TODO: Adhere to resolutions.
        return IssueStats(
            errors = issues.count { it.severity == Severity.ERROR },
            warnings = issues.count { it.severity == Severity.WARNING },
            hints = issues.count { it.severity == Severity.HINT }
        )
    }

    private fun getTreeDepth(ortResult: OrtResult, ignoreExcluded: Boolean = false): Int =
        ortResult
            .getProjects()
            .filter { project -> !ignoreExcluded || ortResult.isExcluded(project.id) }
            .flatMap { project -> project.scopes }
            .filter { scope -> !ignoreExcluded || ortResult.repository.config.excludes.isScopeExcluded(scope) }
            .map { scope -> scope.getTreeDepth() }
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
}
