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

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

class FileSaverTest : StringSpec({
    "All files matching any of the patterns are saved" {
        val dir = createTempDir("ort")

        fun createFile(path: String) {
            val file = dir.resolve(path)
            file.parentFile.safeMkdirs()
            file.writeText(path)
        }

        createFile("a")
        createFile("b")
        createFile("c")
        createFile("d/a")
        createFile("d/b")
        createFile("d/c")
        createFile("d/d/a")
        createFile("d/d/b")
        createFile("d/d/c")

        val storageDir = createTempDir("ort")
        val storage = LocalFileStorage(storageDir)
        val saver = FileSaver(listOf("a", "**/a", "**/b"), storage)

        saver.save(dir, "save")

        fun assertFileSaved(path: String) {
            val file = storageDir.resolve("save/$path")
            file.isFile shouldBe true
            file.readText() shouldBe path
        }

        assertFileSaved("a")
        assertFileSaved("d/a")
        assertFileSaved("d/b")
        assertFileSaved("d/d/a")
        assertFileSaved("d/d/b")

        fun assertFileNotSaved(path: String) {
            val file = storageDir.resolve("save/$path")
            file.exists() shouldBe false
        }

        assertFileNotSaved("b")
        assertFileNotSaved("c")
        assertFileNotSaved("d/c")
        assertFileNotSaved("d/d/c")
    }
})
