/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.readJsonFile
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.scanner.PathScanner
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.scanner.scanOrtResult
import org.ossreviewtoolkit.utils.spdx.calculatePackageVerificationCode
import org.ossreviewtoolkit.utils.test.createTestTempDir
import org.ossreviewtoolkit.utils.test.patchActualResult
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class ScannerIntegrationFunTest : StringSpec() {
    private val assetsDir = File("src/funTest/assets")

    private lateinit var outputDir: File

    init {
        "Gradle project scan results for a given analyzer result are correct".config(invocations = 3) {
            outputDir = createTestTempDir()

            val analyzerResultFile = assetsDir.resolve("analyzer-result.yml")
            val expectedResult = patchExpectedResult(
                assetsDir.resolve("dummy-expected-output-for-analyzer-result.yml")
            )

            val scanner = DummyScanner("Dummy", ScannerConfiguration(), DownloaderConfiguration())
            val ortResult = scanOrtResult(scanner, analyzerResultFile.readValue(), outputDir)
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
        override val resultFileExt = "json"
        override val expectedVersion = "1.0"
        override val version = expectedVersion
        override val configuration = ""

        override fun command(workingDir: File?) = ""

        override fun scanPathInternal(path: File, resultsFile: File): ScanSummary {
            val startTime = Instant.now()
            resultsFile.writeText(jsonMapper.writeValueAsString("Dummy"))
            val endTime = Instant.now()

            return ScanSummary(
                startTime = startTime,
                endTime = endTime,
                packageVerificationCode = calculatePackageVerificationCode(path),
                licenseFindings = sortedSetOf(),
                copyrightFindings = sortedSetOf(),
                issues = mutableListOf()
            )
        }

        override fun getRawResult(resultsFile: File) = readJsonFile(resultsFile)
    }
}
