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

package com.here.ort.scanner

import com.here.ort.scanner.scanners.ScanCode

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.File

class ScanCodeTest : WordSpec({
    "mapTimeoutErrors()" should {
        "return true for scan results with only timeout errors" {
            val resultFileName = "/esprima-2.7.3_scancode-2.2.1.post277.4d68f9377.json"
            val resultFile = File(javaClass.getResource(resultFileName).toURI())
            val result = ScanCode.getPlainResult(resultFile)
            ScanCode.mapTimeoutErrors(result) shouldBe true
            result.errors.joinToString("\n") shouldBe sortedSetOf(
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/angular-1.2.5.json'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/angular-1.2.5.tokens'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/jquery-1.9.1.json'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/jquery-1.9.1.tokens'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/jquery.mobile-1.4.2.json'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/jquery.mobile-1.4.2.tokens'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/mootools-1.4.5.json'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/mootools-1.4.5.tokens'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/underscore-1.5.2.json'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/underscore-1.5.2.tokens'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/yui-3.12.0.json'.",
                    "ERROR: Timeout after 300 seconds while scanning file " +
                            "'test/3rdparty/syntax/yui-3.12.0.tokens'."
            ).joinToString("\n")
        }

        "return false for scan results without errors" {
            val resultFileName = "/esprima-2.7.3_scancode-2.2.1.json"
            val resultFile = File(javaClass.getResource(resultFileName).toURI())
            val result = ScanCode.getResult(resultFile)
            ScanCode.mapTimeoutErrors(result) shouldBe false
        }
    }

    "mapUnknownErrors()" should {
        "return true for scan results with only memory errors" {
            val resultFileName = "/very-long-json-lines_scancode-2.2.1.post277.4d68f9377.json"
            val resultFile = File(javaClass.getResource(resultFileName).toURI())
            val result = ScanCode.getPlainResult(resultFile)
            ScanCode.mapUnknownErrors(result) shouldBe true
            result.errors.joinToString("\n") shouldBe sortedSetOf(
                    "ERROR: MemoryError while scanning file 'data.json'."
            ).joinToString("\n")
        }

        "return false for scan results with other unknown errors" {
            val resultFileName = "/kotlin-annotation-processing-gradle-1.2.21_scancode.json"
            val resultFile = File(javaClass.getResource(resultFileName).toURI())
            val result = ScanCode.getPlainResult(resultFile)
            ScanCode.mapUnknownErrors(result) shouldBe false
            result.errors.joinToString("\n") shouldBe sortedSetOf(
                    "ERROR: AttributeError while scanning file 'compiler/testData/cli/js-dce/withSourceMap.js.map' " +
                            "('NoneType' object has no attribute 'splitlines')."
            ).joinToString("\n")
        }

        "return false for scan results without errors" {
            val resultFileName = "/esprima-2.7.3_scancode-2.2.1.json"
            val resultFile = File(javaClass.getResource(resultFileName).toURI())
            val result = ScanCode.getResult(resultFile)
            ScanCode.mapUnknownErrors(result) shouldBe false
        }
    }
})
