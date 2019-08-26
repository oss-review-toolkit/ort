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

package com.here.ort.model

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude

import java.time.Instant
import java.util.SortedSet

/**
 * A class representing a single license finding.
 */
data class LicenseFinding(
    val license: String,
    val location: TextLocation
)

/**
 * A class representing a single copyright finding.
 */
data class CopyrightFinding(
    val statement: String,
    val location: TextLocation
)

/**
 * A short summary of the scan results.
 */
data class ScanSummary(
    /**
     * The time when the scan started.
     */
    @JsonAlias("startTime")
    val startTime: Instant,

    /**
     * The time when the scan finished.
     */
    @JsonAlias("endTime")
    val endTime: Instant,

    /**
     * The number of scanned files.
     */
    @JsonAlias("fileCount")
    val fileCount: Int,

    /**
     * The licenses associated to their respective copyrights, if any.
     */
    @JsonAlias("licenses")
    val licenseFindings: SortedSet<LicenseFindings>,

    /**
     * The list of errors that occurred during the scan.
     */
    // Do not serialize if empty to reduce the size of the result file. If there are no errors at all,
    // [ScanRecord.hasErrors] already contains that information.
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val errors: List<OrtIssue> = emptyList()
) {
    /**
     * Return all license findings as one entry per finding as opposed to [licenseFindings] which is grouped.
     * This function is temporary for the duration of an incremental license findings refactoring.
     * When that refactoring is finished this function is supposed to be converted to a property which is serialized
     * replacing [licenseFindings].
     */
    @JsonIgnore
    fun getSingleLicenseFindings(): List<LicenseFinding> =
        licenseFindings.flatMap { findings ->
            findings.locations.map {
                LicenseFinding(
                    license = findings.license,
                    location = it
                )
            }
        }

    /**
     * Return all copyright findings as one entry per finding as opposed to [licenseFindings] which is grouped.
     * When that refactoring is finished this function is supposed to be converted to a property which is serialized
     * replacing [licenseFindings].
     */
    @JsonIgnore
    fun getSingleCopyrightFindings(): List<CopyrightFinding> =
        licenseFindings.flatMap { licenseFindings ->
            licenseFindings.copyrights.flatMap { copyrightFindings ->
                copyrightFindings.locations.map {
                    CopyrightFinding(
                        statement = copyrightFindings.statement,
                        location = it
                    )
                }
            }
        }

    @get:JsonIgnore
    val licenseFindingsMap = sortedMapOf<String, SortedSet<CopyrightFindings>>().also {
        licenseFindings.forEach { finding ->
            it.getOrPut(finding.license) { sortedSetOf() } += finding.copyrights
        }
    }

    @get:JsonIgnore
    val licenses = licenseFindingsMap.keys
}
