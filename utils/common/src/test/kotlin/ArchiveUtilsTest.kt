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

package org.ossreviewtoolkit.utils.common

import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.kotest.inspectors.forAll
import io.kotest.matchers.file.aFile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import java.io.File

import org.apache.commons.compress.archivers.ArchiveEntry

import org.ossreviewtoolkit.utils.test.createTestTempDir

class ArchiveUtilsTest : WordSpec() {
    private lateinit var outputDir: File

    override suspend fun beforeEach(testCase: TestCase) {
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

            "unpack with a filter" {
                val archive = File("src/test/assets/test.tar")

                archive.unpack(outputDir, A_FILTER)

                val fileA = outputDir.resolve("a")
                val fileB = outputDir.resolve("dir/b")

                fileA shouldBe aFile()
                fileB shouldNotBe aFile()
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

            "unpack with a filter" {
                val archive = File("src/test/assets/test.tar.gz")

                archive.unpack(outputDir, A_FILTER)

                val fileA = outputDir.resolve("a")
                val fileB = outputDir.resolve("dir/b")

                fileA shouldBe aFile()
                fileB shouldNotBe aFile()
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

            "unpack with a filter" {
                val archive = File("src/test/assets/test.tar.bz2")

                archive.unpack(outputDir, A_FILTER)

                val fileA = outputDir.resolve("a")
                val fileB = outputDir.resolve("dir/b")

                fileA shouldBe aFile()
                fileB shouldNotBe aFile()
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

            "unpack with a filter" {
                val archive = File("src/test/assets/test.tar.xz")

                archive.unpack(outputDir, A_FILTER)

                val fileA = outputDir.resolve("a")
                val fileB = outputDir.resolve("dir/b")

                fileA shouldBe aFile()
                fileB shouldNotBe aFile()
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

            "unpack with a filter" {
                val archive = File("src/test/assets/test.zip")

                archive.unpack(outputDir, A_FILTER)

                val fileA = outputDir.resolve("a")
                val fileB = outputDir.resolve("dir/b")

                fileA shouldBe aFile()
                fileB shouldNotBe aFile()
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

            "unpack with a filter" {
                val archive = File("src/test/assets/test.7z")

                archive.unpack(outputDir, A_FILTER)

                val fileA = outputDir.resolve("a")
                val fileB = outputDir.resolve("dir/b")

                fileA shouldBe aFile()
                fileB shouldNotBe aFile()
            }
        }

        "Debian deb archives" should {
            "unpack" {
                val tempDir = createTestTempDir()
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

                    DEB_NESTED_ARCHIVES.forEach { tarFileName ->
                        val tarFile = outputDir.resolve(tarFileName)
                        tarFile shouldNotBe aFile()
                    }
                }
            }

            "unpack with a filter" {
                val archive = File("src/test/assets/testpkg.deb")

                archive.unpack(outputDir) { it.name.endsWith("test") }

                val extractedScriptFile = outputDir.resolve("data/usr/bin/test")
                extractedScriptFile shouldBe aFile()

                val extractedControlFile = outputDir.resolve("control/control")
                extractedControlFile shouldNotBe aFile()
            }
        }
    }
}

/**
 * A filter for archive entries that filters only for a file named "a".
 */
private val A_FILTER: (ArchiveEntry) -> Boolean = { it.name == "a" }
