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
)
