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

import io.kotlintest.Spec
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

class ScanPathTest : StringSpec() {
    private val config = ScannerConfiguration()
    private val licenseFilePath = File("../LICENSE")
    private lateinit var outputDir: File

    override fun beforeSpec(spec: Spec) {
        super.beforeSpec(spec)
        outputDir = createTempDir()
    }

    override fun afterSpec(spec: Spec) {
        outputDir.safeDeleteRecursively(force = true)
        super.afterSpec(spec)
    }

    init {
        "Askalono recognizes our own LICENSE" {
            val scanner = Askalono("Askalono", config)
            val resultsFile = outputDir.resolve("${scanner.scannerName}.${scanner.resultFileExt}")

            val result = scanner.scanPath(licenseFilePath, resultsFile)

            resultsFile.isFile shouldBe true
            result.summary.fileCount shouldBe 1
            result.summary.licenses shouldBe sortedSetOf("Apache-2.0")
            result.summary.licenseFindings.all {
                it.location.path == licenseFilePath.invariantSeparatorsPath
            } shouldBe true
        }

        "BoyterLc recognizes our own LICENSE" {
            val scanner = BoyterLc("BoyterLc", config)
            val resultsFile = outputDir.resolve("${scanner.scannerName}.${scanner.resultFileExt}")

            val result = scanner.scanPath(licenseFilePath, resultsFile)

            resultsFile.isFile shouldBe true
            result.summary.fileCount shouldBe 1
            result.summary.licenses shouldBe sortedSetOf("Apache-2.0", "ECL-2.0")
            result.summary.licenseFindings.all {
                it.location.path == licenseFilePath.invariantSeparatorsPath
            } shouldBe true
        }

        "Licensee recognizes our own LICENSE".config(tags = setOf(ExpensiveTag)) {
            val scanner = Licensee("Licensee", config)
            val resultsFile = outputDir.resolve("${scanner.scannerName}.${scanner.resultFileExt}")

            val result = scanner.scanPath(licenseFilePath, resultsFile)

            resultsFile.isFile shouldBe true
            result.summary.fileCount shouldBe 1
            result.summary.licenses shouldBe sortedSetOf("Apache-2.0")
        }

        "ScanCode recognizes our own LICENSE".config(tags = setOf(ExpensiveTag, ScanCodeTag)) {
            val scanner = ScanCode("ScanCode", config)
            val resultsFile = outputDir.resolve("${scanner.scannerName}.${scanner.resultFileExt}")

            val result = scanner.scanPath(licenseFilePath, resultsFile)

            resultsFile.isFile shouldBe true
            result.summary.fileCount shouldBe 1
            result.summary.licenses shouldBe sortedSetOf("Apache-2.0")
        }
    }
}
