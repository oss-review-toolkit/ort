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

package org.ossreviewtoolkit.model

import kotlin.math.abs
import kotlin.math.min

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
    val endLine: Int
) : Comparable<TextLocation> {
    companion object {
        const val UNKNOWN_LINE = -1
        private val COMPARATOR = compareBy<TextLocation>({ it.path }, { it.startLine }, { it.endLine })
    }

    init {
        require(path.isNotEmpty()) {
            "The path must not be empty."
        }

        require(startLine in 1..endLine || (startLine == UNKNOWN_LINE && endLine == UNKNOWN_LINE)) {
            "Invalid start or end line values."
        }
    }

    /**
     * A convenience constructor that sets [startLine] and [endLine] to the same [line].
     */
    constructor(path: String, line: Int) : this(path, line, line)

    override fun compareTo(other: TextLocation) = COMPARATOR.compare(this, other)

    /**
     * Return whether the given [line] is contained in the location.
     */
    operator fun contains(line: Int) = line != UNKNOWN_LINE && line in startLine..endLine

    /**
     * Return whether the given [other] location is contained in this location.
     */
    operator fun contains(other: TextLocation) = other.path == path && other.startLine in this && other.endLine in this

    /**
     * Return whether this and the [other] locations are overlapping, i.e. they share at least a single line. Note that
     * the [path] is not compared.
     */
    fun linesOverlapWith(other: TextLocation) = startLine in other || other.startLine in this

    /**
     * Return the minimum distance between this and the [other] location. A distance of 0 means that the locations are
     * overlapping.
     */
    fun distanceTo(other: TextLocation) =
        when {
            linesOverlapWith(other) -> 0
            else -> min(abs(other.startLine - endLine), abs(startLine - other.endLine))
        }
}
