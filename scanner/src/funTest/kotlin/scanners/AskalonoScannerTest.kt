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

import io.kotlintest.shouldBe

class AskalonoScannerTest : AbstractScannerTest() {
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
    }
}
