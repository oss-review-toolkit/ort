/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.containADigit
import io.kotest.matchers.string.shouldContain

import org.ossreviewtoolkit.utils.spdx.parser.SpdxExpressionLexer

class SpdxDeclaredLicenseMappingTest : WordSpec({
    "The list" should {
        "not contain any duplicate keys with respect to capitalization" {
            val keys = SpdxDeclaredLicenseMapping.rawMapping.keys.toMutableList()
            val uniqueKeys = SpdxDeclaredLicenseMapping.mapping.keys

            // Remove keys one by one as calling "-" would remove all occurrences of a key.
            uniqueKeys.forEach { uniqueKey -> keys.remove(uniqueKey) }

            keys should beEmpty()
        }

        "not contain any deprecated values" {
            SpdxDeclaredLicenseMapping.rawMapping.values.forAll {
                it.isValid(SpdxExpression.Strictness.ALLOW_CURRENT) shouldBe true
            }
        }

        "not associate licenses without a version to *-only" {
            val keysWithImpliedVersion = listOf(
                // See http://www.gwtproject.org/terms.html#licenses which explicitly mentions "GNU Lesser General
                // Public License v. 2.1".
                "GWT Terms",
                "http://www.gwtproject.org/terms.html",
                // This forwards to http://www.gnu.org/licenses/lgpl-3.0.html which has a version in the URL.
                "http://www.gnu.org/copyleft/lesser.html"
            )

            SpdxDeclaredLicenseMapping.rawMapping.forAll { (key, license) ->
                if (key !in keysWithImpliedVersion && license.licenses().any { it.endsWith("-only") }) {
                    key should containADigit()
                }
            }
        }
    }

    "The mapping" should {
        "not contain single ID strings" {
            val licenseIdMapping = SpdxDeclaredLicenseMapping.mapping.filter { (_, expression) ->
                expression is SpdxLicenseIdExpression
            }

            licenseIdMapping.keys.forAll { declaredLicense ->
                @Suppress("SwallowedException")
                try {
                    val tokens = SpdxExpressionLexer(declaredLicense).tokens().toList()

                    tokens.size shouldBeGreaterThanOrEqual 2

                    if (tokens.size == 2) {
                        // Rule out that the 2 tokens are caused by IDSTRING and PLUS.
                        declaredLicense shouldContain " "
                    }
                } catch (e: SpdxException) {
                    // For untokenizable strings no further checks are needed.
                }
            }
        }

        "not contain plain SPDX license ids" {
            SpdxDeclaredLicenseMapping.mapping.keys.forAll { declaredLicense ->
                SpdxLicense.forId(declaredLicense) should beNull()
            }
        }

        "be case-insensitive" {
            SpdxDeclaredLicenseMapping.mapping.forAll { (key, license) ->
                SpdxDeclaredLicenseMapping.map(key.lowercase()) shouldBe license
                SpdxDeclaredLicenseMapping.map(key.uppercase()) shouldBe license
            }
        }
    }
})
