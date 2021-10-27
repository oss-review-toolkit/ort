/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.utils.spdx

import com.fasterxml.jackson.module.kotlin.readValue

/**
 * A mapping from simple license names to valid SPDX license IDs. This mapping only contains license strings which *can*
 * be parsed by [SpdxExpression.parse] but have a corresponding valid SPDX license ID that should be used instead. When
 * mapping a name without any indication of a version to an ID with a version, the most commonly used version at the
 * time of writing is used. See [SpdxDeclaredLicenseMapping] for a mapping of unparsable license strings.
 */
object SpdxSimpleLicenseMapping {
    /**
     * The raw map which associates custom license IDs with their corresponding SPDX license ID.
     */
    internal val customLicenseIdsMap by lazy {
        val resource = javaClass.getResource("/simple-license-mapping.yml")
        yamlMapper.readValue<Map<String, SpdxLicense>>(resource)
    }

    /**
     * The map of custom license ids associated with their corresponding SPDX expression.
     */
    internal val customLicenseIds = customLicenseIdsMap.mapValues { (_, v) -> v.toExpression() }
        .toSortedMap(String.CASE_INSENSITIVE_ORDER)

    /**
     * The map of deprecated SPDX license ids associated with their current SPDX expression.
     */
    private val deprecatedLicenseIds by lazy {
        val resource = javaClass.getResource("/deprecated-license-mapping.yml")
        yamlMapper.readValue<Map<String, SpdxSingleLicenseExpression>>(resource)
    }

    /**
     * The map of varied SPDX license ids associated with their corresponding SPDX expression.
     */
    val mapping = (customLicenseIds + deprecatedLicenseIds).toSortedMap(String.CASE_INSENSITIVE_ORDER)

    /**
     * Return the [SpdxExpression] the [license] id maps to, or null if there is no corresponding expression. If
     * [mapDeprecated] is true, license ids marked as deprecated in the SPDX standard are mapped to their
     * corresponding current expression, otherwise they are mapped to their corresponding deprecated expression.
     */
    fun map(license: String, mapDeprecated: Boolean = true) =
        (if (mapDeprecated) mapping else customLicenseIds)[license] ?: SpdxLicense.forId(license)?.toExpression()
}
