/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.utils.spdxexpression

import org.ossreviewtoolkit.utils.spdx.SpdxLicense

/**
 * A class which maps simple license names to valid SPDX license IDs. This mapping only contains license strings which
 * *can* be parsed by [SpdxExpression.parse] but have a corresponding valid SPDX license ID that should be used instead.
 * See [SpdxDeclaredLicenseMapper] for mapping unparsable license strings.
 */
object SpdxSimpleLicenseMapper {
    /**
     * The map of simple license names associated with their corresponding [SPDX expression][SpdxLicenseIdExpression].
     */
    val caseInsensitiveSimpleMapping: Map<String, SpdxSimpleExpression> by lazy {
        readLicenseMappingResource<SpdxSimpleExpression>("/simple-license-mapping.yml")
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }

    /**
     * The map of deprecated SPDX license IDs associated with their current [SPDX expression]
     * [SpdxSingleLicenseExpression].
     */
    val caseInsensitiveDeprecatedMapping: Map<String, SpdxSingleLicenseExpression> by lazy {
        readLicenseMappingResource<SpdxSingleLicenseExpression>("/deprecated-license-mapping.yml")
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }

    /**
     * Return the [SpdxSingleLicenseExpression] the [license] maps to, or null if there is no corresponding expression.
     * Licenses marked as deprecated in the SPDX standard are mapped to their corresponding current expression.
     * Licenses that are commonly known abbreviations or aliases are mapped to their corresponding official expression.
     */
    fun map(license: String): SpdxSingleLicenseExpression? {
        caseInsensitiveDeprecatedMapping[license]?.also { return it }
        caseInsensitiveSimpleMapping[license]?.also { return it }
        return SpdxLicense.forId(license)?.toExpression()
    }
}
