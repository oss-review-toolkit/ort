/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

import java.time.Instant
import java.util.SortedSet

/**
 * A short summary of the scan results.
 */
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
     * The number of scanned files.
     */
    val fileCount: Int,

    /**
     * The [SPDX package verification code](https://spdx.org/spdx_specification_2_0_html#h.2p2csry), calculated from
     * all files in the package. Note that if the scanner is configured to ignore certain files they will still be
     * included in the calculation of this code.
     */
    val packageVerificationCode: String,

    /**
     * The license findings.
     */
    @JsonProperty("licenses")
    val licenseFindings: SortedSet<LicenseFinding>,

    /**
     * The copyright findings.
     */
    @JsonProperty("copyrights")
    val copyrightFindings: SortedSet<CopyrightFinding>,

    /**
     * The list of issues that occurred during the scan.
     * This property is not serialized if the list is empty to reduce the size of the result file. If there are no
     * issues at all, [ScanRecord.hasIssues] already contains that information.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val issues: List<OrtIssue> = emptyList()
) {
    @get:JsonIgnore
    val licenses: Set<String> = licenseFindings.mapTo(mutableSetOf()) { it.license }
}
