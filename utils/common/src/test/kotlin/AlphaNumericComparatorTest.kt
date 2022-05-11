/*
 * Copyright (C) 2022 Bosch.IO GmbH
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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AlphaNumericComparatorTest : StringSpec({
    "Dates should be sorted correctly".config(invocations = 5) {
        val sorted = listOf(
            "1999-3-3",
            "1999-12-25",
            "2000-1-2",
            "2000-1-10",
            "2000-3-23"
        )
        val unsorted = sorted.shuffled()

        unsorted.sortedWith(AlphaNumericComparator) shouldBe sorted
    }

    "Fractions should be sorted correctly".config(invocations = 5) {
        val sorted = listOf(
            "1.002.01",
            "1.002.03",
            "1.002.08",
            "1.009.02",
            "1.009.10",
            "1.009.20",
            "1.010.12",
            "1.011.02"
        )
        val unsorted = sorted.shuffled()

        unsorted.sortedWith(AlphaNumericComparator) shouldBe sorted
    }

    "Versions should be sorted correctly".config(invocations = 5) {
        val sorted = listOf(
            "1.001",
            "1.1",
            "1.002",
            "1.02",
            "1.2",
            "1.09",
            "1.010",
            "1.10",
            "1.101",
            "1.102",
            "1.198",
            "1.199",
            "1.200"
        )
        val unsorted = sorted.shuffled()

        unsorted.sortedWith(AlphaNumericComparator) shouldBe sorted
    }

    "Words should be sorted correctly".config(invocations = 5) {
        val sorted = listOf(
            "1-02",
            "1-2",
            "1-20",
            "10-20",
            "fred",
            "jane",
            "pic01",
            "pic02",
            "pic2",
            "pic02a",
            "pic3",
            "pic4",
            "pic 4 else",
            "pic 5",
            "pic 5",
            "pic05",
            "pic 5 something",
            "pic 6",
            "pic   7",
            "pic100",
            "pic100a",
            "pic120",
            "pic121",
            "pic02000",
            "tom",
            "x2-g8",
            "x2-y7",
            "x2-y08",
            "x8-y8"
        )
        val unsorted = sorted.shuffled()

        unsorted.sortedWith(AlphaNumericComparator) shouldBe sorted
    }
})
