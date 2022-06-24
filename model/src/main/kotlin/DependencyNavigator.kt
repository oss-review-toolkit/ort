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

import java.util.LinkedList

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
@Suppress("TooManyFunctions")
interface DependencyNavigator {
    companion object {
        /**
         * A pre-defined [DependencyMatcher] that matches all dependencies. It can be used to traverse the whole
         * dependency graph.
         */
        val MATCH_ALL: DependencyMatcher = { true }

        /**
         * A pre-defined [DependencyMatcher] that matches only dependencies with a linkage indicating subprojects.
         */
        private val MATCH_SUB_PROJECTS: DependencyMatcher = { node ->
            node.linkage in PackageLinkage.PROJECT_LINKAGE
        }

        /**
         * Return a set with all the [Identifier]s contained in the given map of scope dependencies. This is useful if
         * all dependencies are needed independent of the scope they belong to.
         */
        private fun Map<String, Set<Identifier>>.collectDependencies(): Set<Identifier> =
            values.flatMapTo(mutableSetOf()) { it }

        /**
         * Return a map with all [OrtIssue]s found in the dependency graph spawned by [dependencies] grouped by their
         * [Identifier]s.
         */
        fun collectIssues(dependencies: Sequence<DependencyNode>): Map<Identifier, Set<OrtIssue>> {
            val collectedIssues = mutableMapOf<Identifier, MutableSet<OrtIssue>>()

            fun addIssues(nodes: Sequence<DependencyNode>) {
                nodes.forEach { node ->
                    if (node.issues.isNotEmpty()) {
                        collectedIssues.getOrPut(node.id) { mutableSetOf() } += node.issues
                    }

                    node.visitDependencies(::addIssues)
                }
            }

            addIssues(dependencies)

            return collectedIssues
        }
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

    /**
     * Return a set with the [Identifier]s of packages that are dependencies of the package with the given [packageId]
     * in the given [project]. Starting from [project], the dependency graph is searched for the package in question;
     * then its dependencies are collected. It is possible to restrict the dependencies to be fetched with [maxDepth]
     * and [matcher].
     */
    fun packageDependencies(
        project: Project,
        packageId: Identifier,
        maxDepth: Int = -1,
        matcher: DependencyMatcher = MATCH_ALL
    ): Set<Identifier>

    /**
     * Return a map with the shortest paths to each dependency in all scopes of the given [project]. The path to a
     * dependency is defined by the nodes of the dependency tree that need to be passed to get to the dependency. For
     * direct dependencies the shortest path is empty. The resulting map has scope names as keys; the values are maps
     * with the shorted paths to all the dependencies contained in that scope.
     */
    fun getShortestPaths(project: Project): Map<String, Map<Identifier, List<Identifier>>> {
        val pathMap = mutableMapOf<String, Map<Identifier, List<Identifier>>>()

        scopeNames(project).forEach { scope ->
            pathMap[scope] = getShortestPathForScope(project, scope)
        }

        return pathMap
    }

    /**
     * Return the set of [Identifier]s that refer to subprojects of the given [project].
     */
    fun collectSubProjects(project: Project): Set<Identifier> =
        scopeDependencies(project, matcher = MATCH_SUB_PROJECTS).collectDependencies()

    /**
     * Return a set with the [Identifier]s of all dependencies of the given [project] across all scopes. As usual,
     * it is possible to restrict the dependencies to be fetched with [maxDepth] and [matcher].
     */
    fun projectDependencies(
        project: Project,
        maxDepth: Int = -1,
        matcher: DependencyMatcher = MATCH_ALL
    ): Set<Identifier> =
        scopeDependencies(project, maxDepth, matcher).collectDependencies()

    /**
     * Return the depth of the dependency tree rooted at the given [project] associated with this [scopeName]. If the
     * scope cannot be resolved, return -1.
     */
    fun dependencyTreeDepth(project: Project, scopeName: String): Int =
        getTreeDepthRecursive(directDependencies(project, scopeName))

    /**
     * Return a map of all de-duplicated [OrtIssue]s associated by [Identifier] for the given [project].
     */
    fun projectIssues(project: Project): Map<Identifier, Set<OrtIssue>> =
        collectIssues(scopeNames(project).asSequence().flatMap { directDependencies(project, it) })

    /**
     * Determine the map of the shortest paths for all the dependencies of a [project], given its map of
     * [scopeDependencies].
     */
    private fun getShortestPathForScope(
        project: Project,
        scope: String
    ): Map<Identifier, List<Identifier>> =
        getShortestPathsForScope(directDependencies(project, scope), dependenciesForScope(project, scope))

    /**
     * Determine the map of the shortest paths for a specific scope given its direct dependency [nodes] and a set with
     * [allDependencies].
     */
    private fun getShortestPathsForScope(
        nodes: Sequence<DependencyNode>,
        allDependencies: Set<Identifier>
    ): Map<Identifier, List<Identifier>> {
        data class QueueItem(
            val pkgRef: DependencyNode,
            val parents: List<Identifier>
        )

        val remainingIds = allDependencies.toMutableSet()
        val queue = LinkedList<QueueItem>()
        val result = sortedMapOf<Identifier, List<Identifier>>()

        nodes.forEach { queue.offer(QueueItem(it.getStableReference(), emptyList())) }

        while (queue.isNotEmpty()) {
            val item = queue.poll()
            if (item.pkgRef.id in remainingIds) {
                result[item.pkgRef.id] = item.parents
                remainingIds -= item.pkgRef.id
            }

            val newParents = item.parents + item.pkgRef.id
            item.pkgRef.visitDependencies { dependencyNodes ->
                dependencyNodes.forEach { node -> queue.offer(QueueItem(node.getStableReference(), newParents)) }
            }
        }

        require(remainingIds.isEmpty()) {
            "Could not find the shortest path for these dependencies: ${remainingIds.joinToString()}"
        }

        return result
    }

    /**
     * Traverse the given sequence of [dependencies] recursively to determine the depth of the dependency tree.
     */
    private fun getTreeDepthRecursive(dependencies: Sequence<DependencyNode>): Int =
        dependencies.map { dependency ->
            1 + dependency.visitDependencies { getTreeDepthRecursive(it) }
        }.maxOrNull() ?: 0
}
