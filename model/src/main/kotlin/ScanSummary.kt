/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import java.time.Instant
import java.util.SortedSet

import org.ossreviewtoolkit.model.config.LicenseFilenamePatterns
import org.ossreviewtoolkit.model.utils.RootLicenseMatcher
import org.ossreviewtoolkit.utils.common.FileMatcher
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

/**
 * A short summary of the scan results.
 */
@JsonIgnoreProperties("file_count")
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
     * The [SPDX package verification code](https://spdx.dev/spdx_specification_2_0_html#h.2p2csry), calculated from all
     * files in the package. Note that if the scanner is configured to ignore certain files they will still be included
     * in the calculation of this code.
     */
    val packageVerificationCode: String,

    /**
     * The detected license findings.
     */
    @JsonProperty("licenses")
    val licenseFindings: SortedSet<LicenseFinding>,

    /**
     * The detected copyright findings.
     */
    @JsonProperty("copyrights")
    val copyrightFindings: SortedSet<CopyrightFinding>,

    /**
     * The list of issues that occurred during the scan. This property is not serialized if the list is empty to reduce
     * the size of the result file. If there are no issues at all, [ScanRecord.hasIssues] already contains that
     * information.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val issues: List<OrtIssue> = emptyList()
) {
    @get:JsonIgnore
    val licenses: Set<SpdxExpression> = licenseFindings.mapTo(mutableSetOf()) { it.license }

    /**
     * Filter all detected licenses and copyrights from this [ScanSummary] which are underneath [path]. Findings which
     * [RootLicenseMatcher] assigns as root license files for [path] are also kept.
     */
    fun filterByPath(path: String): ScanSummary {
        if (path.isBlank()) return this

        val rootLicenseMatcher = RootLicenseMatcher(LicenseFilenamePatterns.getInstance())
        val applicableLicenseFiles = rootLicenseMatcher.getApplicableRootLicenseFindingsForDirectories(
            licenseFindings = licenseFindings,
            directories = listOf(path)
        ).values.flatten().mapTo(mutableSetOf()) { it.location.path }

        fun TextLocation.matchesPath() = this.path.startsWith("$path/") || this.path in applicableLicenseFiles

        val licenseFindings = licenseFindings.filter { it.location.matchesPath() }.toSortedSet()
        val copyrightFindings = copyrightFindings.filter { it.location.matchesPath() }.toSortedSet()

        return copy(
            licenseFindings = licenseFindings,
            copyrightFindings = copyrightFindings
        )
    }

    /**
     * Return a [ScanSummary] which contains only findings whose location / path is not matched by any glob expression
     * in [ignorePatterns].
     */
    fun filterByIgnorePatterns(ignorePatterns: Collection<String>): ScanSummary {
        val matcher = FileMatcher(ignorePatterns)

        return copy(
            licenseFindings = licenseFindings.filterTo(sortedSetOf()) { !matcher.matches(it.location.path) },
            copyrightFindings = copyrightFindings.filterTo(sortedSetOf()) { !matcher.matches(it.location.path) }
        )
    }
}
