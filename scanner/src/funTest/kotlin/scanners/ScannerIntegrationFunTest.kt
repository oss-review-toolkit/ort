/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.scanner.scanners

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.scanner.PathScanner
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.scanner.scanOrtResult
import org.ossreviewtoolkit.utils.spdx.calculatePackageVerificationCode
import org.ossreviewtoolkit.utils.test.patchActualResult
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class ScannerIntegrationFunTest : StringSpec() {
    private val assetsDir = File("src/funTest/assets")

    init {
        "Gradle project scan results for a given analyzer result are correct".config(invocations = 3) {
            val analyzerResultFile = assetsDir.resolve("analyzer-result.yml")
            val expectedResult = patchExpectedResult(
                assetsDir.resolve("dummy-expected-output-for-analyzer-result.yml")
            )

            val scanner = DummyScanner("Dummy", ScannerConfiguration(), DownloaderConfiguration())
            val ortResult = scanOrtResult(scanner, analyzerResultFile.readValue())
            val result = yamlMapper.writeValueAsString(ortResult)

            patchActualResult(result, patchStartAndEndTime = true) shouldBe expectedResult

            ScanResultsStorage.storage.stats.reset()
        }
    }

    class DummyScanner(
        name: String,
        scannerConfig: ScannerConfiguration,
        downloaderConfig: DownloaderConfiguration
    ) : PathScanner(name, scannerConfig, downloaderConfig) {
        override val version = "1.0"
        override val configuration = ""

        override fun scanPathInternal(path: File): ScanSummary {
            val time = Instant.now()

            return ScanSummary(
                startTime = time,
                endTime = time,
                packageVerificationCode = calculatePackageVerificationCode(path),
                licenseFindings = sortedSetOf(),
                copyrightFindings = sortedSetOf(),
                issues = mutableListOf()
            )
        }
    }
}
