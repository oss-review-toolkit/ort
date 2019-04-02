/*
 * Copyright (c) 2017-2019 HERE Europe B.V.
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
import com.here.ort.utils.hash

/**
 * A [TextLocation] references text located in a file.
 */
data class TextLocation(
    /**
     * The path of the file that contains the text.
     */
    val path: String,

    /**
     * The line the text is starting at.
     */
    val startLine: Int,

    /**
     * The line the text is ending at.
     */
    val endLine: Int,

    /**
     * The text at the specified location. Can be null if the text is unknown.
     */
    val text: String? = null
) : Comparable<TextLocation> {
    companion object {
        private val COMPARATOR = compareBy<TextLocation>({ it.path }, { it.startLine }, { it.endLine })
        val SORTED_SET_COMPARATOR = SortedSetComparator<TextLocation>()
        val TREE_SET_TYPE by lazy { jsonMapper.typeFactory.constructTreeSetType(TextLocation::class.java) }
    }

    /**
     * A hash built from [startLine], [endLine], and [text]. Can be used to reference this text location.
     */
    val hash = "$startLine.$endLine.$text".hash()

    override fun compareTo(other: TextLocation) = COMPARATOR.compare(this, other)
}
