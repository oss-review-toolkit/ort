/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import com.fasterxml.jackson.annotation.JsonInclude

data class Includes(
    /**
     * Path includes.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val paths: List<PathInclude> = emptyList()
) {
    companion object {
        /**
         * A constant for an [Includes] instance that does not contain any includes: if no includes are defined,
         * everything is included.
         */
        @JvmField
        val EMPTY = Includes()
    }

    /**
     * True if any [path include][paths] matches [path] or if there is no path includes.
     */
    fun isPathIncluded(path: String) = paths.isEmpty() || paths.any { it.matches(path) }
}
