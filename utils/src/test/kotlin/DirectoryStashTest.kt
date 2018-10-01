/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

package com.here.ort.utils

import io.kotlintest.Description
import io.kotlintest.TestResult
import io.kotlintest.matchers.file.containNFiles
import io.kotlintest.matchers.file.exist
import io.kotlintest.should
import io.kotlintest.shouldNot
import io.kotlintest.specs.StringSpec
import java.io.Closeable
import java.io.File

class DirectoryStashTest : StringSpec() {
private lateinit var directoryStash : Closeable

    private lateinit var sandboxDir: File
    private lateinit var a: File
    private lateinit var a1: File
    private lateinit var b: File
    private lateinit var b1: File

    override fun beforeTest(description: Description) {
        sandboxDir = createTempDir()
        a = File(sandboxDir, "a")
        a1 = File(a, "a1")
        b = File(sandboxDir, "b")
        b1 = File(b, "b1")

        check(a1.mkdirs())
        check(b1.mkdirs())
    }

    override fun afterTest(description: Description, result: TestResult) {
        directoryStash.close()
        sandboxDir.safeDeleteRecursively()
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
            directoryStash = stashDirectories(a)

            a shouldNot exist()
            a1 shouldNot exist()
        }

        "given single stashed directory, when un-stashed, sandbox dir is in original state" {
            directoryStash = stashDirectories(a)

            directoryStash.close()

            sandboxDirShouldBeInOriginalState()
        }

        "given single directory, when stashed, sibling is not affected" {
            directoryStash = stashDirectories(a)

            b should exist()
        }

        "given conflicting files created while stashed, when un-stashed, sandbox dir is in original state" {
            directoryStash = stashDirectories(a)
            val a2 = File(a, "a2")
            check(a2.mkdirs())

            directoryStash.close()

            sandboxDirShouldBeInOriginalState()
        }

        "given non-existing directory, stash has no effect" {
            directoryStash = stashDirectories(File(a, "a2"))

            sandboxDirShouldBeInOriginalState()
        }

        "given non-existing directory, un-stash has no effect" {
            directoryStash = stashDirectories(File(a, "a2"))

            directoryStash.close()

            sandboxDirShouldBeInOriginalState()
        }

        "given parent and child, stashing works" {
            directoryStash = stashDirectories(a, a1)

            a shouldNot exist()
            a1 shouldNot exist()
        }

        "given parent and child, un-stashing works" {
            directoryStash = stashDirectories(a, a1)

            directoryStash.close()

            sandboxDirShouldBeInOriginalState()
        }

        "given child and parent, stashing works".config( enabled = false ) {
            directoryStash = stashDirectories(a1, a)

            a shouldNot exist()
            a1 shouldNot exist()
        }

        "given child and parent, un-stashing works".config( enabled = false ) {
            directoryStash = stashDirectories(a1, a)

            directoryStash.close()

            sandboxDirShouldBeInOriginalState()
        }
    }
}
