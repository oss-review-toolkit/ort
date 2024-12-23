/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * A reference to a BlackDuck Origin, see also
 * https://community.blackduck.com/s/article/What-is-an-Origin-and-Origin-ID-in-Blackduck.
 * Note: While the term Origin is still current, the properties `originId` and `originType` haven been deprecated in
 * favor of `externalNamespace` and `externalId`.
 */
data class BlackDuckOriginReference(
    /**
     * The namespace such as 'maven', 'pypi' or 'github'.
     */
    val externalNamespace: String,

    /**
     * The component's identifier within the external namespace.
     */
    val externalId: String
) {
    @JsonValue
    fun toCoordinates() = listOf(externalNamespace, externalId).joinToString(":")

    companion object {
        @JsonCreator
        fun parse(coordinates: String): BlackDuckOriginReference {
            val index = coordinates.indexOf(':')
            require(index != -1)

            return BlackDuckOriginReference(
                externalNamespace = coordinates.substring(0, index),
                externalId = coordinates.substring(index + 1, coordinates.length)
            )
        }
    }
}
