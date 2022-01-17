/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model.licenses

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

import java.util.Collections.emptySortedSet

import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class LicenseClassificationsTest : WordSpec({
    "init()" should {
        "detect duplicate category names" {
            val cat1 = LicenseCategory("Category 1")
            val cat2 = LicenseCategory("Category 2", "Another category")
            val cat3 = LicenseCategory("Category 1", "Duplicate; should cause a failure")

            val exception = shouldThrow<IllegalArgumentException> {
                LicenseClassifications(categories = listOf(cat1, cat2, cat3))
            }
            exception.message shouldContain "[Category 1]"
        }

        "detect duplicate license IDs" {
            val lic1 = LicenseCategorization(
                SpdxSingleLicenseExpression.parse("ASL-1"), emptySortedSet()
            )
            val lic2 = LicenseCategorization(
                SpdxSingleLicenseExpression.parse("ASL-2"), emptySortedSet()
            )
            val lic3 = LicenseCategorization(
                SpdxSingleLicenseExpression.parse("ASL-1"), sortedSetOf("permissive")
            )

            val exception = shouldThrow<IllegalArgumentException> {
                LicenseClassifications(categorizations = listOf(lic1, lic2, lic3))
            }
            exception.message shouldContain "[ASL-1]"
        }

        "detect licenses referencing non-existing categories" {
            val cat1 = LicenseCategory("Category 1")
            val cat2 = LicenseCategory("Category 2")
            val lic1 = LicenseCategorization(
                SpdxSingleLicenseExpression.parse("ASL-1"), sortedSetOf(cat1.name)
            )
            val lic2 = LicenseCategorization(
                SpdxSingleLicenseExpression.parse("ASL-2"), sortedSetOf("unknownCategory")
            )
            val lic3 = LicenseCategorization(
                SpdxSingleLicenseExpression.parse("BSD"), sortedSetOf("anotherUnknownCategory")
            )

            val exception = shouldThrow<IllegalArgumentException> {
                LicenseClassifications(categories = listOf(cat1, cat2), categorizations = listOf(lic1, lic2, lic3))
            }
            exception.message shouldNotContain lic1.id.toString()
            exception.message shouldContain lic2.id.toString()
            exception.message shouldContain lic3.id.toString()
            exception.message shouldContain "unknownCategory"
            exception.message shouldContain "anotherUnknownCategory"
        }
    }

    "licensesByCategory" should {
        "contain all licenses for a specific category" {
            val cat1 = LicenseCategory("permissive", "Permissive licenses")
            val cat2 = LicenseCategory("non permissive", "Strict licenses")
            val lic1 = LicenseCategorization(
                SpdxSingleLicenseExpression.parse("ASL-1"), sortedSetOf("permissive")
            )
            val lic2 = LicenseCategorization(
                SpdxSingleLicenseExpression.parse("ASL-2"), sortedSetOf("permissive")
            )
            val lic3 = LicenseCategorization(
                SpdxSingleLicenseExpression.parse("GPL"), sortedSetOf("non permissive")
            )
            val licenseClassifications = LicenseClassifications(
                categories = listOf(cat1, cat2),
                categorizations = listOf(lic1, lic2, lic3)
            )

            val permissiveLicenses = licenseClassifications.licensesByCategory[cat1.name]

            permissiveLicenses shouldNotBeNull {
                permissiveLicenses should containExactlyInAnyOrder(lic1.id, lic2.id)
            }
        }

        "return null when querying the licenses for an unknown category" {
            val cat = LicenseCategory("oneAndOnlyCategory")
            val lic = LicenseCategorization(
                SpdxSingleLicenseExpression.parse("LICENSE"), sortedSetOf(cat.name)
            )
            val licenseClassifications = LicenseClassifications(categorizations = listOf(lic), categories = listOf(cat))

            licenseClassifications.licensesByCategory["nonExistingCategory"] should beNull()
        }
    }

    "categoriesByLicense" should {
        "contain all categories for a specific license" {
            val asl1 = SpdxSingleLicenseExpression.parse("ASL-1")
            val asl2 = SpdxSingleLicenseExpression.parse("ASL-2")

            val lic1 = LicenseCategorization(asl1, setOf("hot"))
            val lic2 = LicenseCategorization(asl2, setOf("cold"))

            val licenseClassifications = LicenseClassifications(
                categories = listOf(LicenseCategory("hot"), LicenseCategory("cold")),
                categorizations = listOf(lic1, lic2)
            )

            licenseClassifications[asl2] shouldBe setOf("cold")
        }
    }

    "categoryNames" should {
        "contain the expected category names" {
            val cat1 = LicenseCategory("permissive", "Permissive licenses")
            val cat2 = LicenseCategory("non permissive", "Strict licenses")
            val cat3 = LicenseCategory("other", "Completely different licenses")
            val licenseClassifications = LicenseClassifications(categories = listOf(cat1, cat2, cat3))

            licenseClassifications.categoryNames should containExactlyInAnyOrder(
                "permissive",
                "non permissive",
                "other"
            )
        }
    }
})
