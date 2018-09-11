/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

package com.here.ort.model.config

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Defines which parts of a project should be excluded. The project is defined by the definition file located at [path]
 * inside the repository. The project exclude either requires a [reason] and [comment] to exclude the whole project, or
 * a list of [scopes] to exclude only specific scopes.
 */
data class ProjectExclude(
        /**
         * The path of the project definition file, relative to the root of the repository.
         */
        val path: String,

        /**
         * The reason why the project is excluded, out of a predefined choice.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val reason: ProjectExcludeReason?,

        /**
         * A comment to further explain why the [reason] is applicable here.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val comment: String?,

        /**
         * Scopes that will be excluded from this project.
         */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        val scopes: List<ScopeExclude> = emptyList()
) {
    /**
     * True if the whole project will be excluded. This is the case if no specific scopes to exclude are defined.
     */
    @JsonIgnore
    val exclude = scopes.isEmpty()
}
