/*
 * Copyright (C) 2019 HERE Europe B.V.
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

package com.here.ort.utils.storage

import com.here.ort.utils.safeMkdirs
import com.here.ort.utils.unpack

import io.kotlintest.TestCase
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

class FileArchiverTest : StringSpec() {
    lateinit var workingDir: File
    lateinit var storageDir: File
    lateinit var storage: LocalFileStorage

    override fun beforeTest(testCase: TestCase) {
        workingDir = createTempDir("ort").also { it.deleteOnExit() }
        storageDir = createTempDir("ort").also { it.deleteOnExit() }
        storage = LocalFileStorage(storageDir)
    }

    private fun createFile(path: String) {
        val file = workingDir.resolve(path)
        file.parentFile.safeMkdirs()
        file.writeText(path)
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

            val unzipDir = createTempDir()
            unzipDir.deleteOnExit()
            archiveFile.unpack(unzipDir)

            fun assertFileSaved(path: String) {
                val file = unzipDir.resolve(path)
                file.isFile shouldBe true
                file.readText() shouldBe path
            }

            assertFileSaved("a")
            assertFileSaved("d/a")

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

            val targetDir = createTempDir("ort").also { it.deleteOnExit() }
            val result = archiver.unarchive(targetDir, storagePath)

            fun assertFileUnarchived(path: String) {
                val file = targetDir.resolve(path)
                file.isFile shouldBe true
                file.readText() shouldBe path
            }

            result shouldBe true
            assertFileUnarchived("a")
            assertFileUnarchived("b")
            assertFileUnarchived("c/a")
            assertFileUnarchived("c/b")
        }
    }
}
