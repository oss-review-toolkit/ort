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

import com.here.ort.model.Identifier
import com.here.ort.model.OrtResult
import com.here.ort.model.Project
import com.here.ort.model.Scope

/**
 * Defines which parts of a repository should be excluded.
 */
data class Excludes(
    /**
     * Path excludes.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val paths: List<PathExclude> = emptyList(),

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
     * Return the [PathExclude]s matching the provided [path].
     */
    fun findPathExcludes(path: String) = paths.filter { it.matches(path) }

    /**
     * Return the [PathExclude]s matching the [definitionFilePath][Project.definitionFilePath].
     */
    fun findPathExcludes(project: Project, ortResult: OrtResult) =
        paths.filter { it.matches(ortResult.getDefinitionFilePathRelativeToAnalyzerRoot(project)) }

    /**
     * Return all [ProjectExclude]s for the provided [project].
     */
    fun findProjectExcludes(project: Project, ortResult: OrtResult): List<ProjectExclude> {
        val projectPath = ortResult.getDefinitionFilePathRelativeToAnalyzerRoot(project)
        return projects.filter { it.matches(projectPath) }
    }

    /**
     * Return the [ScopeExclude]s for the provided [scope]. This includes global excludes from [scopes] as well as
     * [project] specific excludes from [projects].
     */
    fun findScopeExcludes(scope: Scope, project: Project, ortResult: OrtResult): List<ScopeExclude> {
        val globalScopeExcludes = scopes.filter { it.matches(scope.name) }
        val projectScopeExcludes = findProjectExcludes(project, ortResult)
            .map { it.scopes }
            .flatten()
            .filter { it.matches(scope.name) }

        return globalScopeExcludes + projectScopeExcludes
    }

    /**
     * True if any [path exclude][paths] matches [path].
     */
    fun isPathExcluded(path: String) = paths.any { it.matches(path) }

    /**
     * True if the [scope] is excluded in [project] by this [Excludes] configuration.
     */
    fun isScopeExcluded(scope: Scope, project: Project, ortResult: OrtResult) =
        findScopeExcludes(scope, project, ortResult).isNotEmpty()

    /**
     * Map the project excludes by the identifiers of the provided [projects].
     */
    fun projectExcludesById(projects: Set<Project>, ortResult: OrtResult) =
        projects.associate { Pair(it.id, findProjectExcludes(it, ortResult).singleOrNull()) }

    /**
     * Map the scope excludes for [project] by the names of the provided [scopes].
     */
    fun scopeExcludesByName(project: Project, scopes: Collection<Scope>, ortResult: OrtResult) =
        scopes.associate {
            Pair(it.name, findScopeExcludes(it, project, ortResult))
        }
}
