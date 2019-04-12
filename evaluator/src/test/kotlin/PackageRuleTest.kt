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

package com.here.ort.evaluator

import com.here.ort.model.LicenseSource

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

class PackageRuleTest : WordSpec() {
    private val ruleSet = RuleSet(ortResult)

    init {
        "hasLicense()" should {
            "return true if the package has concluded licenses" {
                val rule = PackageRule(ruleSet, "test", packageWithOnlyConcludedLicense, emptyList(), emptyList())
                val matcher = rule.hasLicense()

                matcher.matches() shouldBe true
            }

            "return true if the package has declared licenses" {
                val rule = PackageRule(ruleSet, "test", packageWithOnlyDeclaredLicense, emptyList(), emptyList())
                val matcher = rule.hasLicense()

                matcher.matches() shouldBe true
            }

            "return true if the package has detected licenses" {
                val rule = PackageRule(ruleSet, "test", packageWithoutLicense, emptyList(), licenseFindings)
                val matcher = rule.hasLicense()

                matcher.matches() shouldBe true
            }

            "return false if the package has no license" {
                val rule = PackageRule(ruleSet, "test", packageWithoutLicense, emptyList(), emptyList())
                val matcher = rule.hasLicense()

                matcher.matches() shouldBe false
            }
        }

        "isExcluded()" should {
            "return true if the package is excluded" {
                val rule = PackageRule(ruleSet, "test", packageExcluded, emptyList(), emptyList())
                val matcher = rule.isExcluded()

                matcher.matches() shouldBe true
            }

            "return false if the package is not excluded" {
                val rule = PackageRule(ruleSet, "test", packageWithoutLicense, emptyList(), emptyList())
                val matcher = rule.isExcluded()

                matcher.matches() shouldBe false
            }
        }

        "isFromOrg()" should {
            "return true if the package is from org" {
                val rule = PackageRule(ruleSet, "test", packageWithoutLicense, emptyList(), emptyList())
                val matcher = rule.isFromOrg("here")

                matcher.matches() shouldBe true
            }

            "return false if the package is not from org" {
                val rule = PackageRule(ruleSet, "test", packageWithoutLicense, emptyList(), emptyList())
                val matcher = rule.isFromOrg("unknown")

                matcher.matches() shouldBe false
            }
        }

        "isType()" should {
            "return true if the package has the provided type" {
                val rule = PackageRule(ruleSet, "test", packageWithoutLicense, emptyList(), emptyList())
                val matcher = rule.isType("Maven")

                matcher.matches() shouldBe true
            }

            "return false if the package has not the provided type" {
                val rule = PackageRule(ruleSet, "test", packageWithoutLicense, emptyList(), emptyList())
                val matcher = rule.isType("Gradle")

                matcher.matches() shouldBe false
            }
        }

        "isSpdxLicense()" should {
            "return true if the license is an SPDX license" {
                PackageRule(ruleSet, "test", packageWithoutLicense, emptyList(), emptyList()).apply {
                    val licenseRule = LicenseRule("test", "Apache-2.0", LicenseSource.DECLARED, emptyMap())
                    val matcher = licenseRule.isSpdxLicense()

                    matcher.matches() shouldBe true
                }
            }

            "return false if the license is not an SPDX license" {
                PackageRule(ruleSet, "test", packageWithoutLicense, emptyList(), emptyList()).apply {
                    val licenseRule = LicenseRule("test", "invalid", LicenseSource.DECLARED, emptyMap())
                    val matcher = licenseRule.isSpdxLicense()

                    matcher.matches() shouldBe false
                }
            }
        }
    }
}
