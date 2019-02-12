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

package com.here.ort.utils

import ch.frankel.slf4k.*

import com.here.ort.spdx.SpdxExpression
import com.here.ort.spdx.SpdxLicenseStringMapping

class DeclaredLicenseProcessor {
    fun processDeclaredLicense(declaredLicense: String): SpdxExpression? {
        val spdxExpression = SpdxLicenseStringMapping.map(declaredLicense) ?: parseLicense(declaredLicense)

        return spdxExpression?.normalize()
    }

    private fun parseLicense(declaredLicense: String) =
            try {
                SpdxExpression.parse(declaredLicense, SpdxExpression.Strictness.ALLOW_ANY)
            } catch (e: Exception) {
                log.debug { "Could not parse declared license '$declaredLicense': ${e.message}" }
                null
            }
}
