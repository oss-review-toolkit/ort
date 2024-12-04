/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.scanner

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.toYaml

class FileListTest : WordSpec({
    "serializing a file list" should {
        "sort the files" {
            val fileList = FileList(
                files = setOf(
                    FileList.FileEntry("src/test.kt", "sha1"),
                    FileList.FileEntry("src/main.kt", "sha1"),
                    FileList.FileEntry("README.md", "sha1")
                ),
                ignorePatterns = emptySet()
            )

            fileList.toYaml().trim() shouldBe """
                ---
                ignore_patterns: []
                files:
                - path: "README.md"
                  sha1: "sha1"
                - path: "src/main.kt"
                  sha1: "sha1"
                - path: "src/test.kt"
                  sha1: "sha1"
            """.trimIndent()
        }

        "sort the ignore patterns" {
            val fileList = FileList(
                files = emptySet(),
                ignorePatterns = setOf(
                    "src/test/**",
                    "src/funTest/**",
                    "README.md"
                )
            )

            fileList.toYaml().trim() shouldBe """
                ---
                ignore_patterns:
                - "README.md"
                - "src/funTest/**"
                - "src/test/**"
                files: []
            """.trimIndent()
        }
    }
})
