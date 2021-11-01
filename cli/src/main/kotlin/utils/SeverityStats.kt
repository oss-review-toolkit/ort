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
internal sealed class SeverityStats(
    private val resolvedCounts: Map<Severity, Int>,
    private val unresolvedCounts: Map<Severity, Int>
) {
    class IssueSeverityStats(resolvedCounts: Map<Severity, Int>, unresolvedCounts: Map<Severity, Int>) :
        SeverityStats(resolvedCounts, unresolvedCounts)

    class RuleViolationsSeverityStats(resolvedCounts: Map<Severity, Int>, unresolvedCounts: Map<Severity, Int>) :
        SeverityStats(resolvedCounts, unresolvedCounts)

    companion object {
        fun createFromIssues(
            resolvedIssues: Collection<OrtIssue>,
            unresolvedIssues: Collection<OrtIssue>
        ) =
            IssueSeverityStats(
                resolvedCounts = resolvedIssues.groupingBy { it.severity }.eachCount(),
                unresolvedCounts = unresolvedIssues.groupingBy { it.severity }.eachCount()
            )

        fun createFromRuleViolations(
            resolvedRuleViolations: Collection<RuleViolation>,
            unresolvedRuleViolations: Collection<RuleViolation>
        ) =
            RuleViolationsSeverityStats(
                resolvedCounts = resolvedRuleViolations.groupingBy { it.severity }.eachCount(),
                unresolvedCounts = unresolvedRuleViolations.groupingBy { it.severity }.eachCount()
            )
    }

    /**
     * Count all unresolved severities above or equal to [threshold].
     */
    private fun getUnresolvedCountWithThreshold(threshold: Severity) =
        unresolvedCounts.entries.sumOf { (severity, count) -> if (severity >= threshold) count else 0 }

    /**
     * Print the stats to stdout.
     */
    fun print(): SeverityStats {
        fun p(count: Int, thing: String) = if (count == 1) "$count $thing" else "$count ${thing}s"

        val thing = when (this) {
            is IssueSeverityStats -> "issues"
            is RuleViolationsSeverityStats -> "rule violations"
        }

        val resolved = Severity.values().sortedArrayDescending().map {
            p(resolvedCounts.getOrDefault(it, 0), it.name.lowercase())
        }

        println("Resolved $thing: ${resolved.joinToString()}.")

        val unresolved = Severity.values().sortedArrayDescending().map {
            p(unresolvedCounts.getOrDefault(it, 0), it.name.lowercase())
        }

        println("Unresolved $thing: ${unresolved.joinToString()}.")

        return this
    }

    /**
     * If there are severities equal to or greater than [threshold], print an according note and throw a [ProgramResult]
     * exception with [severeStatusCode].
     */
    fun conclude(threshold: Severity, severeStatusCode: Int): SeverityStats {
        val severeCount = getUnresolvedCountWithThreshold(threshold)

        if (severeCount > 0) {
            val (be, s) = if (severeCount == 1) "is" to "" else "are" to "s"

            val thing = when (this) {
                is IssueSeverityStats -> "issue$s"
                is RuleViolationsSeverityStats -> "rule violation$s"
            }

            println(
                "There $be $severeCount unresolved $thing with a severity equal to or greater than the $threshold " +
                        "threshold."
            )

            throw ProgramResult(severeStatusCode)
        }

        return this
    }
}
