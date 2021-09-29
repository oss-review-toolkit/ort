/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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

import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.kotest.inspectors.forAll
import io.kotest.matchers.file.aFile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import java.io.File

import org.ossreviewtoolkit.utils.test.createTestTempDir

class ArchiveUtilsTest : WordSpec() {
    private lateinit var outputDir: File

    override fun beforeTest(testCase: TestCase) {
        outputDir = createTestTempDir()
    }

    init {
        "Tar archives" should {
            "unpack" {
                val archive = File("src/test/assets/test.tar")

                archive.unpack(outputDir)

                val fileA = outputDir.resolve("a")
                val fileB = outputDir.resolve("dir/b")

                fileA shouldBe aFile()
                fileA.readText() shouldBe "a\n"
                fileB shouldBe aFile()
                fileB.readText() shouldBe "b\n"
            }
        }

        "Tar GZ archives" should {
            "unpack" {
                val archive = File("src/test/assets/test.tar.gz")

                archive.unpack(outputDir)

                val fileA = outputDir.resolve("a")
                val fileB = outputDir.resolve("dir/b")

                fileA shouldBe aFile()
                fileA.readText() shouldBe "a\n"
                fileB shouldBe aFile()
                fileB.readText() shouldBe "b\n"
            }
        }

        "Tar bzip2 archives" should {
            "unpack" {
                val archive = File("src/test/assets/test.tar.bz2")

                archive.unpack(outputDir)

                val fileA = outputDir.resolve("a")
                val fileB = outputDir.resolve("dir/b")

                fileA shouldBe aFile()
                fileA.readText() shouldBe "a\n"
                fileB shouldBe aFile()
                fileB.readText() shouldBe "b\n"
            }
        }

        "Tar xz archives" should {
            "unpack" {
                val archive = File("src/test/assets/test.tar.xz")

                archive.unpack(outputDir)

                val fileA = outputDir.resolve("a")
                val fileB = outputDir.resolve("dir/b")

                fileA shouldBe aFile()
                fileA.readText() shouldBe "a\n"
                fileB shouldBe aFile()
                fileB.readText() shouldBe "b\n"
            }
        }

        "Zip archives" should {
            "unpack" {
                val archive = File("src/test/assets/test.zip")

                archive.unpack(outputDir)

                val fileA = outputDir.resolve("a")
                val fileB = outputDir.resolve("dir/b")

                fileA shouldBe aFile()
                fileA.readText() shouldBe "a\n"
                fileB shouldBe aFile()
                fileB.readText() shouldBe "b\n"
            }
        }

        "7z archives" should {
            "unpack" {
                val archive = File("src/test/assets/test.7z")

                archive.unpack(outputDir)

                val fileA = outputDir.resolve("a")
                val fileB = outputDir.resolve("dir/b")

                fileA shouldBe aFile()
                fileA.readText() shouldBe "a\n"
                fileB shouldBe aFile()
                fileB.readText() shouldBe "b\n"
            }
        }

        "Debian deb archives" should {
            "unpack" {
                val tempDir = createTestTempDir(ORT_NAME)
                val archiveDeb = File("src/test/assets/testpkg.deb")
                val archiveUdepTemp = tempDir.resolve("testpkg.udeb")
                val archiveUdep = archiveDeb.copyTo(archiveUdepTemp)

            listOf(archiveDeb, archiveUdep).forAll { archive ->
                archive.unpack(outputDir)

                    val extractedScriptFile = outputDir.resolve("data/usr/bin/test")
                    extractedScriptFile shouldBe aFile()

                    val extractedControlFile = outputDir.resolve("control/control")
                    extractedControlFile shouldBe aFile()
                    val expectedControl = File("src/test/assets/control-expected.txt").readText()
                    extractedControlFile.readText() shouldBe expectedControl

                    DEBIAN_PACKAGE_SUBARCHIVES.forEach { tarFileName ->
                        val tarFile = outputDir.resolve(tarFileName)
                        tarFile shouldNotBe aFile()
                    }
                }
            }
        }
    }
}
