/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

import java.net.URI
import java.time.Instant

/**
 * A data class representing detailed information about an advisor finding obtained from a specific source.
 *
 * A single finding, such as a vulnerability, can be listed by multiple sources using different properties.
 * So when ORT queries different providers for findings of a specific type it may well find multiple records for a
 * single finding, which could even contain contradicting information. To model this, a [Finding] is associated
 * with a list of details; each detail points to the source of the information and has some additional information
 * provided by this source.
 */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
data class FindingDetail(
    /**
     * The URI pointing to the source of this finding.
     */
    val url: URI,

    /**
     * The name of the scoring system to express the severity of this finding if available.
     */
    val scoringSystem: String?,

    /**
     * The severity assigned to the finding by the referenced source. Note that this is a plain string, whose meaning
     * depends on the concrete scoring system. It could be a number, but also a constant like _LOW_ or _HIGH_. A
     * *null* value is possible as well, meaning that this object does not contain any information about the severity.
     */
    val severity: String?,

    /**
     * Contains a title for the associated finding if available. If this detail is just a reference to an external
     * source of information, this field is *null*.
     */
    val title: String? = null,

    /**
     * Contains a description for the associated finding if available.
     */
    val description: String? = null,

    /**
     * A state of the associated finding. The concrete meaning of this string depends on the type of the finding and
     * the source from where it was obtained. A typical use case would be the state of an issue tracker with possible
     * values like *OPEN*, *IN PROGRESS*, *BLOCKED*, etc.
     */
    val state: String? = null,

    /**
     * Contains a creation date of the associated finding if available.
     */
    val createdAt: Instant? = null,

    /**
     * Contains a date of last modification of the associated finding if available. This information can be useful for
     * instance to find out how up-to-date this finding might be.
     */
    val modifiedAt: Instant? = null,

    /**
     * A set with labels assigned to this finding. Labels allow a classification of findings based on defined criteria.
     * The exact meaning of these labels depends on the type of the finding and source from where it was obtained. A
     * typical use case could be a labeling system used by an issue tracker to assign additional information to issues.
     */
    val labels: Set<String> = emptySet()
) {
    companion object {
        /**
         * Return a human-readable string that is determined based on the given [scoringSystem] and [severity].
         */
        @Suppress("UNUSED") // This function is used in the templates.
        fun getSeverityString(scoringSystem: String?, severity: String?) =
            when (scoringSystem?.uppercase()) {
                "CVSS2", "CVSSV2" -> severity?.toFloatOrNull()?.let { Cvss2Rating.fromScore(it)?.toString() }
                "CVSS3", "CVSSV3" -> severity?.toFloatOrNull()?.let { Cvss3Rating.fromScore(it)?.toString() }
                else -> null
            } ?: "UNKNOWN"
    }

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
}
