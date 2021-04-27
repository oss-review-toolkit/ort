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

package org.ossreviewtoolkit.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.utils.test.createTestTempDir

class ArchiveUtilsTest : StringSpec() {
    private lateinit var outputDir: File

    override fun beforeTest(testCase: TestCase) {
        outputDir = createTestTempDir()
    }

    init {
        "Tar GZ archive can be unpacked" {
            val archive = File("src/test/assets/test.tar.gz")

            archive.unpack(outputDir)

            val fileA = outputDir.resolve("a")
            val fileB = outputDir.resolve("dir/b")

            fileA.exists() shouldBe true
            fileA.readText() shouldBe "a\n"
            fileB.exists() shouldBe true
            fileB.readText() shouldBe "b\n"
        }

        "Tar bzip2 archive can be unpacked" {
            val archive = File("src/test/assets/test.tar.bz2")

            archive.unpack(outputDir)

            val fileA = outputDir.resolve("a")
            val fileB = outputDir.resolve("dir/b")

            fileA.exists() shouldBe true
            fileA.readText() shouldBe "a\n"
            fileB.exists() shouldBe true
            fileB.readText() shouldBe "b\n"
        }

        "Zip archive can be unpacked" {
            val archive = File("src/test/assets/test.zip")

            archive.unpack(outputDir)

            val fileA = outputDir.resolve("a")
            val fileB = outputDir.resolve("dir/b")

            fileA.exists() shouldBe true
            fileA.readText() shouldBe "a\n"
            fileB.exists() shouldBe true
            fileB.readText() shouldBe "b\n"
        }
    }
}
