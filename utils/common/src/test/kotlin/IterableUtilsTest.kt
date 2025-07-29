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
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

class IterableUtilsTest : WordSpec({
    "collapseToRanges()" should {
        "not modify a single value" {
            val lines = listOf(255)
            lines.collapseToRanges() should containExactly(255 to 255)
        }

        "collapse two elements in a single range" {
            val lines = listOf(255, 256)
            lines.collapseToRanges() should containExactly(255 to 256)
        }

        "collapse three elements in a single range" {
            val lines = listOf(255, 256, 257)
            lines.collapseToRanges() should containExactly(255 to 257)
        }

        "not include single element in a range" {
            val lines = listOf(255, 257, 258)
            lines.collapseToRanges() should containExactly(255 to 255, 257 to 258)
        }

        "collapse multiple ranges" {
            val lines = listOf(255, 256, 258, 259)
            lines.collapseToRanges() should containExactly(255 to 256, 258 to 259)
        }

        "collapse a mix of ranges and single values" {
            val lines = listOf(253, 255, 256, 258, 260, 261, 263)
            lines.collapseToRanges() should containExactly(
                253 to 253,
                255 to 256,
                258 to 258,
                260 to 261,
                263 to 263
            )
        }
    }

    "getDuplicates()" should {
        "return no duplicates if there are none" {
            emptyList<String>().getDuplicates() should beEmpty()
            listOf("no", "dupes", "in", "here").getDuplicates() should beEmpty()
        }

        "return all duplicates" {
            val strings = listOf("foo", "bar", "baz", "foo", "bar", "bar")

            strings.getDuplicates() should containExactlyInAnyOrder("foo", "bar")
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

            val strings = setOf(
                "aaa",
                "bbb",
                "cc"
            )

            strings.getDuplicates { it.length } shouldBe mapOf(
                3 to listOf("aaa", "bbb")
            )
        }
    }
})
