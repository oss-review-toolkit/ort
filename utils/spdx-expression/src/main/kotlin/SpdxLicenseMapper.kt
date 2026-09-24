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
 * A class which unstructured license strings from Open Source packages to SPDX expressions. It only targets license
 * strings which can *not* be parsed by [SpdxExpression.parse], for example because the license strings contain white
 * spaces. See [SpdxDeprecatedLicenseMapper] for a mapping of varied license names.
 */
object SpdxLicenseMapper {
    /**
     * The raw map which associates unstructured license strings with their corresponding SPDX expression.
     */
    internal val rawMapping: Map<String, SpdxExpression> by lazy {
        val resource = checkNotNull(javaClass.getResource("/license-mapping.yml"))
        resource.readText().parseYamlKeyValueLines().mapValues { (_, value) ->
            SpdxExpression.parse(value)
        }
    }

    /**
     * The map which associates unstructured license strings with their corresponding SPDX expression.
     */
    val mapping = rawMapping.toSortedMap(String.CASE_INSENSITIVE_ORDER)

    /**
     * Return the [SpdxExpression] the given [license] string maps to, [SpdxConstants.NONE] if the [license] should be
     * discarded, or null if there is no corresponding expression.
     */
    fun map(license: String) = mapping[license] ?: SpdxLicense.forId(license)?.toExpression()
}
