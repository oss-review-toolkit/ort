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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

import java.util.LinkedList
import java.util.SortedSet

/**
 * The scope class puts package dependencies into context.
 */
@JsonIgnoreProperties("delivered", "distributed")
data class Scope(
    /**
     * The respective package manager's native name for the scope, e.g. "compile", "provided" etc. for Maven, or
     * "dependencies", "devDependencies" etc. for NPM.
     */
    val name: String,

    /**
     * The set of references to packages in this scope. Note that only the first-order packages in this set
     * actually belong to the scope of [name]. Transitive dependency packages usually belong to the scope that
     * describes the packages required to compile the product. As an example, if this was the Maven "test" scope,
     * all first-order items in [dependencies] would be packages required for testing the product. But transitive
     * dependencies would not be test dependencies of the test dependencies, but compile dependencies of test
     * dependencies.
     */
    val dependencies: SortedSet<PackageReference> = sortedSetOf()
) : Comparable<Scope> {
    /**
     * Return the set of package [Identifier]s in this [Scope], up to and including a depth of [maxDepth] where counting
     * starts at 0 (for the [Scope] itself) and 1 are direct dependencies etc. A value below 0 means to not limit the
     * depth. If the given [filterPredicate] is false for a specific [PackageReference] the corresponding [Identifier]
     * is excluded from the result.
     */
    fun collectDependencies(
        maxDepth: Int = -1,
        filterPredicate: (PackageReference) -> Boolean = { true }
    ): Set<Identifier> =
        dependencies.fold(mutableSetOf()) { refs, ref ->
            refs.also {
                if (maxDepth != 0) {
                    if (filterPredicate(ref)) it += ref.id
                    it += ref.collectDependencies(maxDepth - 1, filterPredicate)
                }
            }
        }

    /**
     * A comparison function to sort scopes by their name.
     */
    override fun compareTo(other: Scope) = compareValuesBy(this, other) { it.name }

    /**
     * Return whether the package identified by [id] is contained as a (transitive) dependency in this scope.
     */
    operator fun contains(id: Identifier) = dependencies.any { it.id == id || it.dependsOn(id) }

    /**
     * Return all references to [id] as a dependency in this scope.
     */
    fun findReferences(id: Identifier) =
        dependencies.filter { it.id == id } + dependencies.flatMap { it.findReferences(id) }

    /**
     * Return the depth of the dependency tree rooted at the project associated with this scope.
     */
    @JsonIgnore
    fun getDependencyTreeDepth(): Int {
        fun getTreeDepthRec(dependencies: Collection<PackageReference>): Int =
            dependencies.map { dependency -> 1 + getTreeDepthRec(dependency.dependencies) }.maxOrNull() ?: 0

        return getTreeDepthRec(dependencies)
    }

    /**
     * Return the shortest path to each dependency in this scope. The path to a dependency is defined by the nodes of
     * the dependency tree that need to be passed to get to the dependency. For direct dependencies the shortest path is
     * empty.
     */
    @JsonIgnore
    fun getShortestPaths(): Map<Identifier, List<Identifier>> {
        data class QueueItem(
            val pkgRef: PackageReference,
            val parents: List<Identifier>
        )

        val remainingIds = collectDependencies().toMutableSet()
        val queue = LinkedList<QueueItem>()
        val result = sortedMapOf<Identifier, List<Identifier>>()

        dependencies.forEach { queue.offer(QueueItem(it, emptyList())) }

        while (queue.isNotEmpty()) {
            val item = queue.poll()
            if (item.pkgRef.id in remainingIds) {
                result[item.pkgRef.id] = item.parents
                remainingIds -= item.pkgRef.id
            }

            val newParents = item.parents + item.pkgRef.id
            item.pkgRef.dependencies.forEach { pkgRef ->
                queue.offer(QueueItem(pkgRef, newParents))
            }
        }

        require(remainingIds.isEmpty()) {
            "Could not find the shortest path for these dependencies: ${remainingIds.joinToString()}"
        }

        return result
    }

    /**
     * Return the amount of [PackageReference]s contained in this scope.
      */
    fun size(): Int {
        fun PackageReference.size(): Int = 1 + dependencies.sumOf { it.size() }

        return dependencies.sumOf { it.size() }
    }
}
