/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import java.io.BufferedReader
import java.io.File

import org.ossreviewtoolkit.utils.test.createTestTempDir

class XZCompressedLocalFileStorageFunTest : StringSpec() {
    private fun storage(block: (XZCompressedLocalFileStorage, File) -> Unit) {
        val directory = createTestTempDir()
        val storage = XZCompressedLocalFileStorage(directory)
        block(storage, directory)
    }

    init {
        "Can read written compressed data" {
            storage { storage, _ ->
                storage.write("new-file", "content".byteInputStream())

                val content = storage.read("new-file").bufferedReader().use(BufferedReader::readText)

                content shouldBe "content"
            }
        }

        "Can read existing uncompressed data" {
            storage { storage, directory ->
                val file = directory.resolve("existing-file")
                file.writeText("content")

                val content = storage.read("existing-file").bufferedReader().use(BufferedReader::readText)

                content shouldBe "content"
            }
        }
    }
}
