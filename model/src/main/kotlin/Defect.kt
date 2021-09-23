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
 * A data model for software defects.
 *
 * Instances of this class are created by advisor implementations that retrieve information about known defects in
 * packages.
 */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
data class Defect(
    /**
     * The (external) ID of this defect. This is a string used by a concrete issue tracker system to reference this
     * defect, such as a bug ID or ticket number.
     */
    val id: String,

    /**
     * The URL pointing to the source of this defect. This is typically a reference into the issue tracker system that
     * contains this defect.
     */
    val url: URI,

    /**
     * A title for this defect if available. This is a short summary describing the problem at hand.
     */
    val title: String? = null,

    /**
     * A state of the associated defect if available. The concrete meaning of this string depends on the source from
     * where it was obtained, as different issue tracker systems use their specific terminology. Possible values could
     * be *OPEN*, *IN PROGRESS*, *BLOCKED*, etc.
     */
    val state: String? = null,

    /**
     * The severity assigned to the defect if available. The meaning of this string depends on the source system.
     */
    val severity: String? = null,

    /**
     * An optional description of this defect. It can contain more detailed information about the defect and its
     * impact. The field may be undefined if the [url] of this defect already points to a website with all this
     * information.
     */
    val description: String? = null,

    /**
     * The creation time of this defect if available.
     */
    val creationTime: Instant? = null,

    /**
     * Contains a time when this defect has been modified the last time in the tracker system it has been obtained
     * from. This information can be useful for instance to find out how up-to-date this defect report might be.
     */
    val modificationTime: Instant? = null,

    /**
     * A map with labels assigned to this defect. Labels provide a means frequently used by issue tracker systems to
     * classify defects based on defined criteria. The exact meaning of these labels is depending on the source system.
     */
    val labels: Map<String, String> = emptyMap()
) {
    init {
        require(id.isNotBlank()) { "Defect ID must not be blank." }
    }
}
