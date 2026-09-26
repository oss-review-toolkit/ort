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

import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxLicense

/**
 * A class which maps license strings collected from the declared licenses of Open Source packages to SPDX expressions.
 * The mapping only contains license strings which can *not* be parsed by [SpdxExpression.parse], for example because
 * the license names contain white spaces. See [SpdxSimpleLicenseMapper] for a mapping of varied license names.
 */
class SpdxDeclaredLicenseMapper internal constructor(mapping: Map<String, SpdxExpression>) {
    companion object {
        val MAPPING by lazy { readLicenseMappingResource("/declared-license-mapping.yml") }
        val AMBIGUOUS_MAPPING by lazy { readLicenseMappingResource("/ambiguous-declared-license-mapping.yml") }

        private var instance = SpdxDeclaredLicenseMapper(AMBIGUOUS_MAPPING + MAPPING)

        @Synchronized
        fun configure(mapping: Map<String, SpdxExpression>) {
            instance = SpdxDeclaredLicenseMapper(mapping)
        }

        @Synchronized
        fun getInstance(): SpdxDeclaredLicenseMapper = instance
    }

    /**
     * The map of collected license strings associated with their corresponding SPDX expression.
     */
    private val mapping = mapping.toSortedMap(String.CASE_INSENSITIVE_ORDER)

    /**
     * Return the [SpdxExpression] the [license] string maps to, [SpdxConstants.NONE] if the [license] should be
     * discarded, or null if there is no corresponding expression.
     */
    fun map(license: String) = mapping[license] ?: SpdxLicense.forId(license)?.toExpression()
}

private fun readLicenseMappingResource(name: String): Map<String, SpdxExpression> {
    val resource = checkNotNull(SpdxDeclaredLicenseMapper::class.java.getResource(name))
    return resource.readText().parseYamlKeyValueLines().mapValues { (_, value) ->
        SpdxExpression.parse(value)
    }
}
