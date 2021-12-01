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

package org.ossreviewtoolkit.scanner

import com.vdurmont.semver4j.Semver

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.ScannerDetails

class ScannerCriteriaTest : WordSpec({
    "ScannerCriteria" should {
        "provide a config matcher that accepts every configuration" {
            ScannerCriteria.ALL_CONFIG_MATCHER("") shouldBe true
            ScannerCriteria.ALL_CONFIG_MATCHER("foo") shouldBe true
            ScannerCriteria.ALL_CONFIG_MATCHER("Supercalifragilisticexpialidocious") shouldBe true
        }

        "provide a config matcher that accepts only exact configuration matches" {
            val orgConfig = "--info --copyright --licenses"
            val matcher = ScannerCriteria.exactConfigMatcher(orgConfig)

            matcher(orgConfig) shouldBe true
            matcher("$orgConfig --more") shouldBe false
        }
    }

    "ScannerCriteria.forDetails()" should {
        "create criteria that only match the passed details by default" {
            val criteria = ScannerCriteria.forDetails(testDetails)
            val nextPatchVersion = Semver(testDetails.version).nextPatch().toString()
            val testDetailsForNextPatchVersion = testDetails.copy(version = nextPatchVersion)

            criteria.matches(testDetails) shouldBe true
            criteria.matches(testDetailsForNextPatchVersion) shouldBe false
        }

        "can create criteria that match details with respect to a version difference" {
            val criteria = ScannerCriteria.forDetails(testDetails, Semver.VersionDiff.PATCH)
            val nextPatchVersion = Semver(testDetails.version).nextPatch().toString()
            val testDetailsForNextPatchVersion = testDetails.copy(version = nextPatchVersion)
            val nextMinorVersion = Semver(testDetails.version).nextMinor().toString()
            val testDetailsForNextMinorVersion = testDetails.copy(version = nextMinorVersion)

            criteria.matches(testDetails) shouldBe true
            criteria.matches(testDetailsForNextPatchVersion) shouldBe true
            criteria.matches(testDetailsForNextMinorVersion) shouldBe false
        }
    }

    "ScannerCriteria.matches()" should {
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
                maxVersion = Semver("4.0.0")
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
private val testDetails = ScannerDetails("ScannerCriteriaTest", "3.2.1-rc2", "--command-line-option")

/** A test instance which should accept the test details. */
private val matchingCriteria = ScannerCriteria(
    regScannerName = testDetails.name,
    minVersion = Semver(testDetails.version),
    maxVersion = Semver(testDetails.version).nextPatch(),
    configMatcher = ScannerCriteria.exactConfigMatcher(testDetails.configuration)
)
