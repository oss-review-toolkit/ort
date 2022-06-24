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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.kotest.inspectors.forAll
import io.kotest.matchers.file.aFile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain

import java.io.File
import java.io.IOException
import java.nio.file.Files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import org.apache.commons.compress.archivers.ArchiveEntry

import org.ossreviewtoolkit.utils.test.createTestTempDir
import org.ossreviewtoolkit.utils.test.createTestTempFile

class ArchiveUtilsTest : WordSpec() {
    private lateinit var outputDir: File

    override suspend fun beforeEach(testCase: TestCase) {
        outputDir = createTestTempDir()
    }

    /**
     * Copy the test archive with the given [sourceName] under an unknown file extension to a temporary folder.
     * This is used to test whether archives with unknown file extensions can be unpacked.
     */
    private fun copyTestArchive(sourceName: String): File {
        val sourcePath = File("src/test/assets/$sourceName")
        val targetPath = createTestTempDir().resolve("unknown.dat")

        return sourcePath.copyTo(targetPath)
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

                archive.unpack(outputDir, filter = A_FILTER)

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

                archive.unpack(outputDir, filter = A_FILTER)

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

                archive.unpack(outputDir, filter = A_FILTER)

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

                archive.unpack(outputDir, filter = A_FILTER)

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

                archive.unpack(outputDir, filter = A_FILTER)

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

                archive.unpack(outputDir, filter = A_FILTER)

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

        "unpack" should {
            "throw if no archive type is available" {
                val archive = File("unknown-format.123")

                shouldThrow<IOException> {
                    archive.unpack(outputDir)
                }
            }

            "support overriding the archive type" {
                val archive = File("src/test/assets/test.wrong.zip")

                archive.unpack(outputDir, forceArchiveType = ArchiveType.TAR_GZIP)

                val fileA = outputDir.resolve("a")
                val fileB = outputDir.resolve("dir/b")

                fileA shouldBe aFile()
                fileA.readText() shouldBe "a\n"
                fileB shouldBe aFile()
                fileB.readText() shouldBe "b\n"
            }
        }

        "unpackTryAllTypes" should {
            "unpack a Tar archive" {
                val archive = copyTestArchive("test.tar")

                archive.unpackTryAllTypes(outputDir)

                val fileA = outputDir.resolve("a")
                val fileB = outputDir.resolve("dir/b")

                fileA shouldBe aFile()
                fileA.readText() shouldBe "a\n"
                fileB shouldBe aFile()
                fileB.readText() shouldBe "b\n"
            }

            "unpack a Tar Gz archive" {
                val archive = copyTestArchive("test.tar.gz")

                archive.unpackTryAllTypes(outputDir)

                val fileA = outputDir.resolve("a")
                val fileB = outputDir.resolve("dir/b")

                fileA shouldBe aFile()
                fileA.readText() shouldBe "a\n"
                fileB shouldBe aFile()
                fileB.readText() shouldBe "b\n"
            }

            "unpack bzip2 archives" {
                val archive = copyTestArchive("test.tar.bz2")

                archive.unpackTryAllTypes(outputDir)

                val fileA = outputDir.resolve("a")
                val fileB = outputDir.resolve("dir/b")

                fileA shouldBe aFile()
                fileA.readText() shouldBe "a\n"
                fileB shouldBe aFile()
                fileB.readText() shouldBe "b\n"
            }

            "unpack xz archives" {
                val archive = copyTestArchive("test.tar.xz")

                archive.unpackTryAllTypes(outputDir)

                val fileA = outputDir.resolve("a")
                val fileB = outputDir.resolve("dir/b")

                fileA shouldBe aFile()
                fileA.readText() shouldBe "a\n"
                fileB shouldBe aFile()
                fileB.readText() shouldBe "b\n"
            }

            "unpack Zip archives" {
                val archive = copyTestArchive("test.zip")

                archive.unpackTryAllTypes(outputDir)

                val fileA = outputDir.resolve("a")
                val fileB = outputDir.resolve("dir/b")

                fileA shouldBe aFile()
                fileA.readText() shouldBe "a\n"
                fileB shouldBe aFile()
                fileB.readText() shouldBe "b\n"
            }

            "unpack 7z archives" {
                val archive = copyTestArchive("test.7z")

                archive.unpackTryAllTypes(outputDir)

                val fileA = outputDir.resolve("a")
                val fileB = outputDir.resolve("dir/b")

                fileA shouldBe aFile()
                fileA.readText() shouldBe "a\n"
                fileB shouldBe aFile()
                fileB.readText() shouldBe "b\n"
            }

            "unpack Debian deb archives" {
                val archive = copyTestArchive("testpkg.deb")

                archive.unpackTryAllTypes(outputDir)

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

            "unpack with a filter" {
                val archive = copyTestArchive("test.7z")

                archive.unpackTryAllTypes(outputDir, filter = A_FILTER)

                val fileA = outputDir.resolve("a")
                val fileB = outputDir.resolve("dir/b")

                fileA shouldBe aFile()
                fileB shouldNotBe aFile()
            }

            "throw an exception if the archive cannot be unpacked" {
                val noArchive = createTestTempFile(suffix = ".abc")
                noArchive.writeText("This is not an archive.")

                val exception = shouldThrow<IOException> {
                    noArchive.unpackTryAllTypes(outputDir)
                }

                exception.message shouldContain noArchive.toString()
            }
        }

        "packZip" should {
            "not follow symbolic links".config(enabled = Os.isLinux) {
                val inputDir = createTestTempDir()
                val parentDir = inputDir.resolve("parent").apply { safeMkdirs() }
                val readmeFile = parentDir.resolve("readme.txt").apply { writeText("Hello World!") }

                withContext(Dispatchers.IO) {
                    Files.createSymbolicLink(parentDir.resolve("loop-link").toPath(), parentDir.toPath())
                    Files.createSymbolicLink(parentDir.resolve("readme-link.txt").toPath(), readmeFile.toPath())
                }

                val zipFile = inputDir.packZip(outputDir.resolve("archive.zip")) {
                    it shouldBe readmeFile
                    true
                }

                zipFile shouldBe aFile()
            }
        }
    }
}

/**
 * A filter for archive entries that filters only for a file named "a".
 */
private val A_FILTER: (ArchiveEntry) -> Boolean = { it.name == "a" }
