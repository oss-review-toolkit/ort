/*
 * Copyright (C) 2020-2021 Bosch.IO GmbH
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

/**
 * The result of a specific advisor execution for a single package.
 *
 * Different advisor implementations may produce findings of different types. To reflect this, this class has multiple
 * fields for findings of these types. It is up to a concrete advisor, which of these fields it populates.
 */
data class AdvisorResult(
    /**
     * Details about the used advisor.
     */
    val advisor: AdvisorDetails,

    /**
     * A summary of the advisor results.
     */
    val summary: AdvisorSummary,

    /**
     * The defects.
     */
    val defects: List<Defect> = emptyList(),

    /**
     * The vulnerabilities.
     */
    val vulnerabilities: List<Vulnerability> = emptyList()
)
