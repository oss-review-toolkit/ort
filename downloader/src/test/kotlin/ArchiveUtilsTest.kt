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

package com.here.ort.downloader

import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

class ArchiveUtilsTest : StringSpec() {
    init {
        "Tar GZ archive can be unpacked" {
            val archive = File("src/test/assets/test.tar.gz")
            val outputDirectory = createTempDir()

            try {
                archive.unpack(outputDirectory)

                val fileA = File(outputDirectory, "a")
                val fileB = File(outputDirectory, "dir/b")

                fileA.exists() shouldBe true
                fileA.readText() shouldBe "a\n"
                fileB.exists() shouldBe true
                fileB.readText() shouldBe "b\n"
            } finally {
                outputDirectory.deleteRecursively()
            }
        }

        "Zip archive can be unpacked" {
            val archive = File("src/test/assets/test.zip")
            val outputDirectory = createTempDir()

            try {
                archive.unpack(outputDirectory)

                val fileA = File(outputDirectory, "a")
                val fileB = File(outputDirectory, "dir/b")

                fileA.exists() shouldBe true
                fileA.readText() shouldBe "a\n"
                fileB.exists() shouldBe true
                fileB.readText() shouldBe "b\n"
            } finally {
                outputDirectory.deleteRecursively()
            }
        }
    }
}
