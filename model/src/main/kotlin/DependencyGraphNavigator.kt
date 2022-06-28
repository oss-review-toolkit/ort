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
 * A [DependencyNavigator] implementation based on the dependency graph format. It obtains the information about a
 * project's dependencies from the shared [DependencyGraph] stored in the [OrtResult].
 */
class DependencyGraphNavigator(
    /** The map with shared dependency graphs from the associated result. */
    private val graphs: Map<String, DependencyGraph>
) : DependencyNavigator {
    constructor(ortResult: OrtResult) : this(ortResult.analyzer?.result?.dependencyGraphs.orEmpty())

    init {
        require(graphs.isNotEmpty()) {
            "No dependency graph available to initialize DependencyGraphNavigator."
        }
    }

    /**
     * A data structure allowing fast access to a specific [DependencyReference] based on its index and fragment.
     */
    private val graphDependencyRefMappings: Map<String, Array<MutableList<DependencyGraphNode>>> by lazy {
        graphs.mapValues { it.value.dependencyRefMapping() }
    }

    override fun scopeNames(project: Project): Set<String> = project.scopeNames.orEmpty()

    override fun directDependencies(project: Project, scopeName: String): Sequence<DependencyNode> {
        val graph = graphForManager(project.managerName)
        val rootDependencies = graph.scopes[DependencyGraph.qualifyScope(project, scopeName)].orEmpty().map { root ->
            referenceFor(project.managerName, root)
        }

        return dependenciesSequence(graph, rootDependencies)
    }

    override fun dependenciesForScope(
        project: Project,
        scopeName: String,
        maxDepth: Int,
        matcher: DependencyMatcher
    ): Set<Identifier> {
        val dependencyIds = mutableSetOf<Identifier>()
        collectDependencies(directDependencies(project, scopeName), maxDepth, matcher, dependencyIds)
        return dependencyIds
    }

    override fun packageDependencies(
        project: Project,
        packageId: Identifier,
        maxDepth: Int,
        matcher: DependencyMatcher
    ): Set<Identifier> {
        val dependencies = mutableSetOf<Identifier>()

        fun traverse(node: DependencyNode) {
            if (node.id == packageId) {
                node.visitDependencies { collectDependencies(it, maxDepth, matcher, dependencies) }
            }

            node.visitDependencies { dependencies ->
                dependencies.forEach(::traverse)
            }
        }

        scopeNames(project).forEach { scope ->
            directDependencies(project, scope).forEach(::traverse)
        }

        return dependencies
    }

    /**
     * Return the [DependencyGraph] for the given [package manager][manager] or throw an exception if no such graph
     * exists.
     */
    private fun graphForManager(manager: String): DependencyGraph =
        requireNotNull(graphs[manager]) { "No DependencyGraph for package manager '$manager' available." }

    /**
     * Resolve a [DependencyGraphNode] in the [DependencyGraph] for the specified [package manager][manager] with the
     * given [pkgIndex] and [fragment]. Throw an exception if no such node can be found.
     */
    private fun referenceFor(manager: String, pkgIndex: Int, fragment: Int): DependencyGraphNode =
        requireNotNull(graphDependencyRefMappings[manager])[pkgIndex].find { it.fragment == fragment }
            ?: throw IllegalArgumentException(
                "Could not resolve a DependencyReference for index = $pkgIndex and fragment $fragment."
            )

    /**
     * Resolve a [DependencyGraphNode] in the [DependencyGraph] for the specified [package manager][manager] that
     * corresponds to the given [rootIndex]. Throw an exception if no such reference can be found.
     */
    private fun referenceFor(manager: String, rootIndex: RootDependencyIndex): DependencyGraphNode =
        referenceFor(manager, rootIndex.root, rootIndex.fragment)
}

/**
 * An internal class supporting the efficient traversal of a structure of [DependencyGraphNode]s.
 *
 * The idea behind this class is that only a single instance is created for traversing a collection of
 * [DependencyGraphNode]s. Like a database cursor, this instance moves on to the next node in the collection. It
 * implements the [DependencyNode] interface by delegating to the properties of the current [DependencyGraphNode].
 * That way it is not necessary to wrap all the graph nodes in the collection to iterate over into adapter objects.
 */
