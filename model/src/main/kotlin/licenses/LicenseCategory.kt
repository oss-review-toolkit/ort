/*
 * Copyright (C) 2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.model.licenses

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * A category where licenses can be assigned to. The assignment is expressed via a [LicenseCategorization]. Categories
 * do not have any specific semantic, but users are free to define their own set of categories.
 */
data class LicenseCategory(
    /**
     * The name of this [LicenseCategory]. The name can be chosen freely, but must be unique over all categories.
     */
    val name: String,

    /**
     * A description for this [LicenseCategory].
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val description: String = ""
) {
    init {
        require(name.isNotEmpty()) { "The name must not be empty." }
    }
}
