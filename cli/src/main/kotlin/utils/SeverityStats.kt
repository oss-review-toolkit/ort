/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.cli.utils

import com.github.ajalt.clikt.core.ProgramResult

import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.Severity

/**
 * Helper class to collect severity statistics.
 */
internal class SeverityStats(
    private val resolvedCounts: Map<Severity, Int>,
    private val unresolvedCounts: Map<Severity, Int>
) {
    companion object {
        fun createFromIssues(
            resolvedIssues: Collection<OrtIssue>,
            unresolvedIssues: Collection<OrtIssue>
        ) =
            SeverityStats(
                resolvedCounts = resolvedIssues.groupingBy { it.severity }.eachCount(),
                unresolvedCounts = unresolvedIssues.groupingBy { it.severity }.eachCount()
            )

        fun createFromRuleViolations(
            resolvedRuleViolations: Collection<RuleViolation>,
            unresolvedRuleViolations: Collection<RuleViolation>
        ) =
            SeverityStats(
                resolvedCounts = resolvedRuleViolations.groupingBy { it.severity }.eachCount(),
                unresolvedCounts = unresolvedRuleViolations.groupingBy { it.severity }.eachCount()
            )
    }

    /**
     * Get the resolved count for [severity].
     */
    private fun getResolvedCount(severity: Severity) = resolvedCounts.getOrDefault(severity, 0)

    /**
     * Get the unresolved count for [severity].
     */
    private fun getUnresolvedCount(severity: Severity) = unresolvedCounts.getOrDefault(severity, 0)

    /**
     * Count all unresolved severities above or equal to [threshold].
     */
    private fun getUnresolvedCountWithThreshold(threshold: Severity) =
        unresolvedCounts.entries.sumOf { (severity, count) -> if (severity >= threshold) count else 0 }

    /**
     * Print the stats to stdout.
     */
    fun print(): SeverityStats {
        val resolvedHintCount = getResolvedCount(Severity.HINT)
        val resolvedWarningCount = getResolvedCount(Severity.WARNING)
        val resolvedErrorCount = getResolvedCount(Severity.ERROR)

        println(
            "Found $resolvedErrorCount resolved error(s), $resolvedWarningCount resolved warning(s), " +
                    "$resolvedHintCount resolved hint(s)."
        )

        val unresolvedHintCount = getUnresolvedCount(Severity.HINT)
        val unresolvedWarningCount = getUnresolvedCount(Severity.WARNING)
        val unresolvedErrorCount = getUnresolvedCount(Severity.ERROR)

        println(
            "Found $unresolvedErrorCount unresolved error(s), $unresolvedWarningCount unresolved warning(s), " +
                    "$unresolvedHintCount unresolved hint(s)."
        )

        return this
    }

    /**
     * If there are severities equal to or greater than [threshold], print an according note and throw a [ProgramResult]
     * exception with [severeStatusCode].
     */
    fun conclude(threshold: Severity, severeStatusCode: Int): SeverityStats {
        val severeIssueCount = getUnresolvedCountWithThreshold(threshold)

        if (severeIssueCount > 0) {
            println(
                "There are $severeIssueCount issue(s) with a severity equal to or greater than the $threshold " +
                        "threshold."
            )

            throw ProgramResult(severeStatusCode)
        }

        return this
    }
}
