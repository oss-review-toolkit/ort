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

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.file.aDirectory
import io.kotest.matchers.file.aFile
import io.kotest.matchers.file.exist
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import java.io.File
import java.io.IOException

class FileUtilsTest : WordSpec({
    "expandTilde()" should {
        "expand the path if the SHELL environment variable is set".config(enabled = Os.env["SHELL"] != null) {
            File("~/Desktop").expandTilde() shouldBe Os.userHomeDirectory.resolve("Desktop")
        }

        "make the path absolute if the SHELL environment variable is unset".config(enabled = Os.env["SHELL"] == null) {
            File("~/Desktop").expandTilde() shouldBe File("~/Desktop").absoluteFile
        }

        "not modify the path if it does not start with a tilde" {
            File("/home/user~name").expandTilde() shouldBe File("/home/user~name").absoluteFile
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

    "isSymbolicLink" should {
        val tempDir = tempdir()
        val file = tempDir.resolve("file").apply { createNewFile() }
        val directory = tempDir.resolve("directory").safeMkdirs()

        "return 'false' for non-existent files" {
            tempDir.resolve("non-existent").let { nonExistent ->
                nonExistent shouldNot exist()
                nonExistent.isSymbolicLink shouldBe false
            }
        }

        "return 'false' for files" {
            file shouldBe aFile()
            file.isSymbolicLink shouldBe false
        }

        "return 'false' for directories" {
            directory.isDirectory shouldBe true
            directory.isSymbolicLink shouldBe false
        }

        "return 'false' for hard links on Windows".config(enabled = Os.isWindows) {
            ProcessCapture(tempDir, "cmd", "/c", "mklink", "/h", "hardlink", "file")

            tempDir.resolve("hardlink").let { hardlink ->
                hardlink shouldBe aFile()
                hardlink.isSymbolicLink shouldBe false
            }
        }

        "return 'true' for junctions on Windows".config(enabled = Os.isWindows) {
            ProcessCapture(tempDir, "cmd", "/c", "mklink", "/j", "junction", "directory")

            tempDir.resolve("junction").let { junction ->
                junction.isDirectory shouldBe true
                junction.isSymbolicLink shouldBe true
            }
        }

        "return 'true' for symbolic links to files on Windows".config(enabled = Os.isWindows) {
            ProcessCapture(tempDir, "cmd", "/c", "mklink", "symlink-to-file", "file")

            tempDir.resolve("symlink-to-file").let { symlinkToFile ->
                symlinkToFile shouldBe aFile()
                symlinkToFile.isSymbolicLink shouldBe true
            }
        }

        "return 'true' for symbolic links to directories on Windows".config(enabled = Os.isWindows) {
            ProcessCapture(tempDir, "cmd", "/c", "mklink", "/d", "symlink-to-directory", "directory")

            tempDir.resolve("symlink-to-directory").let { symlinkToDirectory ->
                symlinkToDirectory.isDirectory shouldBe true
                symlinkToDirectory.isSymbolicLink shouldBe true
            }
        }
    }

    "safeMkDirs()" should {
        "succeed if directory already exists" {
            val directory = tempdir()

            directory.isDirectory shouldBe true
            shouldNotThrow<IOException> { directory.safeMkdirs() }
            directory.isDirectory shouldBe true // should still be a directory afterwards
        }

        "succeed if directory could be created" {
            val parent = tempdir()
            val child = File(parent, "child")

            parent.isDirectory shouldBe true
            shouldNotThrow<IOException> { child.safeMkdirs() }
            child.isDirectory shouldBe true
        }

        "succeed if file parent does not yet exist" {
            // Test case for an unexpected behaviour of File.mkdirs() which returns false for
            // File(File("parent1/parent2"), "/").mkdirs() if both "parent" directories do not exist, even when the
            // directory was successfully created.
            val parent = tempdir()
            val nonExistingParent = File(parent, "parent1/parent2")
            val child = File(nonExistingParent, "/")

            parent shouldBe aDirectory()
            nonExistingParent shouldNot exist()
            child shouldNot exist()
            shouldNotThrow<IOException> { child.safeMkdirs() }
            child shouldBe aDirectory()
        }

        "throw exception if file is not a directory" {
            val file = tempfile(null, null)

            file shouldBe aFile()
            shouldThrow<IOException> { file.safeMkdirs() }
            file shouldBe aFile() // should still be a file afterwards
        }
    }

    "searchUpwardFor()" should {
        "find the root Git directory" {
            val dir = File(".").searchUpwardFor(dirPath = ".git")

            dir shouldNotBeNull {
                this shouldBe File("../..").absoluteFile.normalize()
            }
        }

        "find the root LICENSE file" {
            val dir = File(".").searchUpwardFor(filePath = "LICENSE")

            dir shouldNotBeNull {
                this shouldBe File("../..").absoluteFile.normalize()
            }
        }

        "find nothing for a file receiver" {
            val file = tempfile()

            file.searchUpwardFor(filePath = file.name) should beNull()
        }
    }
})
