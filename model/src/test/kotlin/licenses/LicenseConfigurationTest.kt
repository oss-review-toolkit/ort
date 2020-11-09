/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model.licenses

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import java.lang.IllegalStateException
import java.util.Collections.emptySortedSet

import org.ossreviewtoolkit.spdx.SpdxSingleLicenseExpression

class LicenseConfigurationTest : WordSpec({
    "LicenseConfiguration.init" should {
        "detect duplicate category names" {
            val cat1 = LicenseCategory("Category 1")
            val cat2 = LicenseCategory("Category 2", "Another category")
            val cat3 = LicenseCategory("Category 1", "Duplicate; should cause a failure")

            shouldThrow<IllegalArgumentException> {
                LicenseConfiguration(categories = listOf(cat1, cat2, cat3))
            }
        }

        "detect duplicate license IDs" {
            val lic1 = License(
                SpdxSingleLicenseExpression.parse("ASL-1"), emptySortedSet()
            )
            val lic2 = License(
                SpdxSingleLicenseExpression.parse("ASL-2"), emptySortedSet()
            )
            val lic3 = License(
                SpdxSingleLicenseExpression.parse("ASL-1"), sortedSetOf("permissive")
            )

            shouldThrow<IllegalArgumentException> {
                LicenseConfiguration(categorizations = listOf(lic1, lic2, lic3))
            }
        }

        "detect licenses referencing non-existing categories" {
            val cat1 = LicenseCategory("Category 1")
            val cat2 = LicenseCategory("Category 2")
            val lic1 = License(
                SpdxSingleLicenseExpression.parse("ASL-1"), sortedSetOf(cat1.name)
            )
            val lic2 = License(
                SpdxSingleLicenseExpression.parse("ASL-2"), sortedSetOf("unknownCategory")
            )
            val lic3 = License(
                SpdxSingleLicenseExpression.parse("BSD"), sortedSetOf("anotherUnknownCategory")
            )

            val exception = shouldThrow<IllegalArgumentException> {
                LicenseConfiguration(categories = listOf(cat1, cat2), categorizations = listOf(lic1, lic2, lic3))
            }
            exception.message shouldContain lic2.id.toString()
            exception.message shouldContain lic3.id.toString()
            exception.message shouldContain "unknownCategory"
            exception.message shouldContain "anotherUnknownCategory"
        }
    }

    "LicenseConfiguration.getLicensesForCategory" should {
        "fetch all licenses for a specific category" {
            val cat1 = LicenseCategory("permissive", "Permissive licenses")
            val cat2 = LicenseCategory("non permissive", "Strict licenses")
            val lic1 = License(
                SpdxSingleLicenseExpression.parse("ASL-1"), sortedSetOf("permissive")
            )
            val lic2 = License(
                SpdxSingleLicenseExpression.parse("ASL-2"), sortedSetOf("permissive")
            )
            val lic3 = License(
                SpdxSingleLicenseExpression.parse("GPL"), sortedSetOf("non permissive")
            )
            val licenseConfiguration = LicenseConfiguration(
                categories = listOf(cat1, cat2),
                categorizations = listOf(lic1, lic2, lic3)
            )

            val permissiveLicenses = licenseConfiguration.getLicensesForCategory(cat1.name)

            permissiveLicenses should containExactlyInAnyOrder(lic1, lic2)
        }

        "throw an exception when querying the licenses for an unknown category" {
            val cat = LicenseCategory("oneAndOnlyCategory")
            val lic = License(
                SpdxSingleLicenseExpression.parse("LICENSE"), sortedSetOf(cat.name)
            )
            val licenseConfiguration = LicenseConfiguration(categorizations = listOf(lic), categories = listOf(cat))

            shouldThrow<IllegalStateException> {
                licenseConfiguration.getLicensesForCategory("nonExistingCategory")
            }
        }
    }

    "LicenseConfiguration" should {
        "allow querying a license by ID" {
            val lic1 = License(
                SpdxSingleLicenseExpression.parse("ASL-1"), emptySortedSet()
            )
            val lic2 = License(
                SpdxSingleLicenseExpression.parse("ASL-2"), emptySortedSet()
            )
            val licenseConfiguration = LicenseConfiguration(categorizations = listOf(lic1, lic2))

            licenseConfiguration[SpdxSingleLicenseExpression.parse("ASL-2")] shouldBe lic2
        }
    }

    "LicenseConfiguration.categoryNames" should {
        "contain the expected licenses" {
            val cat1 = LicenseCategory("permissive", "Permissive licenses")
            val cat2 = LicenseCategory("non permissive", "Strict licenses")
            val cat3 = LicenseCategory("other", "Completely different licenses")
            val licenseConfiguration = LicenseConfiguration(categories = listOf(cat1, cat2, cat3))

            licenseConfiguration.categoryNames should containExactly("non permissive", "other", "permissive")
        }
    }
})
