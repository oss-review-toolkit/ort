/*
 * Copyright (C) 2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.model.licenses

import com.fasterxml.jackson.annotation.JsonInclude

import com.here.ort.spdx.SpdxExpression

import java.util.SortedSet

/**
 * A class for configuring meta data for a specific license refered to by a SPDX license identifier.
 */
data class License(
    /**
     * The SPDX identifier of this [License]. The value has to be either a compound expression of one license with an
     * exception or no compound expression at all.
     */
    val id: String,

    /**
     * The identifiers of the [LicenseSet]s this license is assigned to.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val sets: SortedSet<String>,

    /**
     * Defines whether the license text should be placed inside the NOTICE file.
     */
    val includeInNoticeFile: Boolean,

    /**
     * Defines whether a source code offer should be made for this license.
     */
    val includeSourceCodeOfferInNoticeFile: Boolean
) {
    init {
        require(SpdxExpression.parse(id).licenses().size == 1) {
            "The id '$id' contains multiple licenses which is not allowed."
        }
    }
}
