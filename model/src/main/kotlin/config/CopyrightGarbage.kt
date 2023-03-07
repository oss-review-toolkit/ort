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

package org.ossreviewtoolkit.model.config

/**
 * A class that defines which Copyright statements are to be considered as garbage instead of real findings.
 */
data class CopyrightGarbage(
    /**
     * A set of literal strings that identify garbage Copyright findings.
     */
    val items: Set<String> = emptySet(),

    /**
     * A set of [Regex] patterns that identify garbage Copyright findings.
     */
    val patterns: Set<String> = emptySet()
) {
    private val regexes by lazy { patterns.map { it.toRegex() } }

    /**
     * Return whether the [statement] is garbage.
     */
    operator fun contains(statement: String): Boolean =
        statement in items || regexes.any { it.matches(statement) }
}

fun CopyrightGarbage?.orEmpty(): CopyrightGarbage = this ?: CopyrightGarbage()
