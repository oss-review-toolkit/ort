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
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveAtMostSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.containADigit
import io.kotest.matchers.types.beOfType

import org.ossreviewtoolkit.utils.spdx.parser.SpdxExpressionLexer
import org.ossreviewtoolkit.utils.spdx.parser.SpdxExpressionParser
import org.ossreviewtoolkit.utils.spdx.parser.Token

class SpdxSimpleLicenseMappingTest : WordSpec({
    "The simple license mapping" should {
        "not contain any duplicate keys with respect to capitalization" {
            val keys = SpdxSimpleLicenseMapping.simpleLicenseMapping.keys.toMutableList()
            val uniqueKeys = SpdxSimpleLicenseMapping.simpleExpressionMapping.keys

            // Remove keys one by one as calling "-" would remove all occurrences of a key.
            uniqueKeys.forEach { uniqueKey -> keys.remove(uniqueKey) }

            keys should beEmpty()
        }

        "not contain any deprecated values" {
            SpdxSimpleLicenseMapping.simpleLicenseMapping.values.forAll {
                it.deprecated shouldBe false
            }
        }

        "not associate licenses without a version to *-only" {
            SpdxSimpleLicenseMapping.simpleLicenseMapping.forAll { (key, license) ->
                if (license.id.endsWith("-only")) key should containADigit()
            }
        }
    }

    "The simple expression mapping" should {
        "contain only single ID strings" {
            val ids = SpdxSimpleLicenseMapping.simpleExpressionMapping.keys +
                SpdxSimpleLicenseMapping.deprecatedExpressionMapping.keys

            ids.forAll { id ->
                val tokens = SpdxExpressionLexer(id).tokens().toList()

                tokens shouldHaveAtLeastSize 1
                tokens shouldHaveAtMostSize 2

                tokens.first() should beOfType<Token.IDENTIFIER>()
                tokens.getOrNull(1)?.let { it should beOfType<Token.PLUS>() }

                SpdxExpressionParser(tokens.asSequence()).parse().toString() shouldBe id
            }
        }

        "not contain plain SPDX license ids" {
            SpdxSimpleLicenseMapping.simpleExpressionMapping.keys.forAll { declaredLicense ->
                SpdxLicense.forId(declaredLicense) should beNull()
            }
        }

        "be case-insensitive" {
            SpdxSimpleLicenseMapping.simpleExpressionMapping.forAll { (key, license) ->
                SpdxSimpleLicenseMapping.map(key.lowercase(), mapDeprecated = false) shouldBe license
                SpdxSimpleLicenseMapping.map(key.uppercase(), mapDeprecated = false) shouldBe license
                SpdxSimpleLicenseMapping.map(key.lowercase(), mapDeprecated = true) shouldBe license
                SpdxSimpleLicenseMapping.map(key.uppercase(), mapDeprecated = true) shouldBe license
            }
        }
    }
})
