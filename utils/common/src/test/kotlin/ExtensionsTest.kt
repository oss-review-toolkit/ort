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
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly as containExactlyCollection
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.file.aDirectory
import io.kotest.matchers.file.aFile
import io.kotest.matchers.file.exist
import io.kotest.matchers.maps.containExactly
import io.kotest.matchers.maps.haveKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.types.beInstanceOf

import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.time.DayOfWeek
import java.util.Locale
import java.util.SortedMap

class ExtensionsTest : WordSpec({
    "ByteArray.toHexString()" should {
        "correctly convert a byte array to a string of hexadecimal digits" {
            byteArrayOf(0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte()).toHexString() shouldBe "deadbeef"
        }
    }

    "Collection.getDuplicates()" should {
        "return no duplicates if there are none" {
            emptyList<String>().getDuplicates() should beEmpty()
            listOf("no", "dupes", "in", "here").getDuplicates() should beEmpty()
        }

        "return all duplicates" {
            val strings = listOf("foo", "bar", "baz", "foo", "bar", "bar")

            strings.getDuplicates().shouldContainExactlyInAnyOrder("foo", "bar")
        }

        "return duplicates according to a selector" {
            val pairs = listOf(
                "a" to "b",
                "b" to "b",
                "c" to "d",
                "a" to "z",
                "b" to "c",
                "o" to "z"
            )

            pairs.getDuplicates { it.first } shouldBe mapOf(
                "a" to listOf("a" to "b", "a" to "z"),
                "b" to listOf("b" to "b", "b" to "c")
            )
            pairs.getDuplicates { it.second } shouldBe mapOf(
                "b" to listOf("a" to "b", "b" to "b"),
                "z" to listOf("a" to "z", "o" to "z")
            )
        }
    }

    "EnumSet.plus()" should {
        "create an empty set if both summands are empty" {
            val sum = enumSetOf<DayOfWeek>() + enumSetOf()

            sum should beEmpty()
        }

        "create the correct sum of two sets" {
            val sum = enumSetOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY) + enumSetOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY)

            sum shouldBe enumSetOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY)
        }
    }

    "File.expandTilde()" should {
        "expand the path if the SHELL environment variable is set".config(enabled = Os.env["SHELL"] != null) {
            File("~/Desktop").expandTilde() shouldBe Os.userHomeDirectory.resolve("Desktop")
        }

        "make the path absolute if the SHELL environment variable is unset".config(enabled = Os.env["SHELL"] == null) {
            File("~/Desktop").expandTilde() shouldBe File("~/Desktop").absoluteFile
        }
    }

    "File.isSymbolicLink()" should {
        val tempDir = tempdir()
        val file = tempDir.resolve("file").apply { createNewFile() }
        val directory = tempDir.resolve("directory").safeMkdirs()

        "return 'false' for non-existent files" {
            tempDir.resolve("non-existent").let { nonExistent ->
                nonExistent shouldNot exist()
                nonExistent.isSymbolicLink() shouldBe false
            }
        }

        "return 'false' for files" {
            file shouldBe aFile()
            file.isSymbolicLink() shouldBe false
        }

        "return 'false' for directories" {
            directory.isDirectory shouldBe true
            directory.isSymbolicLink() shouldBe false
        }

        "return 'false' for hard links on Windows".config(enabled = Os.isWindows) {
            ProcessCapture(tempDir, "cmd", "/c", "mklink", "/h", "hardlink", "file")

            tempDir.resolve("hardlink").let { hardlink ->
                hardlink shouldBe aFile()
                hardlink.isSymbolicLink() shouldBe false
            }
        }

        "return 'true' for junctions on Windows".config(enabled = Os.isWindows) {
            ProcessCapture(tempDir, "cmd", "/c", "mklink", "/j", "junction", "directory")

            tempDir.resolve("junction").let { junction ->
                junction.isDirectory shouldBe true
                junction.isSymbolicLink() shouldBe true
            }
        }

        "return 'true' for symbolic links to files on Windows".config(enabled = Os.isWindows) {
            ProcessCapture(tempDir, "cmd", "/c", "mklink", "symlink-to-file", "file")

            tempDir.resolve("symlink-to-file").let { symlinkToFile ->
                symlinkToFile shouldBe aFile()
                symlinkToFile.isSymbolicLink() shouldBe true
            }
        }

        "return 'true' for symbolic links to directories on Windows".config(enabled = Os.isWindows) {
            ProcessCapture(tempDir, "cmd", "/c", "mklink", "/d", "symlink-to-directory", "directory")

            tempDir.resolve("symlink-to-directory").let { symlinkToDirectory ->
                symlinkToDirectory.isDirectory shouldBe true
                symlinkToDirectory.isSymbolicLink() shouldBe true
            }
        }
    }

    "File.safeMkDirs()" should {
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

    "File.searchUpwardsForSubdirectory()" should {
        "find the root Git directory" {
            val gitRoot = File(".").searchUpwardsForSubdirectory(".git")

            gitRoot shouldNotBeNull {
                this shouldBe File("../..").absoluteFile.normalize()
            }
        }
    }

    "Map.zip()" should {
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

            map.zip(other, operation) shouldBe mapOf(
                "1" to 1,
                "2" to 2,
                "3" to 6,
                "4" to 4
            )
        }

        "not fail if this map is empty" {
            val other = mapOf("1" to 1)

            emptyMap<String, Int>().zip(other, operation) should containExactly("1" to 1)
        }

        "not fail if other map is empty" {
            val map = mapOf("1" to 1)

            map.zip(emptyMap(), operation) should containExactly("1" to 1)
        }

        "work for a sorted map with case-insensitive keys" {
            val map = sortedMapOf(String.CASE_INSENSITIVE_ORDER, "foo" to "bar")
            val other = mapOf("Foo" to "cafe")

            map.zip(other) { a, b ->
                a shouldBe "bar"
                b shouldBe "cafe"

                "resolved"
            }.apply {
                this should beInstanceOf<SortedMap<String, String>>()
                this should containExactly("Foo" to "resolved")
                this should haveKey("foo")
            }
        }
    }

    "Map.zipWithSets()" should {
        "correctly merge maps with set values" {
            val map = mapOf(
                "1" to setOf(1),
                "2" to setOf(2),
                "3" to setOf(3)
            )
            val other = mapOf(
                "3" to setOf(3),
                "4" to setOf(4)
            )

            val result = map.zipWithSets(other)
            result.values.forAll { it should beInstanceOf<Set<Int>>() }
            result shouldBe mapOf(
                "1" to setOf(1),
                "2" to setOf(2),
                "3" to setOf(3),
                "4" to setOf(4)
            )
        }
    }

    "String.collapseWhitespace()" should {
        "remove additional white spaces" {
            "String with additional   white spaces. ".collapseWhitespace() shouldBe
                "String with additional white spaces."
        }

        "remove newlines" {
            "String\nwith\n\nnewlines.".collapseWhitespace() shouldBe "String with newlines."
        }

        "remove indentations" {
            """
                String with indentation.
            """.collapseWhitespace() shouldBe "String with indentation."
        }
    }

    "String.isValidUri()" should {
        "return true for a valid URI" {
            "https://github.com/oss-review-toolkit/ort".isValidUri() shouldBe true
        }

        "return false for an invalid URI" {
            "https://github.com/oss-review-toolkit/ort, ".isValidUri() shouldBe false
        }
    }

    "String.percentEncode()" should {
        "encode characters according to RFC 3986" {
            val genDelims = listOf(':', '/', '?', '#', '[', ']', '@')
            val subDelims = listOf('!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=')
            val reserved = genDelims + subDelims

            val alpha = CharArray(26) { 'a' + it } + CharArray(26) { 'A' + it }
            val digit = CharArray(10) { '0' + it }
            val special = listOf('-', '.', '_', '~')
            val unreserved = alpha + digit + special

            " ".percentEncode() shouldBe "%20"

            reserved.forAll {
                val decoded = it.toString()

                val encoded = decoded.percentEncode()

                encoded shouldBe String.format(Locale.ROOT, "%%%02X", it.code)
                URLDecoder.decode(encoded, Charsets.UTF_8) shouldBe decoded
            }

            unreserved.asList().forAll {
                val decoded = it.toString()

                val encoded = decoded.percentEncode()

                encoded shouldBe decoded
                URLDecoder.decode(encoded, Charsets.UTF_8) shouldBe decoded
            }
        }
    }

    "String.replaceCredentialsInUri()" should {
        "strip the user name from a string representing a URL" {
            "ssh://bot@gerrit.host.com:29418/parent/project".replaceCredentialsInUri() shouldBe
                "ssh://gerrit.host.com:29418/parent/project"
        }

        "strip the user name and password from a string representing a URL" {
            "ssh://bot:pass@gerrit.host.com:29418/parent/project".replaceCredentialsInUri() shouldBe
                "ssh://gerrit.host.com:29418/parent/project"
        }

        "replace the user name from a string representing a URL" {
            "ssh://bot@gerrit.host.com:29418/parent/project".replaceCredentialsInUri("user") shouldBe
                "ssh://user@gerrit.host.com:29418/parent/project"
        }

        "replace the user name and password from a string representing a URL" {
            "ssh://bot:pass@gerrit.host.com:29418/parent/project".replaceCredentialsInUri("user:secret") shouldBe
                "ssh://user:secret@gerrit.host.com:29418/parent/project"
        }

        "not modify encodings in a URL" {
            "ssh://bot@gerrit.host.com:29418/parent/project%20with%20spaces".replaceCredentialsInUri() shouldBe
                "ssh://gerrit.host.com:29418/parent/project%20with%20spaces"
        }

        "not modify a string not representing a URL" {
            "This is not a URL".replaceCredentialsInUri() shouldBe "This is not a URL"
        }
    }

    "String.unquote()" should {
        "remove surrounding quotes" {
            "'single'".unquote() shouldBe "single"
            "\"double\"".unquote() shouldBe "double"
        }

        "remove nested quotes" {
            "'\"single-double\"'".unquote() shouldBe "single-double"
            "\"'double-single'\"".unquote() shouldBe "double-single"
        }

        "remove unmatched quotes" {
            "'single-unmatched".unquote() shouldBe "single-unmatched"
            "\"double-unmatched".unquote() shouldBe "double-unmatched"
            "'\"broken-nesting'\"".unquote() shouldBe "broken-nesting"
        }

        "remove whitespace by default" {
            "  '  \"  single-double  \"  '  ".unquote() shouldBe "single-double"
        }

        "keep whitespace optionally" {
            "  '  \"  single-double  \"  '  ".unquote(trimWhitespace = false) shouldBe "  '  \"  single-double  \"  '  "
            "'\"  single-double  \"'".unquote(trimWhitespace = false) shouldBe "  single-double  "
        }
    }

    "String.fileSystemEncode()" should {
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
            val tempDir = tempdir()
            val fileFromStr = tempDir.resolve(str.fileSystemEncode()).apply { writeText("dummy") }

            fileFromStr shouldBe aFile()
        }
    }

    "URI.getQueryParameters()" should {
        "return the query parameter for a simple query" {
            URI("https://oss-review-toolkit.org?key=value").getQueryParameters() shouldBe
                mapOf("key" to listOf("value"))
        }

        "work with multiple query parameters" {
            URI("https://oss-review-toolkit.org?key1=value1&key2=value2").getQueryParameters() shouldBe
                mapOf("key1" to listOf("value1"), "key2" to listOf("value2"))
        }

        "return query parameter with multiple values" {
            URI("https://oss-review-toolkit.org?key=value1,value2,value3").getQueryParameters() shouldBe
                mapOf("key" to listOf("value1", "value2", "value3"))

            URI("https://oss-review-toolkit.org?key=value1&key=value2").getQueryParameters() shouldBe
                mapOf("key" to listOf("value1", "value2"))
        }

        "work for URIs without query parameters" {
            URI("https://oss-review-toolkit.org").getQueryParameters() shouldBe emptyMap()
        }

        "work with empty values" {
            URI("https://oss-review-toolkit.org?key=").getQueryParameters() shouldBe mapOf("key" to listOf(""))
        }
    }

    "collapseToRanges()" should {
        "not modify a single value" {
            val lines = listOf(255)
            lines.collapseToRanges() should containExactlyCollection(255 to 255)
        }

        "collapse two elements in a single range" {
            val lines = listOf(255, 256)
            lines.collapseToRanges() should containExactlyCollection(255 to 256)
        }

        "collapse three elements in a single range" {
            val lines = listOf(255, 256, 257)
            lines.collapseToRanges() should containExactlyCollection(255 to 257)
        }

        "not include single element in a range" {
            val lines = listOf(255, 257, 258)
            lines.collapseToRanges() should containExactlyCollection(255 to 255, 257 to 258)
        }

        "collapse multiple ranges" {
            val lines = listOf(255, 256, 258, 259)
            lines.collapseToRanges() should containExactlyCollection(255 to 256, 258 to 259)
        }

        "collapse a mix of ranges and single values" {
            val lines = listOf(253, 255, 256, 258, 260, 261, 263)
            lines.collapseToRanges() should containExactlyCollection(
                253 to 253,
                255 to 256,
                258 to 258,
                260 to 261,
                263 to 263
            )
        }
    }
})
