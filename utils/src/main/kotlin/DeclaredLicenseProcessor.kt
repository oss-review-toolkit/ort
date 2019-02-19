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

import com.fasterxml.jackson.annotation.JsonInclude

import com.here.ort.spdx.SpdxDeclaredLicenseMapping
import com.here.ort.spdx.SpdxExpression

object DeclaredLicenseProcessor {
    fun process(declaredLicense: String) =
            (SpdxDeclaredLicenseMapping.map(declaredLicense) ?: parseLicense(declaredLicense))?.normalize()

    fun process(declaredLicenses: Collection<String>): ProcessedDeclaredLicense {
        val processedLicenses = mutableSetOf<SpdxExpression>()
        val unmapped = mutableListOf<String>()

        declaredLicenses.forEach { declaredLicense ->
            val processedLicense = process(declaredLicense)
            if (processedLicense != null) processedLicenses += processedLicense else unmapped += declaredLicense
        }

        val spdxExpression = when {
            processedLicenses.isEmpty() -> null
            else -> processedLicenses.reduce { left, right -> left and right }
        }

        return ProcessedDeclaredLicense(spdxExpression, unmapped)
    }

    private fun parseLicense(declaredLicense: String) =
            try {
                SpdxExpression.parse(declaredLicense, SpdxExpression.Strictness.ALLOW_ANY)
            } catch (e: Exception) {
                log.debug { "Could not parse declared license '$declaredLicense': ${e.message}" }
                null
            }
}

data class ProcessedDeclaredLicense(
        /**
         * The resulting SPDX expression, or null if no license could be mapped.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val spdxExpression: SpdxExpression?,

        /**
         * Declared licenses that could not be mapped to an SPDX expression.
         */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        val unmapped: List<String> = emptyList()
)
