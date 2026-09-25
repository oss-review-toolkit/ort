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
 * A mapping from simple license names to valid SPDX license IDs. This mapping only contains deprecated and
 * non-deprecated license IDs from the SPDX license list. Deprecated license IDs are mapped to their non-deprecated
 * counterparts, that should be used instead. For non-deprecated license IDs the identity is returned.
 *
 * For mapping of unparsable license strings see [SpdxLicenseMapper].
 */
object SpdxDeprecatedLicenseMapper {
    /**
     * The map of deprecated SPDX license IDs associated with their current [SPDX expression]
     * [SpdxSingleLicenseExpression].
     */
    val mapping: Map<String, SpdxSingleLicenseExpression> by lazy {
        val resource = checkNotNull(javaClass.getResource("/deprecated-license-mapping.yml"))
        val mapping = resource.readText().parseYamlKeyValueLines().mapValues { (_, value) ->
            SpdxSingleLicenseExpression.parse(value)
        }

        mapping.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }

    /**
     * Return the [SpdxSingleLicenseExpression] the [license] maps to, or null if there is no corresponding expression.
     * Licenses marked as deprecated in the SPDX standard are mapped to their corresponding current expression.
     */
    fun map(license: String) = mapping[license] ?: SpdxLicense.forId(license)?.toExpression()
}
