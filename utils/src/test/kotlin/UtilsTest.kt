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
import io.kotlintest.specs.WordSpec

import java.io.File
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
                            to "ssh://git@github.com/heremaps/oss-review-toolkit.git"
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
    }
})
