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

package com.here.ort.model

import com.here.ort.utils.SortedSetComparator
import com.here.ort.utils.constructTreeSetType

import java.util.SortedSet

data class CopyrightFindings(
    val statement: String,
    val locations: SortedSet<TextLocation>
) : Comparable<CopyrightFindings> {
    companion object {
        val SORTED_SET_COMPARATOR = SortedSetComparator<CopyrightFindings>()
        val TREE_SET_TYPE by lazy { jsonMapper.typeFactory.constructTreeSetType(CopyrightFindings::class.java) }
    }

    override fun compareTo(other: CopyrightFindings) =
        compareValuesBy(
            this,
            other,
            compareBy(CopyrightFindings::statement)
                .thenBy(TextLocation.SORTED_SET_COMPARATOR, CopyrightFindings::locations)
        ) { it }
}
