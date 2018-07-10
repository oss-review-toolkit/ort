/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

package com.here.ort.scanner.scanners

import com.here.ort.model.CacheStatistics
import com.here.ort.scanner.Main
import com.here.ort.scanner.ScanResultsCache
import com.here.ort.utils.safeDeleteRecursively

import io.kotlintest.Description
import io.kotlintest.TestResult
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File
import java.time.Instant

class FileCounterTest : StringSpec() {
    private val assetsDir = File("src/funTest/assets")

    private lateinit var outputRootDir: File
    private lateinit var outputDir: File

    override fun beforeTest(description: Description) {
        outputRootDir = createTempDir()
        outputDir = File(outputRootDir, "output")
    }

    override fun afterTest(description: Description, result: TestResult) {
        outputRootDir.safeDeleteRecursively()
        ScanResultsCache.stats = CacheStatistics()
    }

    private val timeRegex = Regex("((download|end|start)_time): \".*\"")

    private fun patchActualResult(result: String) = result
            .replace(timeRegex) { "${it.groupValues[1]}: \"${Instant.EPOCH}\"" }

    init {
        "Gradle project scan results from analyzer result file are correct" {
            val analyzerResultFile = File(assetsDir, "analyzer-result.yml")
            val expectedResult = File(assetsDir, "file-counter-expected-output-for-analyzer-result.yml").readText()

            Main.main(arrayOf(
                    "-d", analyzerResultFile.path,
                    "-o", outputDir.path,
                    "-s", "FileCounter"
            ))

            val result = File(outputDir, "scan-record.yml").readText()

            patchActualResult(result) shouldBe expectedResult
        }
    }
}
