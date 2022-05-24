/*
 * Copyright (C) 2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.utils.ort.storage

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.file.aFile
import io.kotest.matchers.file.exist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException

import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.test.createTestTempDir
import org.ossreviewtoolkit.utils.test.createTestTempFile

class LocalFileStorageFunTest : WordSpec() {
    private fun storage(block: (LocalFileStorage, File) -> Unit) {
        val directory = createTestTempDir()
        val storage = LocalFileStorage(directory)
        block(storage, directory)
    }

    init {
        "Creating the storage" should {
            "succeed if the directory exists" {
                shouldNotThrowAny {
                    LocalFileStorage(createTestTempDir())
                }
            }

            "succeed if the directory does not exist and must be created" {
                val directory = createTestTempDir()
                val storageDirectory = directory.resolve("create/storage")

                LocalFileStorage(storageDirectory)

                storageDirectory.isDirectory shouldBe true
            }

            "fail if the directory is a file" {
                val storageDirectory = createTestTempFile()

                shouldThrow<IllegalArgumentException> {
                    LocalFileStorage(storageDirectory)
                }
            }
        }

        "Reading a file" should {
            "succeed if the file exists" {
                storage { storage, directory ->
                    val file = directory.resolve("existing-file")
                    file.writeText("content")

                    val content = storage.read("existing-file").bufferedReader().use(BufferedReader::readText)

                    content shouldBe "content"
                }
            }

            "fail if the file does not exist" {
                storage { storage, _ ->
                    shouldThrow<FileNotFoundException> {
                        storage.read("file-does-not-exist")
                    }
                }
            }

            "fail if the requested path is not inside the storage directory" {
                storage { storage, _ ->
                    shouldThrow<IllegalArgumentException> {
                        storage.read("../file")
                    }
                }
            }
        }

        "Writing a file" should {
            "succeed if the file does not exist" {
                storage { storage, directory ->
                    storage.write("target/file", "content".byteInputStream())

                    val file = directory.resolve("target/file")

                    file shouldBe aFile()
                    file.readText() shouldBe "content"
                }
            }

            "succeed if the file does exist" {
                storage { storage, directory ->
                    val file = directory.resolve("file")
                    file.writeText("old content")

                    storage.write("file", "content".byteInputStream())

                    file shouldBe aFile()
                    file.readText() shouldBe "content"
                }
            }

            "fail if the target path is not inside the storage directory" {
                storage { storage, directory ->
                    shouldThrow<IllegalArgumentException> {
                        storage.write("../file", "content".byteInputStream())
                    }

                    val file = directory.resolve("../file")

                    file shouldNot exist()
                }
            }

            "fail if the target path is a directory" {
                storage { storage, directory ->
                    val dir = directory.resolve("dir")
                    dir.safeMkdirs()

                    shouldThrow<FileNotFoundException> {
                        storage.write("dir", "content".byteInputStream())
                    }

                    dir.isDirectory shouldBe true
                }
            }
        }
    }
}
