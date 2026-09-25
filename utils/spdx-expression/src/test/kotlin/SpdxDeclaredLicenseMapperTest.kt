/*
 * Copyright (C) 2019 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.containADigit
import io.kotest.matchers.string.shouldContain

import org.ossreviewtoolkit.utils.common.getDuplicates
import org.ossreviewtoolkit.utils.spdx.SpdxLicense
import org.ossreviewtoolkit.utils.spdxexpression.parser.SpdxExpressionLexer

class SpdxDeclaredLicenseMapperTest : WordSpec({
    "The raw mapping" should {
        "not contain any duplicate keys with respect to capitalization" {
            val duplicates = SpdxDeclaredLicenseMapper.rawMapping.keys.getDuplicates { it.lowercase() }

            duplicates should beEmpty()
        }

        "not contain any deprecated values" {
            SpdxDeclaredLicenseMapper.rawMapping.values.forAll {
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

            SpdxDeclaredLicenseMapper.rawMapping.forAll { (key, license) ->
                if (key !in keysWithImpliedVersion && license.licenses().any { it.endsWith("-only") }) {
                    key should containADigit()
                }
            }
        }

        "not contain single ID strings" {
            val licenseIdMapping = SpdxDeclaredLicenseMapper.rawMapping.filter { (_, expression) ->
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
            SpdxDeclaredLicenseMapper.rawMapping.keys.forAll { declaredLicense ->
                SpdxLicense.forId(declaredLicense) should beNull()
            }
        }
    }

    "map()" should {
        "be case-insensitive" {
            SpdxDeclaredLicenseMapper.rawMapping.forAll { (key, license) ->
                SpdxDeclaredLicenseMapper.map(key.lowercase()) shouldBe license
                SpdxDeclaredLicenseMapper.map(key.uppercase()) shouldBe license
            }
        }
    }
})
