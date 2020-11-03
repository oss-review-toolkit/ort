/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

import java.util.SortedSet

/**
 * A record of a single run of the advisor tool, containing the input and the [Vulnerability] for every checked package.
 */
data class AdvisorRecord(
    /**
     * The [AdvisorResult]s for all [Package]s.
     */
    val advisorResults: SortedSet<AdvisorResultContainer>
) {
    fun collectIssues(): Map<Identifier, Set<OrtIssue>> {
        val collectedIssues = mutableMapOf<Identifier, MutableSet<OrtIssue>>()

        advisorResults.forEach { container ->
            container.results.forEach { result ->
                collectedIssues.getOrPut(container.id) { mutableSetOf() } += result.summary.issues
            }
        }

        return collectedIssues
    }

    /**
     * True if any of the [advisorResults] contain [OrtIssue]s.
     */
    val hasIssues by lazy {
        advisorResults.any { advisorResultContainer ->
            advisorResultContainer.results.any { it.summary.issues.isNotEmpty() }
        }
    }
}
