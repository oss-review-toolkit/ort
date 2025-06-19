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

import com.fasterxml.jackson.annotation.JsonInclude

import org.ossreviewtoolkit.model.utils.PathLicenseMatcher

/**
 * The result of a single scan of a single package.
 */
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
    val summary: ScanSummary,

    /**
     * A map for scanner-specific data that cannot be mapped into any generalized property, but still needs to be
     * stored in the scan result.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val additionalData: Map<String, String> = emptyMap()
) {
    /**
     * Filter all detected licenses and copyrights from the [summary] which are underneath [path], and set the [path]
     * for [provenance]. Findings which [PathLicenseMatcher] assigns as root license files for [path] are also kept.
     */
    fun filterByPath(path: String): ScanResult =
        when {
            path.isBlank() -> this

            provenance is RepositoryProvenance -> {
                copy(
                    provenance = provenance.copy(vcsInfo = provenance.vcsInfo.copy(path = path)),
                    summary = summary.filterByPath(path)
                )
            }

            else -> copy(summary = summary.filterByPath(path))
        }

    /**
     * Return a [ScanResult] whose [summary] contains only findings whose location / path is not matched by any glob
     * expression in [ignorePatterns].
     */
    fun filterByIgnorePatterns(ignorePatterns: Collection<String>): ScanResult =
        copy(summary = summary.filterByIgnorePatterns(ignorePatterns))

    /**
     * Merge this [ScanResult] with the given [other] [ScanResult].
     *
     * Both [ScanResult]s must have the same [provenance] and [scanner], otherwise an [IllegalArgumentException] is
     * thrown.
     */
    operator fun plus(other: ScanResult) =
        ScanResult(
            provenance = provenance.also {
                require(it == other.provenance) {
                    "Cannot merge ScanResults with different provenance: $it != ${other.provenance}."
                }
            },
            scanner = scanner.also {
                require(it == other.scanner) {
                    "Cannot merge ScanResults with different scanners: $it != ${other.scanner}."
                }
            },
            summary = summary + other.summary,
            additionalData = additionalData + other.additionalData
        )
}
