/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.evaluator

import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.Severity.ERROR
import org.ossreviewtoolkit.model.Severity.HINT
import org.ossreviewtoolkit.model.Severity.WARNING
import org.ossreviewtoolkit.model.SnippetFinding
import org.ossreviewtoolkit.utils.common.enumSetOf

/**
 * A [Rule] to check a single [SnippetFinding] whose source location file path is relative to the root provenance.
 */
class SnippetRule(
    ruleSet: RuleSet,
    name: String,
    val scannerName: String,
    val snippetFinding: SnippetFinding
) : Rule(ruleSet, name) {
    /**
     * A single line string representation of the source location of [snippetFinding].
     */
    val sourceFileLocation by lazy {
        buildString {
            append("${snippetFinding.sourceLocation.path}:${snippetFinding.sourceLocation.startLine}")
            if (snippetFinding.sourceLocation.endLine != snippetFinding.sourceLocation.startLine) {
                append("-${snippetFinding.sourceLocation.endLine}")
            }
        }
    }

    override val description = "\tEvaluating snippet rule '$name' for finding at $sourceFileLocation"

    override fun issueSource(): String = "$name - snippet at $sourceFileLocation"

    /**
     * A [RuleMatcher] that checks if the snippet finding is excluded.
     */
    fun isExcluded() =
        object : RuleMatcher {
            override val description = "isExcluded(${snippetFinding.sourceLocation.path})"

            override fun matches() = ruleSet.ortResult.isPathExcluded(snippetFinding.sourceLocation.path)
        }

    fun isFromScanner(scannerName: String) =
        object : RuleMatcher {
            override val description = "isFromScanner(${snippetFinding.sourceLocation.path})"

            override fun matches(): Boolean = this@SnippetRule.scannerName.equals(scannerName, ignoreCase = true)
        }

    fun issue(severity: Severity, message: String, howToFix: String) =
        issue(severity, null, null, enumSetOf(), message, howToFix)

    /**
     * Add a [hint][Severity.HINT] to the list of [violations].
     */
    fun hint(message: String, howToFix: String) = issue(HINT, message, howToFix)

    /**
     * Add a [warning][Severity.WARNING] to the list of [violations].
     */
    fun warning(message: String, howToFix: String) = issue(WARNING, message, howToFix)

    /**
     * Add an [error][Severity.ERROR] to the list of [violations].
     */
    fun error(message: String, howToFix: String) = issue(ERROR, message, howToFix)
}
