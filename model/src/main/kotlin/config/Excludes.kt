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

import com.fasterxml.jackson.annotation.JsonInclude

import com.here.ort.model.Project

import java.util.SortedSet

/**
 * Defines which parts of a repository should be excluded.
 */
data class Excludes(
        /**
         * Project specific excludes.
         */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        val projects: List<ProjectExclude> = emptyList(),

        /**
         * Scopes that will be excluded from all projects.
         */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        val scopes: List<ScopeExclude> = emptyList()
) {
    fun isExcluded(project: Project) = projects.any { it.path == project.definitionFilePath && it.exclude }

    fun excludedScopes(project: Project): SortedSet<String> {
        val projectExclude = projects.find { it.path == project.definitionFilePath }
        val scopesExcludedFromProject = projectExclude?.scopes?.map { it.name } ?: emptyList()
        val scopesExcludedGlobally = scopes.map { it.name }

        return (scopesExcludedGlobally + scopesExcludedFromProject).toSortedSet()
    }
}