private class DependencyRefCursor(
    /** The [DependencyGraph] that owns the references to traverse. */
    val graph: DependencyGraph,

    /** The [DependencyGraphNode]s to traverse. */
    val nodes: Collection<DependencyGraphNode> = emptyList(),

    /**
     * An optional initial value for the current node. This is mainly used it this instance acts as an adapter
     * for a single [DependencyGraphNode].
     */
    val initCurrent: DependencyGraphNode? = null
) : DependencyNode {
    /** An [Iterator] for doing the actual traversal. */
    private val nodesIterator = nodes.iterator()

    /** Points to the current element of the traversal. */
    var current = initCurrent ?: nodesIterator.next()

    override val id: Identifier
        get() = graph.packages[current.pkg]

    override val linkage: PackageLinkage
        get() = current.linkage

    override val issues: List<OrtIssue>
        get() = current.issues

    override fun <T> visitDependencies(block: (Sequence<DependencyNode>) -> T): T =
        block(dependenciesSequence(graph, graph.dependencies.getValue(current)))

    override fun getStableReference(): DependencyNode = DependencyRefCursor(graph, initCurrent = current)

    override fun getInternalId(): Any = current

    /**
     * Return a sequence of [DependencyNode]s that is implemented based on this instance. This sequence allows access
     * to the properties of the [DependencyReference]s passed to this instance, although each sequence element is a
     * reference to *this*.
     */
    fun asSequence(): Sequence<DependencyNode> =
        generateSequence(this) {
            if (nodesIterator.hasNext()) {
                current = nodesIterator.next()
                this
            } else {
                null
            }
        }

    /**
     * Check for equality with [other]. The check is implemented as a reference comparison to the current node. As the
     * nodes in the dependency graph have been de-duplicated, a reference comparison is sufficient. Note that this
     * implementation is not necessarily consistent when comparing nodes during an iteration; but it works correctly
     * for stable references.
     */
    override fun equals(other: Any?): Boolean = (other as? DependencyRefCursor)?.current == current

    /**
     * Return a hash code for this object. The hash code is obtained from the current node, which is aligned to the
     * [equals] implementation.
     */
    override fun hashCode(): Int = current.hashCode()
}

/**
 * Return the name of the package manager that constructed this [Project]. This is required to find the
 * [DependencyGraph] for this project.
 */
private val Project.managerName: String
    get() = id.type

/**
 * Construct a data structure that allows fast access to all [DependencyGraphNode]s contained in this
 * [DependencyGraph] based on its index and fragment. This structure is used to lookup specific nodes, typically
 * the entry points in the graph (i.e. the roots of the dependency trees referenced by the single scopes). The
 * structure is an array whose index corresponds to the index of the [DependencyNode]. Under an index multiple
 * nodes can be listed for the different fragments.
 */
private fun DependencyGraph.dependencyRefMapping(): Array<MutableList<DependencyGraphNode>> {
    val nodesArray = Array<MutableList<DependencyGraphNode>>(packages.size) { mutableListOf() }

    dependencies.keys.forEach { node -> nodesArray[node.pkg] += node }

    return nodesArray
}

/**
 * Generate a sequence that allows accessing the given [dependencies] from the provided [graph] via the
 * [DependencyNode] interface.
 */
private fun dependenciesSequence(
    graph: DependencyGraph,
    dependencies: Collection<DependencyGraphNode>
): Sequence<DependencyNode> =
    dependencies.takeIf { it.isNotEmpty() }?.let {
        DependencyRefCursor(graph, it).asSequence()
    }.orEmpty()

/**
 * Traverse the given [nodes] recursively up to the given [maxDepth], filter using [matcher] and adds all identifiers
 * found to [ids].
 */
private fun collectDependencies(
    nodes: Sequence<DependencyNode>,
    maxDepth: Int,
    matcher: DependencyMatcher,
    ids: MutableSet<Identifier>,
    visited: MutableSet<DependencyGraphNode> = mutableSetOf()
) {
    if (maxDepth != 0) {
        nodes.forEach { node ->
            val cursor = node as DependencyRefCursor
            if (cursor.current !in visited) {
                visited += cursor.current
                if (matcher(node)) ids += node.id

                node.visitDependencies { collectDependencies(it, maxDepth - 1, matcher, ids, visited) }
            }
        }
    }
}
