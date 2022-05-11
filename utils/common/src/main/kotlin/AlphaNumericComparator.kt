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

/**
 * A [Comparator] that sorts [String]s which contain numbers in a human-readable way, i.e. "foo10" would come after
 * "foo6".
 */
object AlphaNumericComparator : Comparator<String> {
    private val numberOrNotRegex = Regex("\\d+|\\D+")

    override fun compare(o1: String?, o2: String?): Int {
        if (o1 == null || o2 == null) throw NullPointerException("Comparison arguments must not be null.")

        // Split the strings into chunks that each contain either only digits or non-digits.
        val p1 = numberOrNotRegex.findAll(o1).map { it.value }
        val p2 = numberOrNotRegex.findAll(o2).map { it.value }

        val i1 = p1.iterator()
        val i2 = p2.iterator()

        // Iterate over both sequences, comparing the elements either numerically or lexicographically.
        while (true) {
            val e1 = i1.nextOrNull()
            val e2 = i2.nextOrNull()

            // If both iterators are at the end at this point, all elements are considered equal. Fall back to string
            // comparison to get stable sorting if the only difference are spaces or leading zeros.
            if (e1 == null && e2 == null) return o1.compareTo(o2)

            // Shorter strings should go before longer strings.
            if (e1 == null) return -1
            if (e2 == null) return 1

            val n1 = e1.toIntOrNull()
            val n2 = e2.toIntOrNull()

            val c = if (n1 != null && n2 != null) {
                // This comparison implicitly ignores leading zeros in the elements.
                n1.compareTo(n2)
            } else {
                // Disregard whitespace in the comparison.
                e1.filterNot { it.isWhitespace() }.compareTo(e2.filterNot { it.isWhitespace() })
            }

            if (c != 0) return c
        }
    }
}
