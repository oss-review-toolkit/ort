/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
import io.kotest.engine.spec.tempdir
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.file.aFile
import io.kotest.matchers.file.exist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.safeMkdirs

class LocalFileStorageFunTest : WordSpec({
    fun storage(block: (LocalFileStorage, File) -> Unit) {
        val directory = tempdir()
        val storage = LocalFileStorage(directory)
        block(storage, directory)
    }

    "Creating the storage" should {
        "succeed if the directory exists" {
            shouldNotThrowAny {
                LocalFileStorage(tempdir())
            }
        }

        "succeed if the directory does not exist and must be created" {
            val directory = tempdir()
            val storageDirectory = directory / "create" / "storage"

            LocalFileStorage(storageDirectory).write("file", InputStream.nullInputStream())

            storageDirectory.isDirectory shouldBe true
        }

        "fail if the directory is a file" {
            val storageDirectory = tempfile()

            shouldThrow<IOException> {
                LocalFileStorage(storageDirectory).write("file", InputStream.nullInputStream())
            }
        }
    }

    "exists()" should {
        "return true if the file exists" {
            storage { storage, directory ->
                directory.resolve("existing-file").writeText("content")

                storage.exists("existing-file") shouldBe true
            }
        }

        "return false if the file does not exist" {
            storage { storage, _ ->
                storage.exists("file-does-not-exist") shouldBe false
            }
        }
    }

    "read()" should {
        "succeed if the file exists" {
            storage { storage, directory ->
                val file = directory / "existing-file"
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

    "write()" should {
        "succeed if the file does not exist" {
            storage { storage, directory ->
                storage.write("target/file", "content".byteInputStream())

                val file = directory / "target" / "file"

                file shouldBe aFile()
                file.readText() shouldBe "content"
            }
        }

        "succeed if the file does exist" {
            storage { storage, directory ->
                val file = directory / "file"
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

                val file = directory / ".." / "file"

                file shouldNot exist()
            }
        }

        "fail if the target path is a directory" {
            storage { storage, directory ->
                val dir = directory.resolve("dir").safeMkdirs()

                shouldThrow<FileNotFoundException> {
                    storage.write("dir", "content".byteInputStream())
                }

                dir.isDirectory shouldBe true
            }
        }
    }

    "delete()" should {
        "return true if the file exists" {
            storage { storage, directory ->
                val file = directory / "file"
                file.writeText("content")

                storage.delete("file") shouldBe true

                file shouldNot exist()
            }
        }

        "return false if the file does not exist" {
            storage { storage, _ ->
                storage.delete("file-does-not-exist") shouldBe false
            }
        }
    }
})
