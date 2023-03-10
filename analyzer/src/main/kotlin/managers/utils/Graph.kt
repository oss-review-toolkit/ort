/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.analyzer.managers.utils

import java.util.LinkedList
import java.util.SortedSet

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.PackageReference

/**
 * A class to represent a graph with dependencies. This representation is basically an adjacency list implemented by a
 * map whose keys are package identifiers and whose values are the identifiers of packages these packages depend on.
 */
internal class Graph private constructor(private val nodeMap: MutableMap<Identifier, MutableSet<Identifier>>) {
    companion object : Logging

    constructor() : this(mutableMapOf())

    /**
     * Return a set with all nodes (i.e. package identifiers) contained in this graph.
     */
    val nodes: Set<Identifier> get() = nodeMap.keys

    /**
     * Return the size of this graph. This is the number of nodes it contains.
     */
    val size: Int get() = nodeMap.size

    /**
     * Add an edge (i.e. a dependency relation) from [source] to [target] to this dependency graph. Add missing nodes if
     * necessary.
     */
    fun addEdge(source: Identifier, target: Identifier) {
        nodeMap.getOrPut(source) { mutableSetOf() } += target
        addNode(target)
    }

    /**
     * Add a node to this dependency graph.
     */
    fun addNode(node: Identifier) {
        nodeMap.getOrPut(node) { mutableSetOf() }
    }

    /**
     * Return a subgraph of this [Graph] that contains only nodes from the given set of [subNodes]. This can be used to
     * construct graphs for specific scopes.
     */
    fun subgraph(subNodes: Set<Identifier>): Graph =
        Graph(
            nodeMap.filter { it.key in subNodes }.mapValuesTo(mutableMapOf()) { e ->
                e.value.filterTo(mutableSetOf()) { it in subNodes }
            }
        )

    /**
     * Return a copy of this Graph with edges removed so that no circle remains.
     * TODO: The code has been copied from DependencyGraphBuilder as a temporary solutions. Once GoMod is migrated to
     * use the dependency graph, this function can be dropped and the one from DependencyGraphBuilder can be re-used,
     * see also https://github.com/oss-review-toolkit/ort/issues/4249.
     */
    fun breakCycles(): Graph {
        val outgoingEdgesForNodes = nodeMap.mapValuesTo(mutableMapOf()) { it.value.toMutableSet() }
        val color = outgoingEdgesForNodes.keys.associateWithTo(mutableMapOf()) { NodeColor.WHITE }

        fun visit(u: Identifier) {
            color[u] = NodeColor.GRAY

            val nodesClosingCircle = mutableSetOf<Identifier>()

            outgoingEdgesForNodes[u].orEmpty().forEach { v ->
                if (color[v] == NodeColor.WHITE) {
                    visit(v)
                } else if (color[v] == NodeColor.GRAY) {
                    nodesClosingCircle += v
                }
            }

            outgoingEdgesForNodes[u]?.removeAll(nodesClosingCircle)
            nodesClosingCircle.forEach { v ->
                logger.debug { "Removing edge: ${u.toCoordinates()} -> ${v.toCoordinates()}}." }
            }

            color[u] = NodeColor.BLACK
        }

        val queue = LinkedList(outgoingEdgesForNodes.keys)

        while (queue.isNotEmpty()) {
            val v = queue.removeFirst()

            if (color.getValue(v) != NodeColor.WHITE) continue

            visit(v)
        }

        return Graph(outgoingEdgesForNodes.mapValuesTo(mutableMapOf()) { it.value.toMutableSet() })
    }

    /**
     * Convert this [Graph] to a set of [PackageReference]s that spawn the dependency trees of the direct dependencies
     * of the given [root] package. The graph must not contain any cycles, so [breakCycles] should be called before.
     */
    fun toPackageReferenceForest(root: Identifier): SortedSet<PackageReference> {
        fun getPackageReference(id: Identifier): PackageReference {
            val dependencies = nodeMap.getValue(id).mapTo(sortedSetOf()) {
                getPackageReference(it)
            }

            return PackageReference(
                id = id,
                linkage = PackageLinkage.PROJECT_STATIC,
                dependencies = dependencies
            )
        }

        return dependencies(root).mapTo(sortedSetOf()) { getPackageReference(it) }
    }

    /**
     * Return the identifiers of the direct dependencies of the package denoted by [id].
     */
    private fun dependencies(id: Identifier): Set<Identifier> = nodeMap[id].orEmpty()
}

private enum class NodeColor { WHITE, GRAY, BLACK }
