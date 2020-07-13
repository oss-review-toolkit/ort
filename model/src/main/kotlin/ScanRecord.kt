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

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

import java.util.SortedSet

/**
 * A record of a single run of the scanner tool, containing the input and the scan results for all scanned packages.
 */
@JsonIgnoreProperties(
    value = ["has_issues", /* Backwards-compatibility: */ "has_errors", "scanned_scopes", "scopes"],
    allowGetters = true
)
data class ScanRecord(
    /**
     * The [ScanResult]s for all [Package]s.
     */
    val scanResults: SortedSet<ScanResultContainer>,

    /**
     * The [AccessStatistics] for the scan results storage.
     */
    @JsonAlias("cache_stats")
    val storageStats: AccessStatistics
) {
    /**
     * Return a map of all de-duplicated [OrtIssue]s associated by [Identifier].
     */
    fun collectIssues(): Map<Identifier, Set<OrtIssue>> {
        val collectedIssues = mutableMapOf<Identifier, MutableSet<OrtIssue>>()

        scanResults.forEach { container ->
            container.results.forEach { result ->
                collectedIssues.getOrPut(container.id) { mutableSetOf() } += result.summary.issues
            }
        }

        return collectedIssues
    }

    /**
     * True if any of the [scanResults] contain [OrtIssue]s.
     */
    @Suppress("UNUSED") // Not used in code, but shall be serialized.
    val hasIssues by lazy {
        scanResults.any { scanResultContainer ->
            scanResultContainer.results.any { it.summary.issues.isNotEmpty() }
        }
    }
}
