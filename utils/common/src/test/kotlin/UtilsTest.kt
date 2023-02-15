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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

class UtilsTest : WordSpec({
    "getCommonParentFile" should {
        fun getCommonParentFile(vararg files: String) = getCommonParentFile(files.map { File(it) })

        "return a file with an empty path for an empty list" {
            getCommonParentFile() shouldBe File("")
        }

        "return the parent file for a single file" {
            getCommonParentFile("/foo/bar") shouldBe File("/foo")
        }

        "return a file with an empty path for files that have no parent in common".config(enabled = Os.isWindows) {
            getCommonParentFile("C:/foo", "D:/bar") shouldBe File("")
        }

        "return the root directory for different files with absolute paths".config(enabled = !Os.isWindows) {
            getCommonParentFile("/foo", "/bar") shouldBe File("/")
        }

        "return the relative root directory for different files with relative paths" {
            getCommonParentFile("foo/bar.ext", "bar.ext") shouldBe File("")
        }

        "return the common parent for relative files" {
            getCommonParentFile("common/foo", "common/bar") shouldBe File("common")
        }
    }

    "getAllAncestorDirectories" should {
        "return all ancestor directories ordered along the path to root" {
            getAllAncestorDirectories("/a/b/c") should containExactly(
                "/a/b",
                "/a",
                "/"
            )
        }
    }
})
