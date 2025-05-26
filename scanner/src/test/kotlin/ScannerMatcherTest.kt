/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.ScannerDetails

import org.semver4j.Semver

class ScannerMatcherTest : WordSpec({
    "ScannerMatcher.create()" should {
        "obtain default values from the scanner details" {
            val matcher = ScannerMatcher.create(testDetails)

            matcher.regScannerName shouldBe SCANNER_NAME
            matcher.minVersion.version shouldBe SCANNER_VERSION
            matcher.maxVersion.version shouldBe SCANNER_DEFAULT_MAX_VERSION
            matcher.configuration shouldBe SCANNER_CONFIGURATION
        }

        "obtain values from the configuration" {
            val config = object : ScannerMatcherCriteria {
                override val regScannerName = "foo"
                override val minVersion = "1.2.3"
                override val maxVersion = "4.5.6"
                override val configuration = "config"
            }

            val matcher = ScannerMatcher.create(testDetails, config)

            matcher.regScannerName shouldBe "foo"
            matcher.minVersion.version shouldBe "1.2.3"
            matcher.maxVersion.version shouldBe "4.5.6"
            matcher.configuration shouldBe "config"
        }

        "parse versions in a lenient way" {
            val config = object : ScannerMatcherCriteria {
                override val regScannerName = null
                override val minVersion = "1"
                override val maxVersion = "3.7"
                override val configuration = null
            }

            val matcher = ScannerMatcher.create(testDetails, config)

            matcher.minVersion.version shouldBe "1.0.0"
            matcher.maxVersion.version shouldBe "3.7.0"
        }
    }

    "ScannerMatcher.matches()" should {
        "accept matching details" {
            matchingCriteria.matches(testDetails) shouldBe true
        }

        "reject a different name" {
            val matcher = matchingCriteria.copy(regScannerName = testDetails.name + "_other")

            matcher.matches(testDetails) shouldBe false
        }

        "use a regular expression to match the scanner name" {
            val matcher = matchingCriteria.copy(regScannerName = "Sc.*Ma.+Te.t")

            matcher.matches(testDetails) shouldBe true
        }

        "reject a scanner version that is too old" {
            val matcher = matchingCriteria.copy(
                minVersion = matchingCriteria.maxVersion,
                maxVersion = Semver("4.0.0")
            )

            matcher.matches(testDetails) shouldBe false
        }

        "reject a scanner version that is too new" {
            val matcher = matchingCriteria.copy(
                minVersion = Semver("1.0.0"),
                maxVersion = Semver(testDetails.version)
            )

            matcher.matches(testDetails) shouldBe false
        }

        "reject a scanner configuration that does not match" {
            val matcher = matchingCriteria.copy(configuration = "${testDetails.configuration}_other")

            matcher.matches(testDetails) shouldBe false
        }
    }
})

private const val SCANNER_NAME = "ScannerMatcherTest"
private const val SCANNER_VERSION = "3.2.1-rc2"
private const val SCANNER_DEFAULT_MAX_VERSION = "4.0.0"
private const val SCANNER_CONFIGURATION = "--command-line-option"

/** Test details to match against. */
private val testDetails = ScannerDetails(SCANNER_NAME, SCANNER_VERSION, SCANNER_CONFIGURATION)

/** A test instance which should accept the test details. */
private val matchingCriteria = ScannerMatcher(
    regScannerName = testDetails.name,
    minVersion = Semver(testDetails.version),
    maxVersion = Semver(testDetails.version).nextPatch(),
    configuration = testDetails.configuration
)
