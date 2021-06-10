/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model

/**
 * Alias for a function that is used while navigating through the dependency graph to determine which dependencies
 * should be followed or skipped. The function is passed the current dependency node; it returns a flag whether this
 * dependency is matched or not.
 */
typealias DependencyMatcher = (DependencyNode) -> Boolean

/**
 * An interface allowing the navigation through the dependency information contained in an [OrtResult], independent of
 * the concrete storage representation.
 *
 * The dependencies detected by the analyzer are represented in an [OrtResult] in an optimized form which is not
 * accessible easily. To simplify dealing with dependencies, this interface offers functionality tailored towards the
 * typical use cases required by the single ORT components.
 */
interface DependencyNavigator {
    companion object {
        /**
         * A pre-defined [DependencyMatcher] that matches all dependencies. It can be used to traverse the whole
         * dependency graph.
         */
        val MATCH_ALL: DependencyMatcher = { true }
    }

    /**
     * Return a set with the names of all the scopes defined for the given [project].
     */
    fun scopeNames(project: Project): Set<String>

    /**
     * Return a sequence with [DependencyNode]s for the direct dependencies for the given [project] and [scopeName].
     * From this sequence, the whole dependency tree of that scope can be traversed.
     */
    fun directDependencies(project: Project, scopeName: String): Sequence<DependencyNode>

    /**
     * Return a set with information of the dependencies of a specific [scope][scopeName] of a [project]. With
     * [maxDepth] the depth of the dependency tree to be traversed can be restricted; negative values mean that there
     * is no restriction. Use the specified [matcher] to filter for specific dependencies.
     */
    fun dependenciesForScope(
        project: Project,
        scopeName: String,
        maxDepth: Int = -1,
        matcher: DependencyMatcher = MATCH_ALL
    ): Set<Identifier>

    /**
     * Return a map with information of the dependencies of a [project] grouped by scopes. This is equivalent to
     * calling [dependenciesForScope] for all the scopes of the given [project]. (This base implementation does
     * exactly this.)
     */
    fun scopeDependencies(
        project: Project,
        maxDepth: Int = -1,
        matcher: DependencyMatcher = MATCH_ALL
    ): Map<String, Set<Identifier>> =
        scopeNames(project).associateWith { dependenciesForScope(project, it, maxDepth, matcher) }
}
