/*
 * Copyright (C) 2020-2021 HERE Europe B.V.
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

import com.fasterxml.jackson.annotation.JsonInclude

import java.time.Instant

/**
 * Metadata corresponding to a [SpdxDocument].
 */
data class SpdxCreationInfo(
    /**
     * A general comment about the creation of the [SpdxDocument] or any other relevant comment not included in
     * the other fields.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val comment: String = "",

    /**
     * The date and time the [SpdxDocument] was created.
     * Format: YYYY-MM-DDThh:mm:ssZ
     */
    val created: Instant,

    /**
     * The list of subjects who created the related [SpdxDocument]. At least one must be provided. The format equals the
     * one for [SpdxAnnotation.annotator].
     */
    val creators: List<String> = emptyList(),

    /**
     * The version of SPDX license list (https://spdx.dev/licenses/) used in the related [SpdxDocument].
     * Data Format: "M.N"
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val licenseListVersion: String = ""

) {
    init {
        require(creators.isNotEmpty()) { "Creators must contain at least one entry, but was empty." }
    }
}
