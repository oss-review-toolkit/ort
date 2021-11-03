/*
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

import org.ossreviewtoolkit.utils.common.titlecase

class SpdxSimpleLicenseMappingTest : WordSpec({
    "The raw map" should {
        "not contain any duplicate keys with respect to capitalization" {
            val keys = SpdxSimpleLicenseMapping.customLicenseIdsMap.keys.toMutableList()
            val uniqueKeys = SpdxSimpleLicenseMapping.customLicenseIds.keys

            // Remove keys one by one as calling "-" would remove all occurrences of a key.
            uniqueKeys.forEach { uniqueKey -> keys.remove(uniqueKey) }

            keys should beEmpty()
        }

        "not contain any deprecated values" {
            SpdxSimpleLicenseMapping.customLicenseIdsMap.values.forAll {
                it.deprecated shouldBe false
            }
        }

        "not associate licenses without a version to *-only" {
            SpdxSimpleLicenseMapping.customLicenseIdsMap.asSequence().forAll { (key, license) ->
                if (license.id.endsWith("-only")) key should containADigit()
            }
        }
    }

    "The mapping" should {
        "contain only single ID strings" {
            SpdxSimpleLicenseMapping.mapping.keys.forAll { declaredLicense ->
                val tokens = getTokensByTypeForExpression(declaredLicense)
                val types = tokens.map { (type, _) -> type }

                tokens shouldHaveAtLeastSize 1
                tokens shouldHaveAtMostSize 2
                tokens.joinToString("") { (_, text) -> text } shouldBe declaredLicense
                types.first() shouldBe SpdxExpressionLexer.IDSTRING
                types.getOrElse(1) { SpdxExpressionLexer.PLUS } shouldBe SpdxExpressionLexer.PLUS
            }
        }

        "not contain plain SPDX license ids" {
            SpdxSimpleLicenseMapping.customLicenseIds.keys.forAll { declaredLicense ->
                SpdxLicense.forId(declaredLicense) should beNull()
            }
        }

        "be case-insensitive" {
            SpdxSimpleLicenseMapping.customLicenseIds.asSequence().forAll { (key, license) ->
                SpdxSimpleLicenseMapping.map(key.lowercase(), mapDeprecated = false) shouldBe license
                SpdxSimpleLicenseMapping.map(key.uppercase(), mapDeprecated = false) shouldBe license
                SpdxSimpleLicenseMapping.map(key.titlecase(), mapDeprecated = false) shouldBe license
                SpdxSimpleLicenseMapping.map(key.lowercase(), mapDeprecated = true) shouldBe license
                SpdxSimpleLicenseMapping.map(key.uppercase(), mapDeprecated = true) shouldBe license
                SpdxSimpleLicenseMapping.map(key.titlecase(), mapDeprecated = true) shouldBe license
            }
        }
    }
})
