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

/**
 * A set where [License]s can be assigned to.
 */
data class LicenseSet(
    /**
     * The unique identifier of this [LicenseSet].
     */
    val id: String,

    /**
     * The name of this [LicenseSet].
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val name: String = "",

    /**
     * A description for this [LicenseSet].
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val description: String = ""
) {
    init {
        require(id.isNotEmpty()) { "The identifier must not be empty." }
    }
}
