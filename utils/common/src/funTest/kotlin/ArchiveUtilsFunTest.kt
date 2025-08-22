/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.kotest.engine.spec.tempdir
import io.kotest.engine.spec.tempfile
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

import org.ossreviewtoolkit.utils.test.readResource

class ArchiveUtilsFunTest : WordSpec() {
    private lateinit var outputDir: File

    override suspend fun beforeEach(testCase: TestCase) {
        outputDir = tempdir(testCase.name.name.replace(' ', '-'))
    }

    init {
        // The test for DEB is implemented differently and cannot be covered by the parameterized loop.
        (ArchiveType.entries - ArchiveType.DEB - ArchiveType.NONE).forEach { type ->
            val extension = type.extensions.first()

            "${type.name} archives" should {
                "unpack" {
                    val archive = extractResource("/test$extension", tempfile(suffix = extension))

                    archive.unpack(outputDir)

                    val fileA = outputDir / "a"
                    val fileB = outputDir / "dir" / "b"

                    fileA shouldBe aFile()
                    fileA.readText() shouldBe "a\n"
                    fileB shouldBe aFile()
                    fileB.readText() shouldBe "b\n"
                }

                "unpack with a filter" {
                    val archive = extractResource("/test$extension", tempfile(suffix = extension))

                    archive.unpack(outputDir, filter = A_FILTER)

                    val fileA = outputDir / "a"
                    val fileB = outputDir / "dir" / "b"

                    fileA shouldBe aFile()
                    fileB shouldNotBe aFile()
                }
            }
        }

        "DEB archives" should {
            "unpack" {
                val tempDir = tempdir()
                val archiveDeb = extractResource("/testpkg.deb", tempfile(suffix = ".deb"))
                val archiveUdepTemp = tempDir.resolve("testpkg.udeb")
                val archiveUdep = archiveDeb.copyTo(archiveUdepTemp)

                listOf(archiveDeb, archiveUdep).forAll { archive ->
                    archive.unpack(outputDir)

                    val extractedScriptFile = outputDir.resolve("data/usr/bin/test")
                    extractedScriptFile shouldBe aFile()

                    val extractedControlFile = outputDir.resolve("control/control")
                    extractedControlFile shouldBe aFile()
                    val expectedControl = readResource("/control-expected.txt")
                    extractedControlFile.readText() shouldBe expectedControl

                    DEB_NESTED_ARCHIVES.forEach { tarFileName ->
                        val tarFile = outputDir.resolve(tarFileName)
                        tarFile shouldNotBe aFile()
                    }
                }
            }

            "unpack with a filter" {
                val archive = extractResource("/testpkg.deb", tempfile(suffix = ".deb"))

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
                val archive = extractResource("/test.wrong.zip", tempfile(suffix = ".zip"))

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
            // The test for DEB is implemented differently and cannot be covered by the parameterized loop.
            (ArchiveType.entries - ArchiveType.DEB - ArchiveType.NONE).forEach { type ->
                val extension = type.extensions.first()

                "unpack $type archives" {
                    val archive = extractResource("/test$extension", tempfile())

                    archive.unpackTryAllTypes(outputDir)

                    val fileA = outputDir.resolve("a")
                    val fileB = outputDir.resolve("dir/b")

                    fileA shouldBe aFile()
                    fileA.readText() shouldBe "a\n"
                    fileB shouldBe aFile()
                    fileB.readText() shouldBe "b\n"
                }
            }

            "unpack DEB archives" {
                val archive = extractResource("/testpkg.deb", tempfile())

                archive.unpackTryAllTypes(outputDir)

                val extractedScriptFile = outputDir.resolve("data/usr/bin/test")
                extractedScriptFile shouldBe aFile()

                val extractedControlFile = outputDir.resolve("control/control")
                extractedControlFile shouldBe aFile()
                val expectedControl = readResource("/control-expected.txt")
                extractedControlFile.readText() shouldBe expectedControl

                DEB_NESTED_ARCHIVES.forEach { tarFileName ->
                    val tarFile = outputDir.resolve(tarFileName)
                    tarFile shouldNotBe aFile()
                }
            }

            "unpack with a filter" {
                val archive = extractResource("/test.7z", tempfile())

                archive.unpackTryAllTypes(outputDir, filter = A_FILTER)

                val fileA = outputDir.resolve("a")
                val fileB = outputDir.resolve("dir/b")

                fileA shouldBe aFile()
                fileB shouldNotBe aFile()
            }

            "throw an exception if the archive cannot be unpacked" {
                val noArchive = tempfile(suffix = ".abc")
                noArchive.writeText("This is not an archive.")

                val exception = shouldThrow<IOException> {
                    noArchive.unpackTryAllTypes(outputDir)
                }

                exception.message shouldContain noArchive.toString()
            }
        }

        "packZip" should {
            "be able to zip a single file" {
                val file = tempfile().apply { writeText("Hello World!") }

                val zipFile = file.packZip(outputDir.resolve("archive.zip"))

                zipFile shouldBe aFile()
                shouldNotThrow<IOException> {
                    zipFile.unpackZip(outputDir)
                    outputDir.resolve(file.name).readText() shouldBe "Hello World!"
                }
            }

            "not follow symbolic links".config(enabled = Os.isLinux) {
                val inputDir = tempdir()
                val parentDir = inputDir.resolve("parent").safeMkdirs()
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
