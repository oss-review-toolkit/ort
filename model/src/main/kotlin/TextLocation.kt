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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.annotation.JsonIgnore

import java.io.File

import kotlin.math.abs
import kotlin.math.min

/**
 * A [TextLocation] references text located in a file.
 */
data class TextLocation(
    /**
     * The path (with invariant separators) of the file that contains the text.
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

        // This comparator is consistent with `equals()` as all properties are taken into account.
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

    /**
     * Indicate whether this TextLocation has known start and end lines.
     */
    @JsonIgnore
    val hasLineRange = startLine != UNKNOWN_LINE && endLine != UNKNOWN_LINE

    /**
     * Return a negative integer, zero, or a positive integer as this TextLocation comes before, is the same, or comes
     * after the [other] TextLocation.
     */
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

    /**
     * Return a [TextLocation] whose path is relative to [basePath], or throw an [IllegalArgumentException] if the paths
     * have different roots.
     */
    fun withRelativePath(basePath: File): TextLocation {
        val pathAsFile = File(path)

        val relativePath = when {
            !pathAsFile.isAbsolute -> pathAsFile
            basePath.isFile -> pathAsFile.relativeTo(basePath.parentFile)
            else -> pathAsFile.relativeTo(basePath)
        }

        return copy(path = relativePath.invariantSeparatorsPath)
    }
}
