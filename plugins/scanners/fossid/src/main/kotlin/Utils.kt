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

import java.lang.invoke.MethodHandles

import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.clients.fossid.model.rules.IgnoreRule
import org.ossreviewtoolkit.clients.fossid.model.rules.RuleType
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.utils.common.alsoIfNull

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

private val DIRECTORY_REGEX = "(?<directory>.+)/(?<starstar>\\*\\*)?".toRegex()
private val EXTENSION_REGEX = "\\*\\.(?<extension>\\w+)".toRegex()
private val FILE_REGEX = "(?<file>[^/]+)".toRegex()

/**
 * Return the ORT [path excludes][Excludes.paths] in [excludes] converted to FossID [IgnoreRule]s and any errors that
 * occurred during the conversion.
 */
internal fun convertRules(excludes: Excludes): Pair<List<IgnoreRule>, List<Issue>> {
    val issues = mutableListOf<Issue>()

    val ignoreRules = excludes.paths.mapNotNull { pathExclude ->
        pathExclude.mapToRule().alsoIfNull {
            val message = "Path exclude '${pathExclude.pattern}' cannot be converted to an ignore rule."

            issues += Issue(
                source = "FossID.convertRules",
                message = message,
                severity = Severity.HINT
            )

            logger.warn { message }
        }
    }

    return ignoreRules to issues
}

@Suppress("UnsafeCallOnNullableType")
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
 * Filter [IgnoreRule]s which are not contained in the [referenceRules]. These are legacy rules because they were not
 * created from the [Excludes] defined in the repository configuration. Also create an [Issue] for each legacy rule.
 */
internal fun List<IgnoreRule>.filterLegacyRules(referenceRules: List<IgnoreRule>): Pair<List<IgnoreRule>, List<Issue>> {
    val legacyRules = filterNot { rule ->
        referenceRules.any { it.value == rule.value && it.type == rule.type }
    }

    val issues = legacyRules.map {
        Issue(
            source = "FossID.compare",
            message = "Rule '${it.value}' with type '${it.type}' is not present in the .ort.yml path excludes. " +
                "Add it to the .ort.yml file or remove it from the FossID scan.",
            severity = Severity.HINT
        )
    }

    return legacyRules to issues
}
