/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://apache.org
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
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.toYaml

class SpdxOrLaterLicenseMapperTest : WordSpec({
    "isValidChoice()" should {
        val mapper = SpdxOrLaterLicenseMapper(mapOf("A" to "B", "B" to "C", "U" to "V"))

        "return false if identity is chosen" {
            mapper.isValidChoice(given = "A", choice = "A") shouldBe false
            mapper.isValidChoice(given = "A", choice = "A-only") shouldBe false
            mapper.isValidChoice(given = "A-only", choice = "A-only") shouldBe false
            mapper.isValidChoice(given = "A-only", choice = "A") shouldBe false

            mapper.isValidChoice(given = "A+", choice = "A+") shouldBe false
            mapper.isValidChoice(given = "A+", choice = "A-or-later") shouldBe false
            mapper.isValidChoice(given = "A-or-later", choice = "A-or-later") shouldBe false
            mapper.isValidChoice(given = "A-or-later", choice = "A+") shouldBe false
        }

        "return false if identity is chosen with exception" {
            mapper.isValidChoice(given = "A WITH E", choice = "A WITH E") shouldBe false
            mapper.isValidChoice(given = "A-only  WITH E", choice = "A-only  WITH E") shouldBe false
            mapper.isValidChoice(given = "A+", choice = "A+") shouldBe false
            mapper.isValidChoice(given = "A-or-later  WITH E", choice = "A-or-later  WITH E") shouldBe false
        }

        "return false if direct successor is chosen, and later version is not allowed" {
            mapper.isValidChoice(given = "A", choice = "B") shouldBe false
            mapper.isValidChoice(given = "A", choice = "B-only") shouldBe false
            mapper.isValidChoice(given = "A", choice = "B-or-later") shouldBe false
            mapper.isValidChoice(given = "A", choice = "B+") shouldBe false

            mapper.isValidChoice(given = "A-only", choice = "B") shouldBe false
            mapper.isValidChoice(given = "A-only", choice = "B-only") shouldBe false
            mapper.isValidChoice(given = "A-only", choice = "B-or-later") shouldBe false
            mapper.isValidChoice(given = "A-only", choice = "B+") shouldBe false
        }

        "return false if a predecessory is chosen, and later version is allowed" {
            mapper.isValidChoice(given = "B-or-later", choice = "A") shouldBe false
            mapper.isValidChoice(given = "B-or-later", choice = "A-only") shouldBe false
            mapper.isValidChoice(given = "B-or-later", choice = "A-or-later") shouldBe false
        }

        "return true if direct successor is chosen, and later version is not allowed" {
            mapper.isValidChoice(given = "A+", choice = "B") shouldBe true
            mapper.isValidChoice(given = "A+", choice = "B-only") shouldBe true
            mapper.isValidChoice(given = "A+", choice = "B-or-later") shouldBe true
            mapper.isValidChoice(given = "A+", choice = "B+") shouldBe true

            mapper.isValidChoice(given = "A-or-later", choice = "B") shouldBe true
            mapper.isValidChoice(given = "A-or-later", choice = "B-only") shouldBe true
            mapper.isValidChoice(given = "A-or-later", choice = "B-or-later") shouldBe true
            mapper.isValidChoice(given = "A-or-later", choice = "B+") shouldBe true
        }

        "return true if transitive successor is chosen, and later version is allowed" {
            mapper.isValidChoice(given = "A+", choice = "C") shouldBe true
            mapper.isValidChoice(given = "A+", choice = "C-only") shouldBe true
            mapper.isValidChoice(given = "A+", choice = "C-or-later") shouldBe true
            mapper.isValidChoice(given = "A+", choice = "C+") shouldBe true

            mapper.isValidChoice(given = "A-or-later", choice = "C") shouldBe true
            mapper.isValidChoice(given = "A-or-later", choice = "C-only") shouldBe true
            mapper.isValidChoice(given = "A-or-later", choice = "C-or-later") shouldBe true
            mapper.isValidChoice(given = "A-or-later", choice = "C+") shouldBe true
        }

        "return false if an unrelated license is chosen, and later version is allowed" {
            mapper.isValidChoice(given = "A+", choice = "U") shouldBe false
            mapper.isValidChoice(given = "A+", choice = "U-only") shouldBe false
            mapper.isValidChoice(given = "A+", choice = "U-or-later") shouldBe false
            mapper.isValidChoice(given = "A+", choice = "U+") shouldBe false

            mapper.isValidChoice(given = "A-or-later", choice = "U") shouldBe false
            mapper.isValidChoice(given = "A-or-later", choice = "U-only") shouldBe false
            mapper.isValidChoice(given = "A-or-later", choice = "U-or-later") shouldBe false
            mapper.isValidChoice(given = "A-or-later", choice = "U+") shouldBe false
        }

        "return true if transitive successor is chosen and exceptions are identical" {
            mapper.isValidChoice(given = "A+ WITH E", choice = "B+ WITH E") shouldBe true
            mapper.isValidChoice(given = "A+ WITH E", choice = "B-only WITH E") shouldBe true
            mapper.isValidChoice(given = "A+ WITH E", choice = "C+ WITH E") shouldBe true
            mapper.isValidChoice(given = "A+ WITH E", choice = "C-only WITH E") shouldBe true
        }

        "return false if transitive successor is chosen and exceptions are not identical" {
            mapper.isValidChoice(given = "A+ WITH E", choice = "B+ WITH F") shouldBe false
            mapper.isValidChoice(given = "A+ WITH E", choice = "B-only WITH F") shouldBe false
            mapper.isValidChoice(given = "A+ WITH E", choice = "C+ WITH F") shouldBe false
            mapper.isValidChoice(given = "A+ WITH E", choice = "C-only WITH F") shouldBe false
        }

        "handle malformed edge cases" {
            mapper.isValidChoice(given = "A-only+", choice = "C-only") shouldBe true
            mapper.isValidChoice(given = "A-or-later", choice = "C-only+") shouldBe true
            mapper.isValidChoice(given = "A-or-later", choice = "C-or-later+") shouldBe true
        }
    }

    "offersChoice()" should {
        val mapper = SpdxOrLaterLicenseMapper(mapOf("A" to "B"))

        "return the expected values for variations of a base ID contained in the map" {
            mapper.offersChoice("A") shouldBe false
            mapper.offersChoice("A-only") shouldBe false
            mapper.offersChoice("A-or-later") shouldBe true
            mapper.offersChoice("A+") shouldBe true
            mapper.offersChoice("A-only+") shouldBe true

            mapper.offersChoice("A WITH E") shouldBe false
            mapper.offersChoice("A-only WITH E") shouldBe false
            mapper.offersChoice("A-or-later WITH E") shouldBe true
            mapper.offersChoice("A+ WITH E") shouldBe true
            mapper.offersChoice("A-only+ WITH E") shouldBe true
        }

        "return the expected values for variations of a base ID not contained in the map" {
            mapper.offersChoice("C") shouldBe false
            mapper.offersChoice("C-only") shouldBe false
            mapper.offersChoice("C+") shouldBe true
            mapper.offersChoice("C-or-later") shouldBe true

            mapper.offersChoice("C WITH E") shouldBe false
            mapper.offersChoice("C-only WITH E") shouldBe false
            mapper.offersChoice("C-or-later WITH E") shouldBe true
            mapper.offersChoice("C+ WITH E") shouldBe true
            mapper.offersChoice("C-only+ WITH E") shouldBe true
        }
    }

    "INSTANCE.isValidChoice()" should {
        val mapper = SpdxOrLaterLicenseMapper.INSTANCE

        "handle the plus operator for non-GNU licenses correctly" {
            mapper.isValidChoice(given = "Apache-2.0+", choice = "Apache-2.0") shouldBe true
            mapper.isValidChoice(given = "Apache-2.0", choice = "Apache-2.0+") shouldBe false
        }

        "handle GPL licenses correctly" {
            mapper.isValidChoice(given = "GPL-1.0-or-later", choice = "GPL-2.0-only") shouldBe true
            mapper.isValidChoice(given = "GPL-1.0-or-later", choice = "GPL-3.0-only") shouldBe true
            mapper.isValidChoice(given = "GPL-2.0-or-later", choice = "GPL-3.0-or-later") shouldBe true

            mapper.isValidChoice(given = "GPL-3.0-only", choice = "GPL-1.0-only") shouldBe false
            mapper.isValidChoice(given = "GPL-3.0-only", choice = "GPL-2.0-only") shouldBe false
            mapper.isValidChoice(given = "GPL-3.0-or-later", choice = "GPL-2.0-only") shouldBe false
            mapper.isValidChoice(given = "GPL-3.0-or-later", choice = "GPL-2.0-or-later") shouldBe false
        }

        "handle GPL license with exception correctly" {
            mapper.isValidChoice(
                given = "GPL-1.0-or-later WITH Classpath-exception-2.0",
                choice = "GPL-2.0-only WITH Classpath-exception-2.0"
            ) shouldBe true

            mapper.isValidChoice(
                given = "GPL-1.0-or-later WITH Universal-FOSS-Exception-1.0",
                choice = "GPL-2.0-only WITH Classpath-exception-2.0"
            ) shouldBe false

            mapper.isValidChoice(
                given = "GPL-1.0-or-later",
                choice = "GPL-2.0-only WITH Classpath-exception-2.0"
            ) shouldBe false

            mapper.isValidChoice(
                given = "GPL-1.0-or-later WITH Classpath-exception-2.0",
                choice = "GPL-2.0-only"
            ) shouldBe false
        }

        "handle licenses with differing base ID correctly" {
            mapper.isValidChoice(given = "GPL-2.0-or-later", choice = "LGPL-2.1-only") shouldBe false
            mapper.isValidChoice(given = "Apache-2.0+", choice = "MIT") shouldBe false
        }

        "reject licenses with older version" {
            mapper.isValidChoice(given = "GPL-2.0-or-later", choice = "GPL-1.0-only") shouldBe false
        }

        "handle malformed edge cases" {
            mapper.isValidChoice(given = "GPL-1.0-only+", choice = "GPL-3.0-only") shouldBe true
            mapper.isValidChoice(given = "GPL-1.0-or-later", choice = "GPL-2.0-only+") shouldBe true
            mapper.isValidChoice(given = "GPL-2.0-or-later", choice = "GPL-3.0-or-later+") shouldBe true
        }
    }

    "INSTANCE.satisfyingIdsForId" should {
        "be as expected" {
            val expectedSuccorsForId = javaClass.getResource(
                "/spdx-or-later-mapper-expected-satisfying-ids-for-id.yml"
            ).shouldNotBeNull().readText()

            SpdxOrLaterLicenseMapper.INSTANCE.satisfyingIdsForId.mapValuesTo(sortedMapOf()) {
                it.value.sorted()
            }.toYaml() shouldBe expectedSuccorsForId
        }

        "not contain any license IDs with '+', '-only', or '-or-later' suffixes" {
            val ids = buildSet {
                addAll(SpdxOrLaterLicenseMapper.INSTANCE.satisfyingIdsForId.values.flatten())
                addAll(SpdxOrLaterLicenseMapper.INSTANCE.satisfyingIdsForId.keys)
            }

            ids.forAll { id ->
                id.endsWith("+") shouldBe false
                id.endsWith("-only") shouldBe false
                id.endsWith("-or-later") shouldBe false
            }
        }
    }
})

private fun SpdxOrLaterLicenseMapper.isValidChoice(given: String, choice: String) =
    isValidChoice(SpdxSingleLicenseExpression.parse(given), SpdxSingleLicenseExpression.parse(choice))

private fun SpdxOrLaterLicenseMapper.offersChoice(singleLicense: String) =
    offersChoice(SpdxSingleLicenseExpression.parse(singleLicense))
