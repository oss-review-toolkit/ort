/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.fossid

import org.ossreviewtoolkit.clients.fossid.model.rules.IgnoreRule
import org.ossreviewtoolkit.clients.fossid.model.rules.RuleType
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.PathExclude

private val DIRECTORY_REGEX = "(?<directory>(?:[\\w.]+/)*[\\w.]+)/?(?<starstar>\\*\\*)?".toRegex()
private val EXTENSION_REGEX = "\\*\\.(?<extension>\\w+)".toRegex()
private val FILE_REGEX = "(?<file>[^/]+)".toRegex()

/**
 * Convert the ORT [path excludes][Excludes.paths] in [excludes] to FossID [IgnoreRule]s. If an error is encountered
 * during the mapping, an issue is added to [issues].
 */
internal fun convertRules(excludes: Excludes, issues: MutableList<Issue>): List<IgnoreRule> {
    return excludes.paths.mapNotNull {
        it.mapToRule().also { mappedRule ->
            if (mappedRule == null) {
                issues += Issue(
                    source = "FossID.convertRules",
                    message = "Path exclude '${it.pattern}' cannot be converted to an ignore rule.",
                    severity = Severity.HINT
                )
                FossId.logger.warn {
                    "Path exclude  '${it.pattern}' cannot be converted to an ignore rule."
                }
            }
        }
    }
}

private fun PathExclude.mapToRule(): IgnoreRule? {
    EXTENSION_REGEX.matchEntire(pattern)?.let { extensionMatch ->
        val extension = extensionMatch.groups["extension"]!!.value
        return IgnoreRule(-1, RuleType.EXTENSION, ".$extension", -1, "")
    }

    FILE_REGEX.matchEntire(pattern)?.let { fileMatch ->
        val file = fileMatch.groups["file"]!!.value
        return IgnoreRule(-1, RuleType.FILE, file, -1, "")
    }

    DIRECTORY_REGEX.matchEntire(pattern)?.let { directoryMatch ->
        val directory = directoryMatch.groups["directory"]!!.value
        val starStar = directoryMatch.groups["starstar"]?.value

        return if (starStar == null) {
            IgnoreRule(-1, RuleType.DIRECTORY, directory, -1, "")
        } else {
            IgnoreRule(-1, RuleType.DIRECTORY, "$directory/**", -1, "")
        }
    }

    return null
}

/**
 * Check if some elements of [rulesToTest] are legacy rules i.e. are not present in a reference list (current object).
 * Create an issue on [issues] for each legacy rule and return a list of the latter.
 */
internal fun List<IgnoreRule>.filterLegacyRules(
    rulesToTest: List<IgnoreRule>,
    issues: MutableList<Issue>
): List<IgnoreRule> = rulesToTest.filterNot { ruleToTest ->
    any { it.value == ruleToTest.value && it.type == ruleToTest.type }
}.onEach {
    issues += Issue(
        source = "FossID.compare",
        message = "Rule '${it.value}' with type '${it.type}' is not present in the .ort.yml path excludes. " +
                "Add it to the .ort.yml file or remove it from the FossID scan.",
        severity = Severity.HINT
    )
}
