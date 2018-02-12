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

import com.here.ort.scanner.scanners.BoyterLc
import com.here.ort.scanner.scanners.Licensee
import com.here.ort.scanner.scanners.ScanCode
import com.here.ort.utils.ExpensiveTag
import com.here.ort.utils.ScanCodeTag
import com.here.ort.utils.safeDeleteRecursively

import io.kotlintest.matchers.shouldBe
import io.kotlintest.TestCaseContext
import io.kotlintest.specs.StringSpec

import java.io.File

class ScanPathTest : StringSpec() {
    private lateinit var outputDir: File

    // Required to make lateinit of outputDir work.
    override val oneInstancePerTest = false

    override fun interceptTestCase(context: TestCaseContext, test: () -> Unit) {
        outputDir = createTempDir()
        try {
            super.interceptTestCase(context, test)
        } finally {
            outputDir.safeDeleteRecursively()
        }
    }

    init {
        "BoyterLc recognizes our own LICENSE" {
            val result = BoyterLc.scan(File("../LICENSE"), outputDir)
            result.licenses shouldBe setOf("Apache-2.0", "ECL-2.0")
        }.config(tags = setOf(ExpensiveTag))

        "Licensee recognizes our own LICENSE" {
            val result = Licensee.scan(File("../LICENSE"), outputDir)
            result.licenses shouldBe setOf("Apache License 2.0")
        }.config(tags = setOf(ExpensiveTag))

        "ScanCode recognizes our own LICENSE" {
            val result = ScanCode.scan(File("../LICENSE"), outputDir)
            result.licenses shouldBe setOf("Apache-2.0")
        }.config(tags = setOf(ExpensiveTag, ScanCodeTag))
    }
}
