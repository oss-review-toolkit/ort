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
 * A mapping from license strings collected from the declared licenses of Open Source packages to SPDX expressions. This
 * mapping only contains license strings which can *not* be parsed by [SpdxExpression.parse], for example because the
 * license names contain white spaces. See [SpdxSimpleLicenseMapping] for a mapping of varied license names.
 */
object SpdxDeclaredLicenseMapping {
    /**
     * The raw map which associates collected license strings with their corresponding SPDX expression.
     */
    internal val rawMapping by lazy {
        val resource = javaClass.getResource("/declared-license-mapping.yml")
        yamlMapper.readValue<Map<String, SpdxExpression>>(resource)
    }

    /**
     * The map of collected license strings associated with their corresponding SPDX expression.
     */
    val mapping = rawMapping.toSortedMap(String.CASE_INSENSITIVE_ORDER)

    /**
     * Return the [SpdxExpression] the [license] string maps to, [SpdxConstants.NONE] if the [license] should be
     * discarded, or null if there is no corresponding expression.
     */
    fun map(license: String) = mapping[license] ?: SpdxLicense.forId(license)?.toExpression()
}
