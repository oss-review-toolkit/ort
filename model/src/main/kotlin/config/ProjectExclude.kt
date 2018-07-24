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
 * inside the repository. If [packages], [scopes], and [errors] are all empty the whole project is excluded.
 */
data class ProjectExclude(
        val path: String,
        val type: ProjectType,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val reason: ExcludeReason?,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val comment: String?,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        val packages: List<PackageExclude> = emptyList(),
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        val scopes: List<ScopeExclude> = emptyList(),
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        val errors: List<ErrorExclude> = emptyList()
) {
    @JsonIgnore
    val exclude = packages.isEmpty() && scopes.isEmpty() && errors.isEmpty()
}
