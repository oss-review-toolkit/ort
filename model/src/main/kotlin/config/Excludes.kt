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

package org.ossreviewtoolkit.model.config

import com.fasterxml.jackson.annotation.JsonInclude

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Project

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
    fun findPathExcludes(project: Project, ortResult: OrtResult): List<PathExclude> {
        val definitionFilePath = ortResult.getDefinitionFilePathRelativeToAnalyzerRoot(project)
        return paths.filter { it.matches(definitionFilePath) }
    }

    /**
     * Return the [ScopeExclude]s for the provided [scopeName].
     */
    fun findScopeExcludes(scopeName: String): List<ScopeExclude> = scopes.filter { it.matches(scopeName) }

    /**
     * True if any [path exclude][paths] matches [path].
     */
    fun isPathExcluded(path: String) = paths.any { it.matches(path) }

    /**
     * True if the scope with the given [scopeName] is excluded by this [Excludes] configuration.
     */
    fun isScopeExcluded(scopeName: String): Boolean = findScopeExcludes(scopeName).isNotEmpty()
}
