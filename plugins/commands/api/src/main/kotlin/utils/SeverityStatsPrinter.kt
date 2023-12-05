/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.commands.api.utils

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.mordant.rendering.Theme
import com.github.ajalt.mordant.terminal.Terminal

import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.utils.ResolutionProvider

/**
 * A helper class to print severity statistics.
 */
class SeverityStatsPrinter(private val terminal: Terminal, private val resolutionProvider: ResolutionProvider) {
    inner class Entry(
        val name: String,
        val resolvedCounts: Map<Severity, Int>,
        val unresolvedCounts: Map<Severity, Int>
    ) {
        /**
         * Print the stats to the [terminal].
         */
        fun print() =
            apply {
                fun p(count: Int, thing: String) = if (count == 1) "$count $thing" else "$count ${thing}s"

                val resolved = Severity.entries.toTypedArray().sortedArrayDescending().map {
                    val count = resolvedCounts.getOrDefault(it, 0)
                    val text = p(count, it.name.lowercase())
                    Theme.Default.success(text)
                }

                terminal.println("${Theme.Default.info("Resolved ${name}s:")} ${resolved.joinToString()}.")

                val unresolved = Severity.entries.toTypedArray().sortedArrayDescending().map {
                    val count = unresolvedCounts.getOrDefault(it, 0)
                    val text = p(count, it.name.lowercase())
                    if (count == 0) Theme.Default.success(text) else Theme.Default.danger(text)
                }

                terminal.println("${Theme.Default.warning("Unresolved ${name}s:")} ${unresolved.joinToString()}.")
            }

        /**
         * Conclude severity statistics by printing an according note and throw a [ProgramResult] exception with
         * [severeStatusCode] if there are severities equal to or greater than [threshold].
         */
        fun conclude(threshold: Severity, severeStatusCode: Int) =
            apply {
                val severeCount = unresolvedCounts.entries.sumOf { (severity, count) ->
                    if (severity >= threshold) count else 0
                }

                if (severeCount > 0) {
                    val (be, s) = if (severeCount == 1) "is" to "" else "are" to "s"

                    terminal.println(
                        "There $be $severeCount unresolved $name$s with a severity equal to or greater than the " +
                            "$threshold threshold."
                    )

                    throw ProgramResult(severeStatusCode)
                }
            }
    }

    @JvmName("statsForIssues")
    fun stats(issues: Collection<Issue>) =
        stats("issue", issues.partition { resolutionProvider.isResolved(it) }) { severity }

    @JvmName("statsForRuleViolations")
    fun stats(ruleViolations: Collection<RuleViolation>) =
        stats("rule violation", ruleViolations.partition { resolutionProvider.isResolved(it) }) { severity }

    private fun <T> stats(name: String, thingsWithSeverities: Pair<List<T>, List<T>>, selector: T.() -> Severity) =
        Entry(
            name = name,
            resolvedCounts = thingsWithSeverities.first.groupingBy(selector).eachCount(),
            unresolvedCounts = thingsWithSeverities.second.groupingBy(selector).eachCount()
        )
}
