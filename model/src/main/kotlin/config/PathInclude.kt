/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.ossreviewtoolkit.utils.common.FileMatcher

/**
 * Defines paths which should be included. Each file or directory that is matched by the [glob][pattern] is marked as
 * included. If a project definition file is matched by the [pattern], the whole project is included. For details about
 * the glob syntax see the [FileMatcher] implementation.
 */
data class PathInclude(
    /**
     * A glob to match the path of the project definition file, relative to the root of the repository.
     */
    val pattern: String,

    /**
     * The reason why the project is included, out of a predefined choice.
     */
    val reason: PathIncludeReason,

    /**
     * A comment to further explain why the [reason] is applicable here.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val comment: String = ""
) {
    /**
     * Return true if and only if this [PathInclude] matches the given [path].
     */
    fun matches(path: String) =
        FileMatcher.matches(
            pattern = pattern.removePrefix("./"),
            path = path
        )
}
