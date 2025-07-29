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

/**
 * Collapse consecutive values to a list of pairs that each denote a range. A single value is represented as a
 * range whose first and last elements are equal.
 */
fun Iterable<Int>.collapseToRanges(): List<Pair<Int, Int>> {
    if (!iterator().hasNext()) return emptyList()

    val ranges = mutableListOf<Pair<Int, Int>>()

    val sortedValues = toSortedSet()
    val rangeBreaks = sortedValues.zipWithNext { a, b -> (a to b).takeIf { b != a + 1 } }.filterNotNull()

    var current = sortedValues.first()

    rangeBreaks.mapTo(ranges) { (last, first) ->
        (current to last).also { current = first }
    }

    ranges += current to sortedValues.last()

    return ranges
}

/**
 * Return a map that associates duplicates as identified by [keySelector] with belonging lists of entries.
 */
fun <T, K> Iterable<T>.getDuplicates(keySelector: (T) -> K): Map<K, List<T>> =
    groupBy(keySelector).filter { it.value.size > 1 }

/**
 * Return a set of duplicate entries in this [Iterable].
 */
fun <T> Iterable<T>.getDuplicates(): Set<T> = if (this is Set) emptySet() else getDuplicates { it }.keys

/**
 * Return the next value in the iteration, or null if there is no next value.
 */
fun <T> Iterator<T>.nextOrNull() = if (hasNext()) next() else null

/**
 * Return a string of common-separated ranges as denoted by the list of pairs.
 */
fun Iterable<Pair<Int, Int>>.prettyPrintRanges(): String =
    joinToString { (startValue, endValue) ->
        if (startValue == endValue) startValue.toString() else "$startValue-$endValue"
    }
