/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import io.kotlintest.specs.WordSpec

import java.io.File
import java.io.IOException
import java.nio.file.Paths

class UtilsTest : WordSpec({
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

        "be reversible by String.urldecode" {
            str.fileSystemEncode().fileSystemDecode() shouldBe str
        }
    }

    "filterVersionNames" should {
        "return an empty list for a blank version" {
            val names = listOf("dummy")

            filterVersionNames("", names) shouldBe emptyList<String>()
            filterVersionNames(" ", names) shouldBe emptyList<String>()
        }

        "find names separated by underscores" {
            val names = listOf(
                    "A02",
                    "A03",
                    "A04",
                    "DEV0_9_3",
                    "DEV0_9_4",
                    "DEV_0_9_7",
                    "Exoffice",
                    "PROD_1_0_1",
                    "PROD_1_0_2",
                    "PROD_1_0_3",
                    "before_SourceForge",
                    "before_debug_changes"
            )

            filterVersionNames("1.0.3", names).joinToString("\n") shouldBe "PROD_1_0_3"
            filterVersionNames("1", names).joinToString("\n") shouldBe ""
            filterVersionNames("0.3", names).joinToString("\n") shouldBe ""
        }

        "find names separated by dots" {
            val names = listOf(
                    "0.10.0",
                    "0.10.1",
                    "0.5.0",
                    "0.6.0",
                    "0.7.0",
                    "0.8.0",
                    "0.9.0"
            )

            filterVersionNames("0.10.0", names).joinToString("\n") shouldBe "0.10.0"
            filterVersionNames("0.10", names).joinToString("\n") shouldBe ""
            filterVersionNames("10.0", names).joinToString("\n") shouldBe ""
            filterVersionNames("1", names).joinToString("\n") shouldBe ""
        }

        "find names with mixed separators" {
            val names = listOf(
                    "docutils-0.10",
                    "docutils-0.11",
                    "docutils-0.12",
                    "docutils-0.13.1",
                    "docutils-0.14",
                    "docutils-0.14.0a",
                    "docutils-0.14a0",
                    "docutils-0.14rc1",
                    "docutils-0.14rc2",
                    "docutils-0.3.7",
                    "docutils-0.3.9",
                    "docutils-0.4",
                    "docutils-0.5",
                    "docutils-0.6",
                    "docutils-0.7",
                    "docutils-0.8",
                    "docutils-0.8.1",
                    "docutils-0.9",
                    "docutils-0.9.1",
                    "initial",
                    "merged_to_nesting",
                    "prest-0.3.10",
                    "prest-0.3.11",
                    "start"
            )

            filterVersionNames("0.3.9", names).joinToString("\n") shouldBe "docutils-0.3.9"
            filterVersionNames("0.14", names).joinToString("\n") shouldBe "docutils-0.14"
            filterVersionNames("0.3.10", names).joinToString("\n") shouldBe "prest-0.3.10"
            filterVersionNames("0.13", names).joinToString("\n") shouldBe ""
            filterVersionNames("13.1", names).joinToString("\n") shouldBe ""
            filterVersionNames("1.0", names).joinToString("\n") shouldBe ""
        }

        "find names with a 'v' prefix" {
            val names = listOf(
                    "v6.2.0",
                    "v6.2.1",
                    "v6.2.2",
                    "v6.20.0",
                    "v6.20.1",
                    "v6.20.2",
                    "v6.20.3",
                    "v6.21.0",
                    "v6.21.1",
                    "v6.22.0",
                    "v6.22.1",
                    "v6.22.2",
                    "v6.23.0",
                    "v6.23.1",
                    "v6.24.0",
                    "v6.24.1",
                    "v6.25.0",
                    "v6.26.0",
                    "v7.0.0-beta.0",
                    "v7.0.0-beta.1",
                    "v7.0.0-beta.2",
                    "v7.0.0-beta.3"
            )

            filterVersionNames("6.26.0", names).joinToString("\n") shouldBe "v6.26.0"
            filterVersionNames("7.0.0-beta.2", names).joinToString("\n") shouldBe "v7.0.0-beta.2"
            filterVersionNames("7.0.0", names).joinToString("\n") shouldBe ""
            filterVersionNames("2.0", names).joinToString("\n") shouldBe ""
        }

        "find names with the project name as the prefix" {
            val names = listOf(
                    "babel-plugin-transform-member-expression-literals@6.9.0",
                    "babel-plugin-transform-simplify-comparison-operators@6.9.0",
                    "babel-plugin-transform-property-literals@6.9.0"
            )

            filterVersionNames("6.9.0", names, "babel-plugin-transform-simplify-comparison-operators")
                    .joinToString("\n") shouldBe "babel-plugin-transform-simplify-comparison-operators@6.9.0"
        }
    }

    "normalizeVcsUrl" should {
        "do nothing for empty URLs" {
            normalizeVcsUrl("") shouldBe ""
        }

        "handle anonymous Git / HTTPS URL schemes" {
            val packages = mapOf(
                    "git://github.com/cheeriojs/cheerio.git"
                            to "https://github.com/cheeriojs/cheerio.git",
                    "git+https://github.com/fb55/boolbase.git"
                            to "https://github.com/fb55/boolbase.git",
                    "https://www.github.com/DefinitelyTyped/DefinitelyTyped.git"
                            to "https://github.com/DefinitelyTyped/DefinitelyTyped.git"
            )

            packages.forEach { actualUrl, expectedUrl ->
                normalizeVcsUrl(actualUrl) shouldBe expectedUrl
            }
        }

        "handle authenticated SSH URL schemes" {
            val packages = mapOf(
                    "git+ssh://git@github.com/logicalparadox/idris.git"
                            to "ssh://git@github.com/logicalparadox/idris.git",
                    "git@github.com:heremaps/oss-review-toolkit.git"
                            to "ssh://git@github.com/heremaps/oss-review-toolkit.git",
                    "ssh://user@gerrit.server.com:29418/parent/project"
                            to "ssh://user@gerrit.server.com:29418/parent/project"
            )

            packages.forEach { actualUrl, expectedUrl ->
                normalizeVcsUrl(actualUrl) shouldBe expectedUrl
            }
        }

        "add missing .git for GitHub URLs" {
            val packages = mapOf(
                    "https://github.com/fb55/nth-check"
                            to "https://github.com/fb55/nth-check.git",
                    "git://github.com/isaacs/inherits"
                            to "https://github.com/isaacs/inherits.git"
            )

            packages.forEach { actualUrl, expectedUrl ->
                normalizeVcsUrl(actualUrl) shouldBe expectedUrl
            }
        }

        "handle a trailing slash correctly" {
            val packages = mapOf(
                    "https://github.com/kilian/electron-to-chromium/"
                            to "https://github.com/kilian/electron-to-chromium.git",
                    "git://github.com/isaacs/inherits.git/"
                            to "https://github.com/isaacs/inherits.git"
            )

            packages.forEach { actualUrl, expectedUrl ->
                normalizeVcsUrl(actualUrl) shouldBe expectedUrl
            }
        }

        "handle file schemes correctly" {
            var userDir = System.getProperty("user.dir").replace("\\", "/")
            var userRoot = Paths.get(userDir).root.toString().replace("\\", "/")

            if (!userDir.startsWith("/")) {
                userDir = "/" + userDir
            }

            if (!userRoot.startsWith("/")) {
                userRoot = "/" + userRoot
            }

            val packages = mapOf(
                    "relative/path/to/local/file"
                            to "file://$userDir/relative/path/to/local/file",
                    "relative/path/to/local/dir/"
                            to "file://$userDir/relative/path/to/local/dir",
                    "/absolute/path/to/local/file"
                            to "file://${userRoot}absolute/path/to/local/file",
                    "/absolute/path/to/local/dir/"
                            to "file://${userRoot}absolute/path/to/local/dir"
            )

            packages.forEach { actualUrl, expectedUrl ->
                normalizeVcsUrl(actualUrl) shouldBe expectedUrl
            }
        }

        "convert git to https for GitHub URLs" {
            val packages = mapOf(
                    "git://github.com:ccavanaugh/jgnash.git"
                            to "https://github.com/ccavanaugh/jgnash.git",
                    "git://github.com/netty/netty.git/netty-buffer"
                            to "https://github.com/netty/netty.git/netty-buffer"
            )

            packages.forEach { actualUrl, expectedUrl ->
                normalizeVcsUrl(actualUrl) shouldBe expectedUrl
            }
        }

        "fixup crazy URLs" {
            val packages = mapOf(
                    "git@github.com/cisco/node-jose.git"
                            to "ssh://git@github.com/cisco/node-jose.git",
                    "https://git@github.com:hacksparrow/node-easyimage.git"
                            to "https://github.com/hacksparrow/node-easyimage.git"
            )

            packages.forEach { actualUrl, expectedUrl ->
                normalizeVcsUrl(actualUrl) shouldBe expectedUrl
            }
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
})
