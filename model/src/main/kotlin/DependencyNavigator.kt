/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
        val MATCH_SUB_PROJECTS: DependencyMatcher = { node ->
            node.linkage in PackageLinkage.PROJECT_LINKAGE
        }

        /**
         * Return a map with all [Issue]s found in the dependency graph spawned by [dependencies] grouped by their
         * [Identifier]s.
         */
        fun collectIssues(dependencies: Sequence<DependencyNode>): Map<Identifier, Set<Issue>> {
            val collectedIssues = mutableMapOf<Identifier, MutableSet<Issue>>()

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
     * Return a set with the [Identifier]s of the dependencies of a [scope][scopeName] of a [project]. With [maxDepth]
     * the depth of the dependency tree to be traversed can be restricted; negative values mean that there is no
     * restriction. Use the specified [matcher] to filter for specific dependencies.
     */
    fun scopeDependencies(
        project: Project,
        scopeName: String,
        maxDepth: Int = -1,
        matcher: DependencyMatcher = MATCH_ALL
    ): Set<Identifier>

    /**
     * Return a set with the [Identifier]s of the dependencies of a [project]. This is equivalent to calling
     * [scopeDependencies] for all the scopes of the given [project].
     */
    fun projectDependencies(
        project: Project,
        maxDepth: Int = -1,
        matcher: DependencyMatcher = MATCH_ALL
    ): Set<Identifier> =
        scopeNames(project).flatMapTo(mutableSetOf()) { scopeDependencies(project, it, maxDepth, matcher) }

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
     * Return the depth of the dependency tree rooted at the given [project] associated with this [scopeName]. If the
     * scope cannot be resolved, return -1.
     */
    fun dependencyTreeDepth(project: Project, scopeName: String): Int =
        getTreeDepthRecursive(directDependencies(project, scopeName))

    /**
     * Return a map of all de-duplicated [Issue]s associated by [Identifier] for the given [project].
     */
    fun projectIssues(project: Project): Map<Identifier, Set<Issue>> =
        collectIssues(scopeNames(project).asSequence().flatMap { directDependencies(project, it) })

    /**
     * Determine the map of the shortest paths for all the dependencies of a [project], given its map of
     * [projectDependencies].
     */
    private fun getShortestPathForScope(project: Project, scope: String): Map<Identifier, List<Identifier>> {
        val directDependencies = directDependencies(project, scope)
        val shortestPaths = getShortestPathsForScope(directDependencies)

        val unvisitedDependencies = scopeDependencies(project, scope) - shortestPaths.keys
        require(unvisitedDependencies.isEmpty()) {
            "Could not find the shortest path for these dependencies: ${unvisitedDependencies.joinToString()}"
        }

        return shortestPaths
    }

    /**
     * Traverse the given sequence of [dependencies] recursively to determine the depth of the dependency tree.
     */
    private fun getTreeDepthRecursive(dependencies: Sequence<DependencyNode>): Int =
        dependencies.map { dependency ->
            1 + dependency.visitDependencies { getTreeDepthRecursive(it) }
        }.maxOrNull() ?: 0
}

private data class QueueItem(
    val node: DependencyNode,
    val parent: DependencyNode?
)

/**
 * Determine the map of the shortest paths for a specific scope given its direct dependency [nodes].
 */
private fun getShortestPathsForScope(nodes: Sequence<DependencyNode>): Map<Identifier, List<Identifier>> {
    // A node is visited if and only if it is a key in this map.
    val predecessorForVisitedNode = mutableMapOf<DependencyNode, DependencyNode?>()
    val queue = LinkedList<QueueItem>()
    // Keep track of the end-points of the shortest paths to speed up the re-construction.
    val firstVisitedNodeForId = mutableMapOf<Identifier, DependencyNode>()

    nodes.forEach { queue.offer(QueueItem(it.getStableReference(), null)) }

    while (queue.isNotEmpty()) {
        val item = queue.poll()
        if (item.node in predecessorForVisitedNode) continue

        predecessorForVisitedNode[item.node] = item.parent
        // Once any node with a particular identifier is visited, the endpoint of the shortest path to that
        // identifier is known to be that visited node.
        firstVisitedNodeForId.putIfAbsent(item.node.id, item.node)

        item.node.visitDependencies { dependencyNodes ->
            dependencyNodes.forEach { node ->
                val ref = node.getStableReference()
                if (ref !in predecessorForVisitedNode) {
                    queue.offer(QueueItem(ref, item.node))
                }
            }
        }
    }

    // Reconstruct the shortest paths.
    return firstVisitedNodeForId.mapValues { (_, node) ->
        LinkedList<Identifier>().apply {
            var current = predecessorForVisitedNode[node]

            while (current != null) {
                addFirst(current.id)
                current = predecessorForVisitedNode[current]
            }
        }
    }
}
