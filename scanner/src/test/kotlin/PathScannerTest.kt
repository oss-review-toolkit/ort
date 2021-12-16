/*
 * Copyright (C) 2020-2021 Bosch.IO GmbH
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

import java.io.File

import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.config.ScannerOptions

class PathScannerTest : WordSpec({
    "getScannerCriteria()" should {
        "obtain default values from the scanner" {
            val scanner = createScanner(createScannerConfig(emptyMap()), DownloaderConfiguration())

            val criteria = scanner.getScannerCriteria()

            criteria.regScannerName shouldBe SCANNER_NAME
            criteria.minVersion.originalValue shouldBe SCANNER_VERSION
            criteria.maxVersion shouldBe Semver(SCANNER_VERSION).nextMinor()
        }

        "obtain values from the configuration" {
            val config = mapOf(
                PathScanner.PROP_CRITERIA_NAME to "foo",
                PathScanner.PROP_CRITERIA_MIN_VERSION to "1.2.3",
                PathScanner.PROP_CRITERIA_MAX_VERSION to "4.5.6"
            )
            val scanner = createScanner(createScannerConfig(config), DownloaderConfiguration())

            val criteria = scanner.getScannerCriteria()

            criteria.regScannerName shouldBe "foo"
            criteria.minVersion.originalValue shouldBe "1.2.3"
            criteria.maxVersion.originalValue shouldBe "4.5.6"
        }

        "parse versions in a lenient way" {
            val config = mapOf(
                PathScanner.PROP_CRITERIA_MIN_VERSION to "1",
                PathScanner.PROP_CRITERIA_MAX_VERSION to "3.7"
            )
            val scanner = createScanner(createScannerConfig(config), DownloaderConfiguration())

            val criteria = scanner.getScannerCriteria()

            criteria.minVersion.originalValue shouldBe "1.0.0"
            criteria.maxVersion.originalValue shouldBe "3.7.0"
        }

        "use an exact configuration matcher" {
            val scanner = createScanner(createScannerConfig(emptyMap()), DownloaderConfiguration())

            val criteria = scanner.getScannerCriteria()

            criteria.configMatcher(scanner.configuration) shouldBe true
            criteria.configMatcher(scanner.configuration + "_other") shouldBe false
        }
    }
})

private const val SCANNER_NAME = "TestScanner"
private const val SCANNER_VERSION = "3.2.1.final"

/**
 * Creates a [ScannerConfiguration] with the given properties for the test scanner.
 */
private fun createScannerConfig(properties: ScannerOptions): ScannerConfiguration {
    val options = mapOf(SCANNER_NAME to properties)
    return ScannerConfiguration(options = options)
}

/**
 * Create a test instance of [PathScanner].
 */
private fun createScanner(
    scannerConfig: ScannerConfiguration,
    downloaderConfig: DownloaderConfiguration
): PathScanner =
    object : PathScanner(SCANNER_NAME, scannerConfig, downloaderConfig) {
        override val version = SCANNER_VERSION
        override val configuration = "someConfig"

        override fun scanPathInternal(path: File) = throw NotImplementedError()
    }
