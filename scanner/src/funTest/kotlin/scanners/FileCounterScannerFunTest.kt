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

package org.ossreviewtoolkit.scanner.scanners

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import java.io.File

import kotlin.io.path.createTempDirectory

import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.safeDeleteRecursively
import org.ossreviewtoolkit.utils.test.patchActualResult
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class FileCounterScannerFunTest : StringSpec() {
    private val assetsDir = File("src/funTest/assets")

    private lateinit var outputDir: File

    init {
        "Gradle project scan results for a given analyzer result are correct".config(invocations = 3) {
            outputDir = createTempDirectory("$ORT_NAME-${javaClass.simpleName}").toFile()

            val analyzerResultFile = assetsDir.resolve("analyzer-result.yml")
            val expectedResult = patchExpectedResult(
                assetsDir.resolve("file-counter-expected-output-for-analyzer-result.yml")
            )

            val scanner = FileCounter("FileCounter", ScannerConfiguration(), DownloaderConfiguration())
            val ortResult = scanner.scanOrtResult(analyzerResultFile, outputDir)
            val result = yamlMapper.writeValueAsString(ortResult)

            patchActualResult(result, patchStartAndEndTime = true) shouldBe expectedResult

            outputDir.safeDeleteRecursively()
            ScanResultsStorage.storage.stats.reset()
        }
    }
}
