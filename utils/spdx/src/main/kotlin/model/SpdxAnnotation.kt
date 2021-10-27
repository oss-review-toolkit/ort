/*
 * Copyright (C) 2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.utils.spdx.model

import java.time.Instant

import org.ossreviewtoolkit.utils.spdx.SpdxConstants

/**
 * An annotation which can relate to [SpdxDocument]s, [SpdxFile]s, or [SpdxPackage]s.
 */
data class SpdxAnnotation(
    /**
     * The creation date of this annotation.
     */
    val annotationDate: Instant,

    /**
     * The type of this annotation.
     */
    val annotationType: Type,

    /**
     * The person, organization or tool that has created this annotation. The value must be a single line of text in one
     * of the following formats:
     *
     * 1. "Person: person name" or "Person: person name (email)"
     * 2. "Organization: organization name" or "Organization: organization name (email)"
     * 3. "Tool: tool identifier - version"
     */
    val annotator: String,

    /**
     * Comments from the [annotator].
     */
    val comment: String
) {
    enum class Type {
        /**
         * Type of annotation which does not fit in any of the pre-defined annotation types.
         */
        OTHER,

        /**
         * A Review represents an audit and signoff by an individual, organization
         * or tool on the information for an SpdxElement.
         */
        REVIEW;
    }

    init {
        val validPrefixes = listOf(SpdxConstants.PERSON, SpdxConstants.ORGANIZATION, SpdxConstants.TOOL)

        require(validPrefixes.any { annotator.startsWith(it) }) {
            "The annotator has to start with any of $validPrefixes."
        }
    }
}
