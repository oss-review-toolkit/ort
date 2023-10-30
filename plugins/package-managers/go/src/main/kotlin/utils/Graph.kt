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

package org.ossreviewtoolkit.plugins.packagemanagers.go.utils

import java.util.LinkedList

import org.apache.logging.log4j.kotlin.logger

/**
 * A class to represent a graph with dependencies between nodes of type [T]. The representation is an adjacency list
 * implemented by a map whose keys are nodes and whose values are the target nodes of the outgoing edges for the
 * respective key.
 */
internal class Graph<T> private constructor(private val nodeMap: MutableMap<T, MutableSet<T>>) {
    constructor() : this(mutableMapOf())

    /**
     * Return a set with all nodes (i.e. package identifiers) contained in this graph.
     */
    val nodes: Set<T> get() = nodeMap.keys

    /**
     * Return the size of this graph. This is the number of nodes it contains.
     */
    val size: Int get() = nodeMap.size

    /**
     * Add an edge (i.e. a dependency relation) from [source] node to [target] node to this dependency graph. Add
     * missing nodes if necessary.
     */
    fun addEdge(source: T, target: T) {
        nodeMap.getOrPut(source) { mutableSetOf() } += target
        addNode(target)
    }

    /**
     * Add a node to this dependency graph.
     */
    fun addNode(node: T) {
        nodeMap.getOrPut(node) { mutableSetOf() }
    }

    /**
     * Return a subgraph of this [Graph] that contains only nodes from the given set of [subNodes]. This can be used to
     * construct graphs for specific scopes.
     */
    fun subgraph(subNodes: Set<T>): Graph<T> =
        Graph(
            nodeMap.filter { it.key in subNodes }.mapValuesTo(mutableMapOf()) { e ->
                e.value.filterTo(mutableSetOf()) { it in subNodes }
            }
        )

    /**
     * Return a copy of this Graph with edges removed so that no circle remains.
     *
     * TODO: The code has been copied from DependencyGraphBuilder as a temporary solutions. Once GoMod is migrated to
     *       use the dependency graph, this function can be dropped and the one from DependencyGraphBuilder can be
     *       re-used, see also https://github.com/oss-review-toolkit/ort/issues/4249.
     */
    fun breakCycles(): Graph<T> {
        val outgoingEdgesForNodes = nodeMap.mapValuesTo(mutableMapOf()) { it.value.toMutableSet() }
        val color = outgoingEdgesForNodes.keys.associateWithTo(mutableMapOf()) { NodeColor.WHITE }

        fun visit(u: T) {
            color[u] = NodeColor.GRAY

            val nodesClosingCircle = mutableSetOf<T>()

            outgoingEdgesForNodes[u].orEmpty().forEach { v ->
                if (color[v] == NodeColor.WHITE) {
                    visit(v)
                } else if (color[v] == NodeColor.GRAY) {
                    nodesClosingCircle += v
                }
            }

            outgoingEdgesForNodes[u]?.removeAll(nodesClosingCircle)
            nodesClosingCircle.forEach { v ->
                logger.debug { "Removing edge: $u -> $v." }
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
     * Return the direct dependencies of the given [node].
     */
    fun getDependencies(node: T): Set<T> = nodeMap[node].orEmpty()
}

private enum class NodeColor { WHITE, GRAY, BLACK }
