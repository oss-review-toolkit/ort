/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.helper.commands

import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.utils.OrtHelperCommand
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.utils.common.expandTilde

internal class GroupScanIssuesCommand : OrtHelperCommand(
    help = "Shows the amount of affected dependencies for each scan issue category."
) {
    private val ortFile by option(
        "--ort-file", "-i",
        help = "The ORT result file to read as input."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    override fun run() {
        val ortResult = ortFile.readValue<OrtResult>()

        val issues = ortResult.getScannerIssues(
            omitExcluded = true,
            omitResolved = true,
            minSeverity = Severity.ERROR
        ).filter { (id, _) -> ortResult.isPackage(id) }

        val issueCategoriesForId = issues.mapValues { issue ->
            issue.value.mapTo(mutableSetOf()) { it.category }
        }

        val issueCategoriesForIdWithoutVersion = issueCategoriesForId.entries.groupBy(
            { it.key.copy(version = "") },
            { it.value }
        ).mapValues { it.value.flatten().toSet() }

        val pkgCountsForIssueCategory = ScanIssueCategory.entries.associateWith { category ->
            val numPackages = issueCategoriesForId.count { (_, categories) ->
                category in categories
            }

            val numPackagesWithoutVersion = issueCategoriesForIdWithoutVersion.count { (_, categories) ->
                category in categories
            }

            numPackages to numPackagesWithoutVersion
        }

        val stats = buildString {
            pkgCountsForIssueCategory.entries.sortedByDescending { it.value.first }.forEach { (category, counts) ->
                appendLine("$category: ${counts.first} / ${counts.second}")
            }
        }

        print(stats)
    }
}

private enum class ScanIssueCategory(
    val regex: Regex
) {
    PROVENANCE_INFO_MISSING(
        "IOException: Could not resolve provenance for package '.*' for source code origins \\[.*\\]\\."
    ),
    SCAN_TIMED_OUT(
        "ERROR: Timeout after .* seconds while scanning file '.*'\\."
    ),
    VCS_REVISION_NOT_FOUND(
        ".*Could not resolve revision.*Could not find any revision candidates.*"
    ),
    VCS_PATH_NOT_EXISTENT(
        ".*Could not resolve provenance.*because the requested VCS path.*does not exist."
    ),
    VCS_TYPE_UNKNOWN(
        ".*Could not determine VCS for type.*"
    ),
    OTHER("");

    constructor(pattern: String) : this(pattern.toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE)))

    companion object {
        fun forIssue(issue: Issue) = entries.find { it.regex.matches(issue.message) } ?: OTHER
    }
}

private val Issue.category: ScanIssueCategory
    get() = ScanIssueCategory.forIssue(this)
