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
@JsonInclude(JsonInclude.Include.NON_EMPTY)
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
     * Contains a time when this defect has been closed if it has been resolved already (and this information is
     * available in the source system). For users of the component affected by this defect, this information can be of
     * interest to find out whether a fix is available, maybe in a newer version.
     */
    val closingTime: Instant? = null,

    /**
     * Contains the version of the release, in which this defect was fixed if available. This is important information
     * for consumers of the component affected by the defect, so they can upgrade to this version.
     */
    val fixReleaseVersion: String? = null,

    /**
     * A URL pointing to the release, in which this defect was fixed if available. Depending on the information
     * provided by a source, this URL could point to a website with detail information about the release, to release
     * notes, or something like that. This information is important for consumers of the component affected by this
     * defect, so they can upgrade to this release.
     */
    val fixReleaseUrl: String? = null,

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
