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
    fun filterByPath(path: String): ScanResult =
        takeIf { path.isBlank() } ?: ScanResult(
            provenance = if (provenance is RepositoryProvenance) {
                provenance.copy(vcsInfo = provenance.vcsInfo.copy(path = path))
            } else {
                provenance
            },
            scanner = scanner,
            summary = summary.filterByPath(path),
        )

    /**
     * Return a [ScanResult] whose [summary] contains only findings from the [provenance]'s [VcsInfo.path].
     */
    fun filterByVcsPath(): ScanResult =
        if (provenance is RepositoryProvenance) filterByPath(provenance.vcsInfo.path) else this

    /**
     * Return a [ScanResult] whose [summary] contains only findings whose location / path is not matched by any glob
     * expression in [ignorePatterns].
     */
    fun filterByIgnorePatterns(ignorePatterns: Collection<String>): ScanResult =
        copy(summary = summary.filterByIgnorePatterns(ignorePatterns))
}
