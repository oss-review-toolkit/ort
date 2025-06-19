/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.util.StdConverter

import java.time.Instant

import org.ossreviewtoolkit.model.config.LicenseFilePatterns
import org.ossreviewtoolkit.model.utils.CopyrightFindingSortedSetConverter
import org.ossreviewtoolkit.model.utils.LicenseFindingSortedSetConverter
import org.ossreviewtoolkit.model.utils.PathLicenseMatcher
import org.ossreviewtoolkit.model.utils.SnippetFindingSortedSetConverter
import org.ossreviewtoolkit.utils.common.FileMatcher
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

/**
 * A short summary of the scan results.
 */
@JsonIgnoreProperties("file_count", "package_verification_code")
data class ScanSummary(
    /**
     * The time when the scan started.
     */
    val startTime: Instant,

    /**
     * The time when the scan finished.
     */
    val endTime: Instant,

    /**
     * The detected license findings.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("licenses")
    @JsonSerialize(converter = LicenseFindingSortedSetConverter::class)
    val licenseFindings: Set<LicenseFinding> = emptySet(),

    /**
     * The detected copyright findings.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("copyrights")
    @JsonSerialize(converter = CopyrightFindingSortedSetConverter::class)
    val copyrightFindings: Set<CopyrightFinding> = emptySet(),

    /**
     * The detected snippet findings.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("snippets")
    @JsonSerialize(converter = SnippetFindingSortedSetConverter::class)
    val snippetFindings: Set<SnippetFinding> = emptySet(),

    /**
     * The list of issues that occurred during the scan. This property is not serialized if the list is empty to reduce
     * the size of the result file.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonDeserialize(converter = IssueListConverter::class)
    val issues: List<Issue> = emptyList()
) {
    companion object {
        /**
         * A constant for a [ScannerRun] where all properties are empty.
         */
        @JvmField
        val EMPTY = ScanSummary(
            startTime = Instant.EPOCH,
            endTime = Instant.EPOCH
        )
    }

    @get:JsonIgnore
    val licenses: Set<SpdxExpression> by lazy { licenseFindings.mapTo(mutableSetOf()) { it.license } }

    /**
     * Filter all detected licenses and copyrights from this [ScanSummary] which are underneath [path]. Findings which
     * [PathLicenseMatcher] assigns as root license files for [path] are also kept.
     */
    fun filterByPath(path: String): ScanSummary = filterByPaths(listOf(path))

    /**
     * Filter all detected licenses and copyrights from this [ScanSummary] which are underneath the given [paths].
     * Findings which [PathLicenseMatcher] assigns as root license files for path in [paths] are also kept.
     */
    fun filterByPaths(paths: Collection<String>): ScanSummary {
        if (paths.any { it.isBlank() }) return this

        val pathLicenseMatcher = PathLicenseMatcher(LicenseFilePatterns.getInstance())
        val applicableLicenseFiles = pathLicenseMatcher.getApplicableLicenseFindingsForDirectories(
            licenseFindings = licenseFindings,
            directories = paths
        ).values.flatten().mapTo(mutableSetOf()) { it.location.path }

        fun String.matchesPaths() =
            paths.any { filterPath ->
                startsWith("$filterPath/") || this in applicableLicenseFiles
            }

        fun TextLocation.matchesPaths() = path.matchesPaths()

        return copy(
            licenseFindings = licenseFindings.filterTo(mutableSetOf()) { it.location.matchesPaths() },
            copyrightFindings = copyrightFindings.filterTo(mutableSetOf()) { it.location.matchesPaths() },
            snippetFindings = snippetFindings.filterTo(mutableSetOf()) { it.sourceLocation.matchesPaths() },
            issues = issues.filter { it.affectedPath?.matchesPaths() != false }
        )
    }

    /**
     * Return a [ScanSummary] which contains only findings whose location / path is not matched by any glob expression
     * in [ignorePatterns].
     */
    fun filterByIgnorePatterns(ignorePatterns: Collection<String>): ScanSummary {
        val matcher = FileMatcher(ignorePatterns)

        return copy(
            licenseFindings = licenseFindings.filterTo(mutableSetOf()) { !matcher.matches(it.location.path) },
            copyrightFindings = copyrightFindings.filterTo(mutableSetOf()) { !matcher.matches(it.location.path) },
            snippetFindings = snippetFindings.filterTo(mutableSetOf()) { !matcher.matches(it.sourceLocation.path) },
            issues = issues.filter { it.affectedPath == null || !matcher.matches(it.affectedPath) }
        )
    }

    /**
     * Merge this [ScanSummary] with the given [other] [ScanSummary].
     *
     * The [startTime] and [endTime] are widened to the earliest and latest time of both summaries.
     *
     * The [licenseFindings], [copyrightFindings], [snippetFindings] and [issues] are merged by concatenation.
     */
    operator fun plus(other: ScanSummary) =
        ScanSummary(
            startTime = minOf(startTime, other.startTime),
            endTime = maxOf(endTime, other.endTime),
            licenseFindings = licenseFindings + other.licenseFindings,
            copyrightFindings = copyrightFindings + other.copyrightFindings,
            snippetFindings = snippetFindings + other.snippetFindings,
            issues = issues + other.issues
        )
}

/**
 * Set the `affectedPath` for scan timeout errors if it is null. This way scan results which have been created before
 * the `affectedPath` was introduced will still have that property set.
 */
internal class IssueListConverter : StdConverter<List<Issue>, List<Issue>>() {
    override fun convert(issues: List<Issue>): List<Issue> =
        issues.map { issue ->
            if (issue.affectedPath != null) return@map issue
            val match = TIMEOUT_ERROR_REGEX.matchEntire(issue.message) ?: return@map issue

            @Suppress("UnsafeCallOnNullableType")
            issue.copy(affectedPath = match.groups["file"]!!.value)
        }
}

private val TIMEOUT_ERROR_REGEX = Regex("ERROR: Timeout after (\\d+) seconds while scanning file '(?<file>.+)'.")
