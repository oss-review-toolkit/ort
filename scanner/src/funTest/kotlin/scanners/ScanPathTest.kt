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

package com.here.ort.scanner.scanners

import com.here.ort.model.config.ScannerConfiguration
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.test.ExpensiveTag
import com.here.ort.utils.test.ScanCodeTag

import io.kotlintest.TestCase
import io.kotlintest.TestResult
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

class ScanPathTest : StringSpec() {
    private val config = ScannerConfiguration()
    private lateinit var outputDir: File

    override fun beforeTest(testCase: TestCase) {
        outputDir = createTempDir()
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        outputDir.safeDeleteRecursively(force = true)
    }

    init {
        "Askalono recognizes our own LICENSE".config(tags = setOf(ExpensiveTag)) {
            val result = Askalono(config).scanPath(File("../LICENSE"), outputDir)
            result.summary.fileCount shouldBe 1
            result.summary.licenses shouldBe sortedSetOf("Apache-2.0")
        }

        "BoyterLc recognizes our own LICENSE".config(tags = setOf(ExpensiveTag)) {
            val result = BoyterLc(config).scanPath(File("../LICENSE"), outputDir)
            result.summary.fileCount shouldBe 1
            result.summary.licenses shouldBe sortedSetOf("Apache-2.0", "ECL-2.0")
        }

        "Licensee recognizes our own LICENSE".config(tags = setOf(ExpensiveTag)) {
            val result = Licensee(config).scanPath(File("../LICENSE"), outputDir)
            result.summary.fileCount shouldBe 1
            result.summary.licenses shouldBe sortedSetOf("Apache-2.0")
        }

        "ScanCode recognizes our own LICENSE".config(tags = setOf(ExpensiveTag, ScanCodeTag)) {
            val result = ScanCode(config).scanPath(File("../LICENSE"), outputDir)
            result.summary.fileCount shouldBe 1
            result.summary.licenses shouldBe sortedSetOf("Apache-2.0")
        }
    }
}
