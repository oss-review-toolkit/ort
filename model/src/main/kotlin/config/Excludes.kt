/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import com.here.ort.model.AnalyzerResult
import com.here.ort.model.Identifier
import com.here.ort.model.Project
import com.here.ort.model.Scope

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
    /**
     * Return the [ProjectExclude] for the provided [project], or null if there is none.
     */
    fun findProjectExclude(project: Project) = projects.find { it.matches(project.definitionFilePath) }

    /**
     * Return the [ScopeExclude]s for the provided [scope]. This includes global excludes from [scopes] as well as
     * [project] specific excludes from [projects].
     */
    fun findScopeExcludes(scope: Scope, project: Project) =
            scopes.filter { it.matches(scope.name) } +
                    (findProjectExclude(project)?.scopes?.filter { it.matches(scope.name) } ?: emptyList())

    /**
     * Checks if all occurrences of an [id] are excluded. If the [id] references a package it checks if this package is
     * excluded. If the [id] references a [Project] it checks if the [Project] is excluded and if the [id] does not
     * appear as a dependency of another non-excluded [Project].
     */
    fun isExcluded(id: Identifier, analyzerResult: AnalyzerResult) =
            analyzerResult.projects.find { it.id == id }?.let {
                // An excluded project could still be included as a dependency of another non-excluded project.
                isProjectExcluded(it) && isPackageExcluded(id, analyzerResult)
            } ?: isPackageExcluded(id, analyzerResult)

    /**
     * True if all occurrences of the package identified by [id] in the [analyzerResult] are excluded by this [Excludes]
     * configuration.
     */
    fun isPackageExcluded(id: Identifier, analyzerResult: AnalyzerResult) =
            analyzerResult.projects.all { project ->
                isProjectExcluded(project) || project.scopes.all { scope ->
                    isScopeExcluded(scope, project) || !scope.contains(id)
                }
            }

    /**
     * True if the [project] is excluded by this [Excludes] configuration.
     */
    fun isProjectExcluded(project: Project) =
            projects.any {
                it.matches(project.definitionFilePath) && it.isWholeProjectExcluded
            }

    /**
     * True if the [scope] is excluded in [project] by this [Excludes] configuration.
     */
    fun isScopeExcluded(scope: Scope, project: Project) =
            scopes.any { it.matches(scope.name) } ||
                    findProjectExclude(project)?.scopes?.any { it.matches(scope.name) } ?: false

    /**
     * Map the project excludes by the identifiers of the provided [projects].
     */
    fun projectExcludesById(projects: Set<Project>) = projects.associate { Pair(it.id, findProjectExclude(it)) }

    /**
     * Map the scope excludes for [project] by the names of the provided [scopes].
     */
    fun scopeExcludesByName(project: Project, scopes: Collection<Scope>) =
            scopes.associate {
                Pair(it.name, findScopeExcludes(it, project))
            }
}
