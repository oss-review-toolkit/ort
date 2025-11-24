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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.file.aDirectory
import io.kotest.matchers.file.aFile
import io.kotest.matchers.file.containNFiles
import io.kotest.matchers.file.exist
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import java.io.File
import java.io.IOException

class DirectoryStashFunTest : StringSpec() {
    private lateinit var sandboxDir: File

    private lateinit var a: File
    private lateinit var aSubdir: File
    private lateinit var aNestedFile: File

    private lateinit var b: File
    private lateinit var bSubdir: File
    private lateinit var bNestedFile: File

    override suspend fun beforeTest(testCase: TestCase) {
        sandboxDir = tempdir()

        a = sandboxDir / "a"
        aSubdir = a / "a-subdir"
        aNestedFile = aSubdir / "a-file"

        b = sandboxDir / "b"
        bSubdir = b / "b-subdir"
        bNestedFile = bSubdir / "b-file"

        check(aSubdir.mkdirs())
        check(aNestedFile.createNewFile())

        check(bSubdir.mkdirs())
        check(bNestedFile.createNewFile())
    }

    private fun sandboxDirShouldBeInOriginalState() {
        sandboxDir should containNFiles(2)

        a shouldBe aDirectory()
        a should containNFiles(1)
        aSubdir shouldBe aDirectory()
        aNestedFile shouldBe aFile()

        b shouldBe aDirectory()
        b should containNFiles(1)
        bSubdir shouldBe aDirectory()
        bNestedFile shouldBe aFile()
    }

    init {
        "given single directory, when stashed, subtree is not existent" {
            stashDirectories(a).use {
                a shouldNot exist()
                aSubdir shouldNot exist()
            }
        }

        "given single stashed directory, when un-stashed, sandbox dir is in original state" {
            stashDirectories(a).use {}

            sandboxDirShouldBeInOriginalState()
        }

        "given single directory, when stashed, sibling is not affected" {
            stashDirectories(a).use {
                b should exist()
            }
        }

        "given conflicting files created while stashed, when un-stashed, sandbox dir is in original state" {
            stashDirectories(a).use {
                val a2 = File(a, "a2")
                check(a2.mkdirs())
            }

            sandboxDirShouldBeInOriginalState()
        }

        "given non-existing directory, stash has no effect" {
            stashDirectories(File(a, "a2")).use {}

            sandboxDirShouldBeInOriginalState()
        }

        "given non-existing directory, un-stash has no effect" {
            stashDirectories(File(a, "a2")).use {}

            sandboxDirShouldBeInOriginalState()
        }

        "given parent and child, stashing works" {
            stashDirectories(a, aSubdir).use {
                a shouldNot exist()
                aSubdir shouldNot exist()
            }
        }

        "given parent and child, un-stashing works" {
            stashDirectories(a, aSubdir).use {}

            sandboxDirShouldBeInOriginalState()
        }

        "given child and parent, stashing works" {
            stashDirectories(aSubdir, a).use {
                a shouldNot exist()
                aSubdir shouldNot exist()
            }
        }

        "given child and parent, un-stashing works" {
            stashDirectories(aSubdir, a).use {}

            sandboxDirShouldBeInOriginalState()
        }

        "stashing an initially non-existing directory deletes it when un-stashing" {
            val nonExistingDir = sandboxDir.resolve("initially-non-existing-directory")

            stashDirectories(nonExistingDir).use {
                nonExistingDir.mkdirs() shouldBe true
            }

            sandboxDirShouldBeInOriginalState()
        }

        "stashed directories are restored even if an exception is thrown" {
            shouldThrow<IOException> {
                stashDirectories(a, b).use {
                    throw IOException()
                }
            }

            sandboxDirShouldBeInOriginalState()
        }

        "individual files can be stashed" {
            stashDirectories(aNestedFile, bNestedFile).use {}

            sandboxDirShouldBeInOriginalState()
        }

        "files and their parent directories can be stashed" {
            stashDirectories(aNestedFile, aSubdir, bNestedFile, bSubdir).use {}

            sandboxDirShouldBeInOriginalState()
        }

        "root directories and files can be stashed" {
            stashDirectories(a, aNestedFile, b, bNestedFile).use {}

            sandboxDirShouldBeInOriginalState()
        }
    }
}
