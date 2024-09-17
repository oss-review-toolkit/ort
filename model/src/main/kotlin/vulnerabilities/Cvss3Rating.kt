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
 * The rating attaches human-readable semantics to the score number according to CVSS version 3, see
 * https://www.first.org/cvss/v3.0/specification-document#Qualitative-Severity-Rating-Scale.
 */
enum class Cvss3Rating(private val upperBound: Float) {
    NONE(0.0f),
    LOW(4.0f),
    MEDIUM(7.0f),
    HIGH(9.0f),
    CRITICAL(10.0f);

    companion object {
        /**
         * A set of prefixes that refer to the CVSS version 3 scoring system.
         */
        val PREFIXES = setOf("CVSS3", "CVSSV3", "CVSS_V3", "CVSS:3")

        /**
         * Get the [Cvss3Rating] from a [score], or null if the [score] does not map to any [Cvss3Rating].
         */
        fun fromScore(score: Float): Cvss3Rating? =
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
