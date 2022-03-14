/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.file.containNFiles
import io.kotest.matchers.file.exist
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import java.io.File

import org.ossreviewtoolkit.utils.test.createTestTempDir

class DirectoryStashTest : StringSpec() {
    private lateinit var sandboxDir: File
    private lateinit var a: File
    private lateinit var a1: File
    private lateinit var b: File
    private lateinit var b1: File

    override suspend fun beforeTest(testCase: TestCase) {
        sandboxDir = createTestTempDir()
        a = sandboxDir.resolve("a")
        a1 = a.resolve("a1")
        b = sandboxDir.resolve("b")
        b1 = b.resolve("b1")

        check(a1.mkdirs())
        check(b1.mkdirs())
    }

    private fun sandboxDirShouldBeInOriginalState() {
        sandboxDir should containNFiles(2)
        a should containNFiles(1)
        b should containNFiles(1)
        a should exist()
        a1 should exist()
        b should exist()
        b1 should exist()
    }

    init {
        "given single directory, when stashed, subtree is not existent" {
            stashDirectories(a).use {
                a shouldNot exist()
                a1 shouldNot exist()
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
            stashDirectories(a, a1).use {
                a shouldNot exist()
                a1 shouldNot exist()
            }
        }

        "given parent and child, un-stashing works" {
            stashDirectories(a, a1).use {}

            sandboxDirShouldBeInOriginalState()
        }

        "given child and parent, stashing works" {
            stashDirectories(a1, a).use {
                a shouldNot exist()
                a1 shouldNot exist()
            }
        }

        "given child and parent, un-stashing works" {
            stashDirectories(a1, a).use {}

            sandboxDirShouldBeInOriginalState()
        }

        "stashing an initially non-existing directory deletes it when un-stashing" {
            val nonExistingDir = sandboxDir.resolve("initially-non-existing-directory")

            stashDirectories(nonExistingDir).use {
                nonExistingDir.mkdirs() shouldBe true
            }

            sandboxDirShouldBeInOriginalState()
        }
    }
}
