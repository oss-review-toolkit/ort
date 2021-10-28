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

package org.ossreviewtoolkit.utils.common

import org.springframework.util.AntPathMatcher

/**
 * A class to determine whether a path is matched by any of the given globs.
 */
class FileMatcher(
    /**
     * The collection of [glob patterns][1] to consider for matching.
     *
     * [1]: https://docs.oracle.com/javase/tutorial/essential/io/fileOps.html#glob
     */
    val patterns: Collection<String>,

    /**
     * Toggle the case-sensitivity of the matching.
     */
    ignoreCase: Boolean = false
) {
    constructor(vararg patterns: String, ignoreCase: Boolean = false) : this(patterns.asList(), ignoreCase)

    private val matcher = AntPathMatcher().apply {
        setCaseSensitive(!ignoreCase)
    }

    /**
     * Return true if and only if the given [path] is matched by any of the file globs passed to the
     * constructor. The [path] must use '/' as separators, if it contains any.
     */
    fun matches(path: String): Boolean = patterns.any { pattern -> matcher.match(pattern, path) }
}
