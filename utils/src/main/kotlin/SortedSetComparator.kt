/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.utils

import java.util.SortedSet

/**
 * A [Comparator] for comparing [SortedSet]s containing [Comparable] elements. It works by comparing element by element
 * using the natural order of [T].
 */
class SortedSetComparator<T : Comparable<T>> : Comparator<SortedSet<T>> {
    override fun compare(o1: SortedSet<T>, o2: SortedSet<T>): Int {
        val iterator1 = o1.iterator()
        val iterator2 = o2.iterator()

        while (iterator1.hasNext() && iterator2.hasNext()) {
            val value1 = iterator1.next()
            val value2 = iterator2.next()

            value1.compareTo(value2).let {
                if (it != 0) return it
            }
        }

        return when {
            iterator1.hasNext() -> 1
            iterator2.hasNext() -> -1
            else -> 0
        }
    }
}
