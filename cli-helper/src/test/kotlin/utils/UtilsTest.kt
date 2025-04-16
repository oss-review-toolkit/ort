/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.helper.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.should

class UtilsTest : WordSpec({
    "greedySetCover()" should {
        "prefer the set which covers more items" {
            val sets = mapOf(
                "a" to setOf(1),
                "b" to setOf(2),
                "c" to setOf(1, 2)
            )

            greedySetCover(sets) should containExactly("c")
        }

        "resolve a tie using the given comparator" {
            val sets = mapOf(
                "aa" to setOf(1),
                "b" to setOf(1)
            )

            greedySetCover(sets) { a, b ->
                a.length - b.length
            } should containExactly("aa")

            greedySetCover(sets) { a, b ->
                b.length - a.length
            } should containExactly("b")
        }
    }
})
