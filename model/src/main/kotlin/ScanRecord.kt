/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
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

import java.util.SortedMap

/**
 * A record of a single run of the scanner tool, containing the input and the scan results for all scanned packages.
 */
@JsonIgnoreProperties(value = ["has_issues"], allowGetters = true)
data class ScanRecord(
    /**
     * The [ScanResult]s for all [Package]s.
     */
    val scanResults: SortedMap<Identifier, List<ScanResult>>,

    /**
     * The [AccessStatistics] for the scan results storage.
     */
    val storageStats: AccessStatistics
) {
    /**
     * Return a map of all de-duplicated [OrtIssue]s associated by [Identifier].
     */
    fun collectIssues(): Map<Identifier, Set<OrtIssue>> {
        val collectedIssues = mutableMapOf<Identifier, MutableSet<OrtIssue>>()

        scanResults.forEach { (id, results) ->
            results.forEach { result ->
                if (result.summary.issues.isNotEmpty()) {
                    collectedIssues.getOrPut(id) { mutableSetOf() } += result.summary.issues
                }
            }
        }

        return collectedIssues
    }

    /**
     * True if any of the [scanResults] contain [OrtIssue]s.
     */
    val hasIssues by lazy {
        scanResults.any { (_, results) ->
            results.any { it.summary.issues.isNotEmpty() }
        }
    }
}
