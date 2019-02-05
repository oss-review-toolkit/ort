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

package com.here.ort.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * This class assigns a [PackageCurationData] object to a [Package] identified by the [id].
 */
data class PackageCuration(
        /**
         * The identifier of the package.
         */
        val id: Identifier,

        /**
         * The curation data for the package.
         */
        @JsonProperty("curations")
        val data: PackageCurationData
) {
    /**
     * Apply the curation [data] to the provided package.
     *
     * @see [PackageCurationData.apply]
     */
    fun apply(curatedPackage: CuratedPackage): CuratedPackage {
        if (!id.matches(curatedPackage.pkg.id)) {
            throw IllegalArgumentException("Package curation identifier '${id.toCoordinates()}' does not match " +
                    "package identifier '${curatedPackage.pkg.id.toCoordinates()}'.")
        }

        return data.apply(curatedPackage)
    }
}
