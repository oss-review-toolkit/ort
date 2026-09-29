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
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveAtMostSize
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.containADigit
import io.kotest.matchers.types.beOfType

import org.ossreviewtoolkit.utils.common.getDuplicates
import org.ossreviewtoolkit.utils.spdx.SpdxLicense
import org.ossreviewtoolkit.utils.spdxexpression.parser.SpdxExpressionLexer
import org.ossreviewtoolkit.utils.spdxexpression.parser.SpdxExpressionParser
import org.ossreviewtoolkit.utils.spdxexpression.parser.Token

class SpdxSimpleLicenseMapperTest : WordSpec({
    "The mapping" should {
        val mapping = SpdxSimpleLicenseMapper.simpleExpressionMapping +
            SpdxSimpleLicenseMapper.deprecatedExpressionMapping

        "be case-insensitive" {
            mapping.forAll { (key, license) ->
                SpdxSimpleLicenseMapper.map(key.lowercase()) shouldBe license
                SpdxSimpleLicenseMapper.map(key.uppercase()) shouldBe license
            }
        }

        "contain only single ID strings" {
            mapping.keys.forAll { id ->
                val tokens = SpdxExpressionLexer(id).tokens().toList()

                tokens shouldHaveAtLeastSize 1
                tokens shouldHaveAtMostSize 2

                tokens.first() should beOfType<Token.IDENTIFIER>()
                tokens.getOrNull(1)?.let { it should beOfType<Token.PLUS>() }

                SpdxExpressionParser(tokens.asSequence()).parse().toString() shouldBe id
            }
        }

        "not associate licenses without a version to *-only" {
            mapping.forAll { (key, license) ->
                if (license.toString().endsWith("-only")) key should containADigit()
            }
        }

        "not contain any duplicate keys with respect to capitalization" {
            val duplicates = mapping.keys.getDuplicates { it.lowercase() }

            duplicates should beEmpty()
        }
    }

    "The simple mapping" should {
        "not contain any deprecated values" {
            SpdxSimpleLicenseMapper.simpleExpressionMapping.values.forAll {
                SpdxLicense.forId(it.toString()).shouldNotBeNull().deprecated shouldBe false
            }
        }

        "not contain plain SPDX license ids" {
            SpdxSimpleLicenseMapper.simpleExpressionMapping.keys.forAll { declaredLicense ->
                SpdxLicense.forId(declaredLicense) should beNull()
            }
        }
    }
})
