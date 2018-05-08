/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

import com.here.ort.scanner.Main
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.searchUpwardsForSubdirectory

import io.kotlintest.TestCaseContext
import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

class FileCounterTest : StringSpec() {
    private val rootDir = File(".").searchUpwardsForSubdirectory(".git")!!
    private val assetsDir = File(rootDir, "scanner/src/funTest/assets")

    private lateinit var outputRootDir: File
    private lateinit var outputDir: File

    // Required to make lateinit of outputDir work.
    override val oneInstancePerTest = false

    override fun interceptTestCase(context: TestCaseContext, test: () -> Unit) {
        outputRootDir = createTempDir()
        outputDir = File(outputRootDir, "output")
        try {
            super.interceptTestCase(context, test)
        } finally {
            outputRootDir.safeDeleteRecursively()
        }
    }

    private val timeRegex = Regex("(download|end|start)Time: \".*\"")
    private val urlRegex = Regex("url: \".*%3CREPLACE_URL%3E\"")

    private fun patchResult(result: String) = result
            .replace(timeRegex) { "${it.groupValues[1]}Time: \"<TIMESTAMP>\"" }
            .replace(urlRegex, "url: \"<REPLACE_URL>\"")

    init {
        "Gradle project scan results are correct" {
            val analyzerResultFile = File(assetsDir, "project-analyzer-result.yml")
            val expectedResult = File(assetsDir, "file-counter-expected-result.yml").readText()

            Main.main(arrayOf(
                    "-d", analyzerResultFile.path,
                    "-o", outputDir.path,
                    "-s", "FileCounter"
            ))

            val result = File(outputDir, "scan-record.yml").readText()

            patchResult(result) shouldBe expectedResult
        }
    }
}
