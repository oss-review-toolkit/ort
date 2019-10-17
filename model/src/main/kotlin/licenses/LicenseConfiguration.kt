/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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
 * A configuration for licenses which allows to assign meta data to licenses. It allows assigning licenses to sets which
 * are useful for making license classifications. The available license sets are implicitly defined by the references
 * from the [licenses] and it is optional to define the meta data for any license set via an entry in [licenseSets].
 */
data class LicenseConfiguration(
    /**
     * Defines meta data for the license sets.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val licenseSets: List<LicenseSet> = emptyList(),

    /**
     * Defines meta data for licenses.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val licenses: List<License> = emptyList()
) {
    init {
        licenseSets.groupBy { it.id }.values.filter { it.size > 1 }.let { groups ->
            require(groups.isEmpty()) {
                "Found multiple license set entries with the same Id: ${groups.joinToString { it.first().id }}."
            }
        }
        licenses.groupBy { it.id }.values.filter { it.size > 1 }.let { groups ->
            require(groups.isEmpty()) {
                "Found multiple license entries with the same Id: ${groups.joinToString { it.first().id }}."
            }
        }
    }
}
