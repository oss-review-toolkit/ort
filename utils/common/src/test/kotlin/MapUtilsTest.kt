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
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.maps.containExactly
import io.kotest.matchers.maps.haveKey
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf

import java.util.SortedMap

class MapUtilsTest : WordSpec({
    "getConflictingKeys()" should {
        "return an empty set for equals maps" {
            val foo = mapOf("a" to "b")
            val bar = mapOf("a" to "b")

            foo.getConflictingKeys(foo) should beEmpty()
            foo.getConflictingKeys(bar) should beEmpty()
        }

        "return only keys with different values" {
            val foo = mapOf("a" to "a", "b" to "b")
            val bar = mapOf("a" to "a", "b" to "c")

            foo.getConflictingKeys(bar) should containExactly("b")
        }
    }

    "zip()" should {
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

    "zipWithSets()" should {
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
})
