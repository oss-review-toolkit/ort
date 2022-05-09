/*
 * Copyright (C) 2022 Bosch.IO GmbH
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
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.file.shouldHaveFileSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.utils.test.createTestTempFile

class FileFormatTest : WordSpec({
    "File.readTree()" should {
        "return and empty node for empty files" {
            val file = createTestTempFile(suffix = ".json")

            file shouldHaveFileSize 0
            file.readTree() shouldBe EMPTY_JSON_NODE
        }

        "throw for invalid files" {
            val file = createTestTempFile(suffix = ".json").apply { writeText("foo") }

            shouldThrow<JsonParseException> {
                file.readTree()
            }
        }
    }

    "File.readValueOrNull()" should {
        "return null for empty files" {
            val file = createTestTempFile(suffix = ".json")

            file shouldHaveFileSize 0
            file.readValueOrNull<Any>() should beNull()
        }

        "throw for invalid files" {
            val file = createTestTempFile(suffix = ".json").apply { writeText("foo") }

            shouldThrow<JsonParseException> {
                file.readValueOrNull()
            }
        }
    }
})
