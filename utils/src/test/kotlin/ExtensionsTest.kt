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

import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.WordSpec

import java.io.File
import java.io.IOException

class ExtensionsTest : WordSpec({
    "File.searchUpwardsForSubdirectory" should {
        "find the root Git directory" {
            val gitRoot = File(".").searchUpwardsForSubdirectory(".git")

            gitRoot shouldNotBe null
            gitRoot shouldBe File("..").absoluteFile.normalize()
        }
    }

    "File.safeMkDirs()" should {
        "should succeed if directory already exists" {
            val directory = createTempDir()
            directory.deleteOnExit()

            directory.isDirectory shouldBe true
            directory.safeMkdirs() // should not throw exception
            directory.isDirectory shouldBe true // should still be a directory afterwards
        }

        "should succeed if directory could be created" {
            val parent = createTempDir()
            parent.deleteOnExit()
            val child = File(parent, "child")
            child.deleteOnExit()

            parent.isDirectory shouldBe true
            child.safeMkdirs() // should not throw exception
            child.isDirectory shouldBe true
        }

        "should succeed if file parent does not yet exist" {
            // Test case for an unexpected behaviour of File.mkdirs() which returns false for
            // File(File("parent1/parent2"), "/").mkdirs() if both "parent" directories do not exist, even when the
            // directory was successfully created.
            val parent = createTempDir()
            parent.deleteOnExit()
            val nonExistingParent = File(parent, "parent1/parent2")
            nonExistingParent.deleteOnExit()
            val child = File(nonExistingParent, "/")
            child.deleteOnExit()

            parent.isDirectory shouldBe true
            nonExistingParent.exists() shouldBe false
            child.exists() shouldBe false
            child.safeMkdirs() // should not throw exception
            child.isDirectory shouldBe true
        }

        "should throw exception if file is not a directory" {
            val file = createTempFile()
            file.deleteOnExit()

            file.isFile shouldBe true
            shouldThrow<IOException> { file.safeMkdirs() }
            file.isFile shouldBe true // should still be a file afterwards
        }
    }

    "String.isSemanticVersion" should {
        "return true for a semantic version" {
            "1.0.0".isSemanticVersion() shouldBe true
        }

        "return false for a URL" {
            "https://registry.npmjs.org/form-data/-/form-data-0.2.0.tgz".isSemanticVersion() shouldBe false
        }
    }

    "String.stripCredentialsFromUrl" should {
        "strip the user name from a string representing a URL" {
            "ssh://bot@gerrit.host.com:29418/parent/project".stripCredentialsFromUrl() shouldBe
                    "ssh://gerrit.host.com:29418/parent/project"
        }

        "strip the user name and password from a string representing a URL" {
            "ssh://bot:pass@gerrit.host.com:29418/parent/project".stripCredentialsFromUrl() shouldBe
                    "ssh://gerrit.host.com:29418/parent/project"
        }

        "not modify encodings in a URL" {
            "ssh://bot@gerrit.host.com:29418/parent/project%20with%20spaces".stripCredentialsFromUrl() shouldBe
                    "ssh://gerrit.host.com:29418/parent/project%20with%20spaces"
        }

        "not modify a string not representing a URL" {
            "This is not a URL".stripCredentialsFromUrl() shouldBe "This is not a URL"
        }
    }

    "String.urlencode" should {
        val str = "project: fünky\$name*>nul."

        "encode '*'" {
            "*".fileSystemEncode() shouldBe "%2A"
        }

        "encode '.'" {
            ".".fileSystemEncode() shouldBe "%2E"
        }

        "encode ':'" {
            ":".fileSystemEncode() shouldBe "%3A"
        }

        "create a valid file name" {
            val tempDir = createTempDir()
            val fileFromStr = File(tempDir, str.fileSystemEncode()).apply { writeText("dummy") }

            fileFromStr.isFile shouldBe true

            // This should not throw an IOException.
            tempDir.safeDeleteRecursively()
        }
    }

    "Map.zip" should {
        val operation = { left: Int?, right: Int? -> (left ?: 0) + (right ?: 0) }

        "correctly merge maps" {
            val map = mapOf(
                    "1" to 1,
                    "2" to 2,
                    "3" to 3
            )
            val other = mapOf(
                    "3" to 3,
                    "4" to 4
            )

            val expectedResult = mapOf(
                    "1" to 1,
                    "2" to 2,
                    "3" to 6,
                    "4" to 4
            )

            map.zip(other, operation) shouldBe expectedResult
        }

        "not fail if this map is empty" {
            val other = mapOf("1" to 1)

            emptyMap<String, Int>().zip(other, operation) shouldBe mapOf("1" to 1)
        }

        "not fail if other map is empty" {
            val map = mapOf("1" to 1)

            map.zip(emptyMap(), operation) shouldBe mapOf("1" to 1)
        }
    }

    "Map.zipWithDefault" should {
        val operation = { left: Int, right: Int -> left + right }

        "correctly merge maps" {
            val map = mapOf(
                    "1" to 1,
                    "2" to 2,
                    "3" to 3
            )
            val other = mapOf(
                    "3" to 3,
                    "4" to 4
            )

            val expectedResult = mapOf(
                    "1" to 2,
                    "2" to 3,
                    "3" to 6,
                    "4" to 5
            )

            map.zipWithDefault(other, 1, operation) shouldBe expectedResult
        }

        "not fail if this map is empty" {
            val other = mapOf("1" to 1)

            emptyMap<String, Int>().zipWithDefault(other, 1, operation) shouldBe mapOf("1" to 2)
        }

        "not fail if other map is empty" {
            val map = mapOf("1" to 1)

            map.zipWithDefault(emptyMap(), 1, operation) shouldBe mapOf("1" to 2)
        }
    }
})
