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

package org.ossreviewtoolkit.scanner

import com.vdurmont.semver4j.Semver

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.CopyrightResultOption
import org.ossreviewtoolkit.model.EmailResultOption
import org.ossreviewtoolkit.model.LicenseResultOption
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.ScannerOptions
import org.ossreviewtoolkit.model.SubOptions
import org.ossreviewtoolkit.model.jsonMapper

class ScannerCriteriaTest : WordSpec({
    "ScannerCriteria" should {
        "provide a config matcher that accepts every configuration" {
            ScannerCriteria.ALL_CONFIG_MATCHER(testDetails.copy(configuration = "")) shouldBe true
            ScannerCriteria.ALL_CONFIG_MATCHER(testDetails.copy(configuration = "foo")) shouldBe true
            ScannerCriteria.ALL_CONFIG_MATCHER(
                testDetails.copy(configuration = "Supercalifragilisticexpialidocious")
            ) shouldBe true
        }

        "provide a config matcher that accepts only exact configuration matches" {
            val orgConfig = "--info --copyright --licenses"
            val matcher = ScannerCriteria.exactConfigMatcher(orgConfig)

            matcher(testDetails.copy(configuration = orgConfig)) shouldBe true
            matcher(testDetails) shouldBe false
        }
    }

    "ScannerCriteria.compatibleConfigMatcher" should {
        "fallback to an exact configuration match if no options are available" {
            val orgConfig = "--license --copy-right --processes 4"
            val licenseOption = LicenseResultOption(SubOptions(jsonMapper.createObjectNode()))
            val copyrightOption = CopyrightResultOption(SubOptions(jsonMapper.createObjectNode()))
            val options = ScannerOptions(setOf(licenseOption, copyrightOption))
            val matcher = ScannerCriteria.compatibleConfigMatcher(orgConfig, options, emptySet())

            matcher(testDetails.copy(configuration = orgConfig)) shouldBe true
            matcher(testDetails) shouldBe false
        }

        "check the compatibility of scanner options if available" {
            val licenseOption = LicenseResultOption(SubOptions(jsonMapper.createObjectNode()))
            val emailOption1 = EmailResultOption(
                SubOptions.create { putThresholdOption(50.0) }
            )
            val emailOption2 = EmailResultOption(
                SubOptions.create { putThresholdOption(100.0) }
            )
            val copyrightOption = CopyrightResultOption(SubOptions(jsonMapper.createObjectNode()))
            val orgOptions = ScannerOptions(setOf(licenseOption, emailOption1))
            val compatibleOptions = ScannerOptions(setOf(licenseOption, emailOption2, copyrightOption))
            val incompatibleOptions = ScannerOptions(setOf(copyrightOption, licenseOption))

            val matcher = ScannerCriteria.compatibleConfigMatcher("irrelevant config", orgOptions, emptySet())

            matcher(testDetails.copy(options = compatibleOptions)) shouldBe true
            matcher(testDetails.copy(options = incompatibleOptions)) shouldBe false
        }

        "do strict compatibility checks by default" {
            val licenseOption1 = LicenseResultOption(SubOptions(jsonMapper.createObjectNode()))
            val licenseOption2 = LicenseResultOption(
                SubOptions.create { putThresholdOption(key = "license-score", value = 33.0) }
            )
            val options1 = ScannerOptions(setOf(licenseOption1))
            val options2 = ScannerOptions(setOf(licenseOption2))

            val matcher = ScannerCriteria.compatibleConfigMatcher("?", options1, emptySet())

            matcher(testDetails.copy(options = options2)) shouldBe false
        }

        "take the set of non-strict options into account" {
            val licenseOption1 = LicenseResultOption(SubOptions(jsonMapper.createObjectNode()))
            val licenseOption2 = LicenseResultOption(
                SubOptions.create { putThresholdOption(key = "license-score", value = 33.0) }
            )
            val options1 = ScannerOptions(setOf(licenseOption1))
            val options2 = ScannerOptions(setOf(licenseOption2))

            val matcher = ScannerCriteria.compatibleConfigMatcher("?", options1,
                setOf(LicenseResultOption::class.java.simpleName))

            matcher(testDetails.copy(options = options2)) shouldBe true
        }
    }

    "ScannerCriteria.isCompatible()" should {
        "accept matching details" {
            matchingCriteria.matches(testDetails) shouldBe true
        }

        "detect a different name" {
            val criteria = matchingCriteria.copy(regScannerName = testDetails.name + "_other")

            criteria.matches(testDetails) shouldBe false
        }

        "use a regular expression to match the scanner name" {
            val criteria = matchingCriteria.copy(regScannerName = "Sc.*Cr.+Te.t")

            criteria.matches(testDetails) shouldBe true
        }

        "detect a scanner version that is too old" {
            val criteria = matchingCriteria.copy(
                minVersion = matchingCriteria.maxVersion,
                maxVersion = Semver("2.0.0")
            )

            criteria.matches(testDetails) shouldBe false
        }

        "detect a scanner version that is too new" {
            val criteria = matchingCriteria.copy(
                minVersion = Semver("1.0.0"),
                maxVersion = Semver(testDetails.version)
            )

            criteria.matches(testDetails) shouldBe false
        }

        "detect a difference reported by the config matcher" {
            val criteria = matchingCriteria.copy(
                configMatcher = ScannerCriteria.exactConfigMatcher(testDetails.configuration + "_other")
            )

            criteria.matches(testDetails) shouldBe false
        }
    }
})

/** Test details to match against. */
private val testDetails = ScannerDetails("ScannerCriteriaTest", "1.2.3.beta-47", "a b c")

/** A test instance which should accept the test details. */
private val matchingCriteria = ScannerCriteria(
    regScannerName = testDetails.name,
    minVersion = Semver(testDetails.version),
    maxVersion = Semver(testDetails.version).nextPatch(),
    configMatcher = ScannerCriteria.exactConfigMatcher(testDetails.configuration)
)
