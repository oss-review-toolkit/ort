/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model.vulnerabilities

/**
 * The rating attaches human-readable semantics to the score number according to CVSS version 4, see
 * https://www.first.org/cvss/v4.0/specification-document#Qualitative-Severity-Rating-Scale.
 */
enum class Cvss4Rating(private val upperBound: Float) {
    NONE(0.0f),
    LOW(4.0f),
    MEDIUM(7.0f),
    HIGH(9.0f),
    CRITICAL(10.0f);

    companion object {
        /**
         * A set of prefixes that refer to the CVSS version 4 scoring system.
         */
        val PREFIXES = setOf("CVSS4", "CVSSV4", "CVSS_V4", "CVSS:4")

        /**
         * Get the [Cvss4Rating] from a [score], or null if the [score] does not map to any [Cvss4Rating].
         */
        fun fromScore(score: Float): Cvss4Rating? =
            when {
                score < 0.0f || score > CRITICAL.upperBound -> null
                score == NONE.upperBound -> NONE
                score < LOW.upperBound -> LOW
                score < MEDIUM.upperBound -> MEDIUM
                score < HIGH.upperBound -> HIGH
                score <= CRITICAL.upperBound -> CRITICAL
                else -> null
            }
    }
}
