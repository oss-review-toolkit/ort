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
import com.fasterxml.jackson.annotation.JsonPropertyOrder

import java.time.Instant

import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.utils.ort.Environment

/**
 * The summary of a single run of the scanner.
 */
@JsonIgnoreProperties(value = ["has_issues"], allowGetters = true)
data class ScannerRun(
    /**
     * The [Instant] the scanner was started.
     */
    val startTime: Instant,

    /**
     * The [Instant] the scanner has finished.
     */
    val endTime: Instant,

    /**
     * The [Environment] in which the scanner was executed.
     */
    val environment: Environment,

    /**
     * The [ScannerConfiguration] used for this run.
     */
    val config: ScannerConfiguration,

    /**
     * The [ScanResult]s for all [Package]s.
     */
    @JsonPropertyOrder(alphabetic = true)
    val scanResults: Map<Identifier, List<ScanResult>>,

    /**
     * The [AccessStatistics] for the scan results storage.
     */
    val storageStats: AccessStatistics
) {
    companion object {
        /**
         * A constant for a [ScannerRun] where all properties are empty.
         */
        @JvmField
        val EMPTY = ScannerRun(
            startTime = Instant.EPOCH,
            endTime = Instant.EPOCH,
            environment = Environment(),
            config = ScannerConfiguration(),
            scanResults = emptyMap(),
            storageStats = AccessStatistics()
        )
    }

    /**
     * Return a map of all de-duplicated [Issue]s associated by [Identifier].
     */
    @JsonIgnore
    fun getIssues(): Map<Identifier, Set<Issue>> =
        buildMap<Identifier, MutableSet<Issue>> {
            scanResults.forEach { (id, results) ->
                results.forEach { result ->
                    if (result.summary.issues.isNotEmpty()) {
                        getOrPut(id) { mutableSetOf() } += result.summary.issues
                    }
                }
            }
        }
}
