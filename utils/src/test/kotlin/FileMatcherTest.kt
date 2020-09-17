/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe

private val COMMONLY_USED_LICENSE_FILE_NAMES = listOf(
    "copying",
    "copyright",
    "licence",
    "licence.extension",
    "licencesuffix",
    "license",
    "license.extension",
    "licensesuffix",
    "filename.license",
    "patents",
    "readme",
    "readme.extension",
    "readmesuffix",
    "unlicence",
    "unlicense"
)

class FileMatcherTest : WordSpec({
    val defaultMatcher = FileMatcher.LICENSE_FILE_MATCHER

    "default license file matcher" should {
        "match commonly used license file paths in upper-case" {
            COMMONLY_USED_LICENSE_FILE_NAMES.map { it.toUpperCase() }.forAll {
                defaultMatcher.matches(it) shouldBe true
            }
        }

        "match commonly used license file paths in lower-case" {
            COMMONLY_USED_LICENSE_FILE_NAMES.map { it.toLowerCase() }.forAll {
                defaultMatcher.matches(it) shouldBe true
            }
        }

        "match commonly used license file paths in capital (case)" {
            COMMONLY_USED_LICENSE_FILE_NAMES.map { it.capitalize() }.forAll {
                defaultMatcher.matches(it) shouldBe true
            }
        }
    }

    "matches" should {
        "match the given patterns" {
            val matcher = FileMatcher("a/LICENSE", "b/LICENSE")

            with(matcher) {
                matches("a/LICENSE") shouldBe true
                matches("b/LICENSE") shouldBe true
                matches("c/LICENSE") shouldBe false
            }
        }

        "adhere to the case-sensitivity" {
            FileMatcher("LICENSE", caseSensitive = true).apply {
                matches("LICENSE") shouldBe true
                matches("license") shouldBe false
            }

            FileMatcher("LICENSE", caseSensitive = false).apply {
                matches("LICENSE") shouldBe true
                matches("license") shouldBe true
            }
        }
    }
})
