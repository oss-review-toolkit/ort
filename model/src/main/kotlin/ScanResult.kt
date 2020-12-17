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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

import org.ossreviewtoolkit.model.utils.RootLicenseMatcher

/**
 * The result of a single scan of a single package.
 */
@JsonIgnoreProperties("raw_result")
data class ScanResult(
    /**
     * Provenance information about the scanned source code.
     */
    val provenance: Provenance,

    /**
     * Details about the used scanner.
     */
    val scanner: ScannerDetails,

    /**
     * A summary of the scan results.
     */
    val summary: ScanSummary
) {
    /**
     * Filter all detected licenses and copyrights from the [summary] which are underneath [path], and set the [path]
     * for [provenance]. Findings which [RootLicenseMatcher] assigns as root license files for [path] are also kept.
     */
    fun filterByPath(path: String): ScanResult {
        if (path.isBlank()) return this

        val applicableLicenseFiles = RootLicenseMatcher().getApplicableRootLicenseFindingsForDirectories(
            licenseFindings = summary.licenseFindings,
            directories = listOf(path)
        ).values.flatten().mapTo(mutableSetOf()) { it.location.path }

        fun TextLocation.matchesPath() = this.path.startsWith("$path/") || this.path in applicableLicenseFiles

        val newProvenance = provenance.copy(
            vcsInfo = provenance.vcsInfo?.copy(path = path),
            originalVcsInfo = provenance.originalVcsInfo?.copy(path = path)
        )

        val licenseFindings = summary.licenseFindings.filter { it.location.matchesPath() }.toSortedSet()
        val copyrightFindings = summary.copyrightFindings.filter { it.location.matchesPath() }.toSortedSet()
        val fileCount = mutableSetOf<String>().also { set ->
            licenseFindings.mapTo(set) { it.location.path }
            copyrightFindings.mapTo(set) { it.location.path }
        }.size

        val summary = summary.copy(
            fileCount = fileCount,
            licenseFindings = licenseFindings,
            copyrightFindings = copyrightFindings
        )

        return ScanResult(newProvenance, scanner, summary)
    }
}
