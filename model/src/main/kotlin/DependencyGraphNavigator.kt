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
        // Collect all root indices for all managers whose graphs have projects of the respective type.
        val rootIndicesForGraphs = graphs.mapNotNull { (managerName, graph) ->
            graph.scopes[DependencyGraph.qualifyScope(project.id, scopeName)]?.let { Triple(managerName, graph, it) }
        }

        if (rootIndicesForGraphs.isEmpty()) return emptySequence()

        // TODO: Consider extending the signature of this function to also take a manager name, so that the dependencies
        //       for that manager could be obtained without "guessing" which of the managers created the project.
        val (managerName, graph, rootIndices) = requireNotNull(rootIndicesForGraphs.singleOrNull()) {
            val managerNames = rootIndicesForGraphs.map { (managerName, _, _) -> managerName }
            "All of the $managerNames managers are able to manage '${project.id.type}' projects. Please enable only " +
                "one of them."
        }

        return dependenciesAccessor(managerName, graph, rootIndices)
    }

    fun dependenciesAccessor(
        manager: String,
        graph: DependencyGraph,
        rootIndices: List<RootDependencyIndex>
    ): Sequence<DependencyNode> {
        val rootDependencies = rootIndices.map { rootIndex ->
            referenceFor(manager, rootIndex)
        }

        return dependenciesSequence(graph, rootDependencies)
    }

    override fun scopeDependencies(
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
        val visited = mutableSetOf<DependencyGraphNode>()

        fun traverse(node: DependencyNode) {
            val cursor = node as DependencyRefCursor
            if (cursor.current in visited) return
            visited += cursor.current

            if (node.id == packageId) {
                node.visitDependencies { collectDependencies(it, maxDepth, matcher, dependencies, visited) }
            }

            // This continues to visit dependencies even after a matching node has been found as there could be multiple
            // nodes with the same ID in the graph, that each could have different dependencies due to custom resolution
            // configuration in the build system.
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
     * Resolve a [DependencyGraphNode] in the [DependencyGraph] for the specified [package manager][manager] that
     * corresponds to the given [rootIndex]. Throw an exception if no such reference can be found.
     */
    private fun referenceFor(manager: String, rootIndex: RootDependencyIndex): DependencyGraphNode =
        requireNotNull(graphDependencyRefMappings[manager])[rootIndex.root].find { it.fragment == rootIndex.fragment }
            ?: throw IllegalArgumentException(
                "Could not resolve a DependencyReference for index = ${rootIndex.root} and fragment " +
                    "${rootIndex.fragment}."
            )
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
    nodes: Collection<DependencyGraphNode> = emptyList(),

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

    override val issues: List<Issue>
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
    if (maxDepth == 0) return

    nodes.forEach { node ->
        val cursor = node as DependencyRefCursor
        if (matcher(node)) ids += node.id

        if (cursor.current !in visited) {
            visited += cursor.current
            node.visitDependencies { collectDependencies(it, maxDepth - 1, matcher, ids, visited) }
        }
    }
}
