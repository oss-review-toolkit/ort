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

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonInclude

import java.util.Deque
import java.util.LinkedList
import java.util.SortedSet

// A custom value filter for [PackageLinkage] to work around
// https://github.com/FasterXML/jackson-module-kotlin/issues/193.
@Suppress("EqualsOrHashCode", "EqualsWithHashCodeExist")
class PackageLinkageValueFilter {
    override fun equals(other: Any?) = other == PackageLinkage.DYNAMIC
}

/**
 * A human-readable reference to a software [Package]. Each package reference itself refers to other package
 * references that are dependencies of the package.
 */
// Do not serialize default values to reduce the size of the result file.
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
data class PackageReference(
    /**
     * The identifier of the package.
     */
    override val id: Identifier,

    /**
     * The type of linkage used for the referred package from its dependent package. As most of our supported
     * package managers / languages only support dynamic linking or at least default to it, also use that as the
     * default value here to not blow up our result files.
     */
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = PackageLinkageValueFilter::class)
    override val linkage: PackageLinkage = PackageLinkage.DYNAMIC,

    /**
     * The list of references to packages this package depends on. Note that this list depends on the scope in
     * which this package reference is used.
     */
    val dependencies: SortedSet<PackageReference> = sortedSetOf(),

    /**
     * A list of [OrtIssue]s that occurred handling this [PackageReference].
     */
    @JsonAlias("errors")
    override val issues: List<OrtIssue> = emptyList()
) : Comparable<PackageReference>, DependencyNode {
    /**
     * Return the set of [Identifier]s the package referred by this [PackageReference] transitively depends on,
     * up to and including a depth of [maxDepth] where counting starts at 0 (for the [PackageReference] itself) and 1
     * are direct dependencies etc. A value below 0 means to not limit the depth. If the given [filterPredicate] is
     * false for a specific [PackageReference] the corresponding [Identifier] is excluded from the result.
     */
    fun collectDependencies(
        maxDepth: Int = -1,
        filterPredicate: (PackageReference) -> Boolean = { true }
    ): Set<Identifier> {
        val result = mutableSetOf<Identifier>()

        val queue: Deque<Pair<PackageReference, Int>> = LinkedList()
        fun enqueue(packages: Collection<PackageReference>, level: Int) {
            if (maxDepth < 0 || level <= maxDepth) {
                packages.forEach { queue += Pair(it, level) }
            }
        }

        enqueue(dependencies, 1)
        while (queue.isNotEmpty()) {
            val (pkg, level) = queue.removeFirst()

            if (filterPredicate(pkg)) result += pkg.id

            enqueue(pkg.dependencies, level + 1)
        }

        return result
    }

    /**
     * A comparison function to sort package references by their identifier. This function ignores all other properties
     * except for [id].
     */
    override fun compareTo(other: PackageReference) = id.compareTo(other.id)

    /**
     * Return whether the package identified by [id] is a (transitive) dependency of this reference.
     */
    fun dependsOn(id: Identifier): Boolean = dependencies.any { it.id == id || it.dependsOn(id) }

    /**
     * Return all references to [id] as a dependency.
     */
    fun findReferences(id: Identifier): List<PackageReference> =
        dependencies.filter { it.id == id } + dependencies.flatMap { it.findReferences(id) }

    /**
     * Return whether this package reference or any of its dependencies has issues.
     */
    fun hasIssues(): Boolean = issues.isNotEmpty() || dependencies.any { it.hasIssues() }

    /**
     * Apply the provided [transform] to each node in the dependency tree represented by this [PackageReference] and
     * return the modified [PackageReference]. The tree is traversed depth-first (post-order).
     */
    fun traverse(transform: (PackageReference) -> PackageReference): PackageReference {
        val transformedDependencies = dependencies.map {
            it.traverse(transform)
        }
        return transform(copy(dependencies = transformedDependencies.toSortedSet()))
    }

    override fun <T> visitDependencies(block: (Sequence<DependencyNode>) -> T): T = block(dependencies.asSequence())
}
