/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.core.JsonParseException

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.file.shouldHaveFileSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.IOException

class FileFormatTest : WordSpec({
    "File.readTree()" should {
        "return and empty node for zero size files" {
            val file = tempfile(suffix = ".json")

            file shouldHaveFileSize 0
            file.readTree() shouldBe EMPTY_JSON_NODE
        }

        "throw for invalid files" {
            val file = tempfile(suffix = ".json").apply { writeText("foo") }

            shouldThrow<JsonParseException> {
                file.readTree()
            }
        }
    }

    "File.readValue()" should {
        "throw for zero size files" {
            val file = tempfile(suffix = ".yml")

            file shouldHaveFileSize 0
            shouldThrow<IOException> {
                file.readValue()
            }
        }

        "refuse to read multiple documents per file" {
            val file = tempfile(null, ".yml").apply {
                @Suppress("MaxLineLength")
                writeText(
                    """
                    ---
                    id: "Maven:dom4j:dom4j:1.6.1"
                    source_artifact_url: "https://repo.maven.apache.org/maven2/dom4j/dom4j/1.6.1/dom4j-1.6.1-sources.jar"
                    ---
                    id: "Maven:dom4j:dom4j:1.6.1"
                    source_artifact_url: "<INTERNAL_ARTIFACTORY>/dom4j-1.6.1-sources.jar"
                    """.trimIndent()
                )
            }

            shouldThrowWithMessage<IOException>(
                "Multiple top-level objects found in file '$file'."
            ) {
                file.readValue()
            }
        }
    }

    "File.readValueOrNull()" should {
        "return null for zero size files" {
            val file = tempfile(suffix = ".json")

            file shouldHaveFileSize 0
            file.readValueOrNull<Any>() should beNull()
        }

        "be able to deserialize empty JSON arrays" {
            val file = tempfile(suffix = ".json").apply { writeText("[]") }

            file.readValueOrNull<List<PackageCuration>>().shouldBeEmpty()
        }

        "be able to deserialize empty YAML sequences" {
            val file = tempfile(suffix = ".yml").apply { writeText("[]") }

            file.readValueOrNull<List<PackageCuration>>().shouldBeEmpty()
        }

        "throw for invalid files" {
            val file = tempfile(suffix = ".json").apply { writeText("foo") }

            shouldThrow<JsonParseException> {
                file.readValueOrNull()
            }
        }

        "refuse to read multiple documents per file" {
            val file = tempfile(null, ".yml").apply {
                @Suppress("MaxLineLength")
                writeText(
                    """
                    ---
                    id: "Maven:dom4j:dom4j:1.6.1"
                    source_artifact_url: "https://repo.maven.apache.org/maven2/dom4j/dom4j/1.6.1/dom4j-1.6.1-sources.jar"
                    ---
                    id: "Maven:dom4j:dom4j:1.6.1"
                    source_artifact_url: "<INTERNAL_ARTIFACTORY>/dom4j-1.6.1-sources.jar"
                    """.trimIndent()
                )
            }

            shouldThrowWithMessage<IOException>(
                "Multiple top-level objects found in file '$file'."
            ) {
                file.readValueOrNull()
            }
        }
    }
})
