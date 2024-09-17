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
 * The rating attaches human-readable semantics to the score number according to CVSS version 2, see
 * https://www.balbix.com/insights/cvss-v2-vs-cvss-v3/#CVSSv3-Scoring-Scale-vs-CVSSv2-6.
 */
enum class Cvss2Rating(private val upperBound: Float) {
    LOW(4.0f),
    MEDIUM(7.0f),
    HIGH(10.0f);

    companion object {
        /**
         * A set of prefixes that refer to the CVSS version 2 scoring system.
         */
        val PREFIXES = setOf("CVSS2", "CVSSV2", "CVSS_V2", "CVSS:2")

        /**
         * Get the [Cvss2Rating] from a [score], or null if the [score] does not map to any [Cvss2Rating].
         */
        fun fromScore(score: Float): Cvss2Rating? =
            when {
                score < 0.0f || score > HIGH.upperBound -> null
                score < LOW.upperBound -> LOW
                score < MEDIUM.upperBound -> MEDIUM
                score <= HIGH.upperBound -> HIGH
                else -> null
            }
    }
}
