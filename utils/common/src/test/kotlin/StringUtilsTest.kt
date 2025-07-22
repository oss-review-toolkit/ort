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
import io.kotest.engine.spec.tempdir
import io.kotest.inspectors.forAll
import io.kotest.matchers.file.aFile
import io.kotest.matchers.shouldBe

import java.net.URLDecoder
import java.util.Locale

class StringUtilsTest : WordSpec({
    "collapseWhitespace()" should {
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

    "fileSystemEncode()" should {
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

    "isValidUri()" should {
        "return true for a valid URI" {
            "https://github.com/oss-review-toolkit/ort".isValidUri() shouldBe true
        }

        "return false for an invalid URI" {
            "https://github.com/oss-review-toolkit/ort, ".isValidUri() shouldBe false
        }
    }

    "percentEncode()" should {
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

    "replaceCredentialsInUri()" should {
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

    "unquote()" should {
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
})
