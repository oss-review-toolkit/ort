/*
 * Copyright (C) 2019-2021 HERE Europe B.V.
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

package org.ossreviewtoolkit.model.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.shouldBe

import java.io.File

import kotlin.io.path.createTempDirectory

import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.safeDeleteRecursively
import org.ossreviewtoolkit.utils.safeMkdirs
import org.ossreviewtoolkit.utils.storage.LocalFileStorage
import org.ossreviewtoolkit.utils.test.createDefault
import org.ossreviewtoolkit.utils.unpack

class FileArchiverTest : StringSpec() {
    private lateinit var workingDir: File
    private lateinit var storageDir: File
    private lateinit var targetDir: File
    private lateinit var storage: LocalFileStorage

    override fun beforeTest(testCase: TestCase) {
        workingDir = createTempDirectory("$ORT_NAME-${javaClass.simpleName}-workingDir").toFile()
        storageDir = createTempDirectory("$ORT_NAME-${javaClass.simpleName}-storageDir").toFile()
        targetDir = createTempDirectory("$ORT_NAME-${javaClass.simpleName}-targetDir").toFile()
        storage = LocalFileStorage(storageDir)
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        workingDir.safeDeleteRecursively()
        storageDir.safeDeleteRecursively()
        targetDir.safeDeleteRecursively()
    }

    private fun createFile(path: String) {
        val file = workingDir.resolve(path)
        file.parentFile.safeMkdirs()
        file.writeText(path)
    }

    private fun File.assertFileContent(path: String) {
        val file = resolve(path)
        file.isFile shouldBe true
        file.readText() shouldBe path
    }

    init {
        "All files matching any of the patterns are archived" {
            createFile("a")
            createFile("b")
            createFile("d/a")
            createFile("d/b")

            val archiver = FileArchiver(listOf("a", "**/a"), storage)

            archiver.archive(workingDir, "save")

            val archiveFile = storageDir.resolve("save/archive.zip")

            archiveFile.isFile shouldBe true

            archiveFile.unpack(targetDir)

            targetDir.assertFileContent("a")
            targetDir.assertFileContent("d/a")

            fun assertFileNotSaved(path: String) {
                val file = storageDir.resolve("save/$path")
                file.exists() shouldBe false
            }

            assertFileNotSaved("b")
            assertFileNotSaved("d/b")
        }

        "All archived files are unarchived" {
            createFile("a")
            createFile("b")
            createFile("c/a")
            createFile("c/b")

            val storagePath = "save"
            val archiver = FileArchiver(listOf("**"), storage)
            archiver.archive(workingDir, storagePath)

            val result = archiver.unarchive(targetDir, storagePath)

            result shouldBe true
            with(targetDir) {
                assertFileContent("a")
                assertFileContent("b")
                assertFileContent("c/a")
                assertFileContent("c/b")
            }
        }

        "LICENSE files are archived by default, independently of the directory" {
            createFile("LICENSE")
            createFile("path/LICENSE")

            val archiver = FileArchiver.createDefault()
            archiver.archive(workingDir, "save")
            archiver.unarchive(targetDir, "save")

            with(targetDir) {
                assertFileContent("LICENSE")
                assertFileContent("path/LICENSE")
            }
        }

        "The pattern matching is case-insensitive" {
            createFile("a/LICENSE")
            createFile("b/License")
            createFile("c/license")
            createFile("d/LiCeNsE")

            val archiver = FileArchiver.createDefault()
            archiver.archive(workingDir, "save")
            archiver.unarchive(targetDir, "save")

            with(targetDir) {
                assertFileContent("a/LICENSE")
                assertFileContent("b/License")
                assertFileContent("c/license")
                assertFileContent("d/LiCeNsE")
            }
        }
    }
}
