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
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.model.LicenseResultOption
import org.ossreviewtoolkit.model.PackageResultOption
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.ScannerOption
import org.ossreviewtoolkit.model.ScannerOptions
import org.ossreviewtoolkit.model.SubOptions
import org.ossreviewtoolkit.model.UrlResultOption
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.jsonMapper

class LocalScannerTest : WordSpec({
    "getScannerCriteria()" should {
        "obtain default values from the scanner" {
            val scanner = createScanner(createConfig(emptyMap()))

            val criteria = scanner.getScannerCriteria()

            criteria.regScannerName shouldBe SCANNER_NAME
            criteria.minVersion.originalValue shouldBe SCANNER_VERSION
            criteria.maxVersion shouldBe Semver(SCANNER_VERSION).nextMinor()
        }

        "obtain values from the configuration" {
            val config = mapOf(
                LocalScanner.PROP_CRITERIA_NAME to "foo",
                LocalScanner.PROP_CRITERIA_MIN_VERSION to "1.2.3",
                LocalScanner.PROP_CRITERIA_MAX_VERSION to "4.5.6"
            )
            val scanner = createScanner(createConfig(config))

            val criteria = scanner.getScannerCriteria()

            criteria.regScannerName shouldBe "foo"
            criteria.minVersion.originalValue shouldBe "1.2.3"
            criteria.maxVersion.originalValue shouldBe "4.5.6"
        }

        "parse versions in a lenient way" {
            val config = mapOf(
                LocalScanner.PROP_CRITERIA_MIN_VERSION to "1",
                LocalScanner.PROP_CRITERIA_MAX_VERSION to "3.7"
            )
            val scanner = createScanner(createConfig(config))

            val criteria = scanner.getScannerCriteria()

            criteria.minVersion.originalValue shouldBe "1.0.0"
            criteria.maxVersion.originalValue shouldBe "3.7.0"
        }

        "use an exact configuration matcher if there are no options" {
            val scanner = createScanner(createConfig(emptyMap()))
            val details = ScannerDetails(SCANNER_NAME, SCANNER_VERSION, scanner.configuration)

            val criteria = scanner.getScannerCriteria()

            criteria.configMatcher(details) shouldBe true
            criteria.configMatcher(details.copy(configuration = details.configuration + "_other")) shouldBe false
        }

        "match on scanner options if available" {
            val scanner = createScanner(createConfig(emptyMap()), withOptions = true)
            val optionSet = createScannerOptions()
            val superSet = mutableSetOf<ScannerOption>()
            superSet.addAll(optionSet)
            superSet.add(UrlResultOption(SubOptions(jsonMapper.createObjectNode())))
            val superOptions = ScannerOptions(superSet)
            val subSet = optionSet.drop(1).toSet()
            val subOptions = ScannerOptions(subSet)

            val criteria = scanner.getScannerCriteria()

            val superDetails = ScannerDetails(SCANNER_NAME, SCANNER_VERSION, "aConfig", superOptions)
            criteria.configMatcher(superDetails) shouldBe true

            val subDetails = ScannerDetails(SCANNER_NAME, SCANNER_VERSION, "aConfig", subOptions)
            criteria.configMatcher(subDetails) shouldBe false
        }

        "match scanner options in strict mode per default" {
            val licenseOptionExtended = LicenseResultOption(
                SubOptions.create {
                    putThresholdOption(key = "license-score", value = 77.0)
                }
            )
            val packageOption = PackageResultOption(SubOptions(jsonMapper.createObjectNode()))
            val resultOptions = ScannerOptions(setOf(packageOption, licenseOptionExtended))
            val details = ScannerDetails(SCANNER_NAME, SCANNER_VERSION, "?", resultOptions)
            val scanner = createScanner(createConfig(emptyMap()), withOptions = true)

            val criteria = scanner.getScannerCriteria()

            criteria.configMatcher(details) shouldBe false
        }

        "allow configuring non-strict options for matches" {
            val licenseOptionExtended = LicenseResultOption(
                SubOptions.create {
                    putThresholdOption(key = "license-score", value = 77.0)
                }
            )
            val packageOption = PackageResultOption(
                SubOptions.create { putStringOption(key = "consolidated", value = "true") }
            )
            val resultOptions = ScannerOptions(setOf(packageOption, licenseOptionExtended))
            val details = ScannerDetails(SCANNER_NAME, SCANNER_VERSION, "?", resultOptions)
            val config = mapOf(
                LocalScanner.PROP_CRITERIA_NON_STRICT_OPTIONS to "LicenseResultOption,PackageResultOption"
            )
            val scanner = createScanner(createConfig(config), withOptions = true)

            val criteria = scanner.getScannerCriteria()

            criteria.configMatcher(details) shouldBe true
        }
    }

    "LocalScanner.getDetails" should {
        "contain correct properties" {
            val scanner = createScanner(createConfig(emptyMap()), withOptions = true)

            val details = scanner.details

            details.name shouldBe SCANNER_NAME
            details.version shouldBe SCANNER_VERSION
            details.configuration shouldBe scanner.configuration
            details.options.shouldNotBeNull()
            details.options shouldBe scanner.getScannerOptions()
        }
    }
})

private const val SCANNER_NAME = "TestScanner"
private const val SCANNER_VERSION = "3.2.1.final"

/**
 * Creates a [ScannerConfiguration] with the given properties for the test scanner.
 */
private fun createConfig(properties: Map<String, String>): ScannerConfiguration {
    val options = mapOf(SCANNER_NAME to properties)
    return ScannerConfiguration(options = options)
}

/**
 * Create a test instance of [LocalScanner] that uses the given [config] and may return scanner options depending on
 * the [withOptions] flag.
 */
private fun createScanner(config: ScannerConfiguration, withOptions: Boolean = false): LocalScanner =
    object : LocalScanner(SCANNER_NAME, config) {
        override val configuration = "someConfig"

        override val resultFileExt: String
            get() = "xml"

        override val expectedVersion: String
            get() = SCANNER_VERSION

        override fun scanPathInternal(path: File, resultsFile: File) = throw NotImplementedError()

        override fun getRawResult(resultsFile: File) = throw NotImplementedError()

        override val version: String = SCANNER_VERSION

        override fun getScannerOptions(): ScannerOptions? {
            if (!withOptions) {
                return super.getScannerOptions()
            }

            return ScannerOptions(
                createScannerOptions()
            )
        }

        override fun command(workingDir: File?) = throw NotImplementedError()
    }

/**
 * Return a set with scanner options used by the test scanner instance.
 */
private fun createScannerOptions(): MutableSet<ScannerOption> {
    return mutableSetOf(
        PackageResultOption(SubOptions(jsonMapper.createObjectNode())),
        LicenseResultOption(SubOptions(jsonMapper.createObjectNode()))
    )
}
