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

import java.util.SortedMap

/**
 * Return the set of keys that have different values in this and the [other] map.
 */
fun <K, V> Map<K, V>.getConflictingKeys(other: Map<K, V>): Set<K> {
    if (this == other) return emptySet()
    return keys.intersect(other.keys).filterTo(mutableSetOf()) { this[it] != other[it] }
}

/**
 * Merge two maps by iterating over the combined key set of both maps and calling [operation] with any conflicting
 * values for the same key. In case of a [SortedMap] the iteration order is maintained.
 */
fun <K, V> Map<K, V>.zip(other: Map<K, V>, operation: (V, V) -> V): Map<K, V> {
    val combinedKeys = if (this is SortedMap) {
        // Create a copy of the view of other keys to add keys to. It is important that "other.keys" comes first so that
        // its key names are exactly maintained for the lookup in "other" as part of "assocateWith" to work.
        other.keys.toSortedSet(comparator()).apply { addAll(keys) }
    } else {
        keys + other.keys
    }

    val target = if (this is SortedMap) sortedMapOf(comparator()) else mutableMapOf<K, V>()
    return combinedKeys.associateWithTo(target) { key ->
        val a = this[key]
        val b = other[key]

        when {
            a != null && b != null -> operation(a, b)
            a != null -> a
            b != null -> b
            else -> error("Either map must have a value for the key '$key'.")
        }
    }
}

/**
 * Merge two maps which have sets as values by creating the combined key set of both maps and merging the sets. If there
 * is no entry for a key in one of the maps, the value from the other map is used.
 */
fun <K, V : Set<T>, T> Map<K, V>.zipWithSets(other: Map<K, V>): Map<K, V> =
    zip(other) { a, b ->
        @Suppress("UNCHECKED_CAST")
        (a + b) as V
    }
