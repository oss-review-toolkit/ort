/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.model

import com.fasterxml.jackson.annotation.JsonInclude

import java.util.SortedSet

/**
 * A human-readable reference to a software [Package]. Each package reference itself refers to other package
 * references that are dependencies of the package.
 */
data class PackageReference(
        /**
         * The identifier of the package.
         */
        val id: Identifier,

        /**
         * The list of references to packages this package depends on. Note that this list depends on the scope in
         * which this package reference is used.
         */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        val dependencies: SortedSet<PackageReference> = sortedSetOf(),

        /**
         * A list of errors that occurred handling this [PackageReference].
         */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        val errors: List<Error> = emptyList(),

        /**
         * A map that holds arbitrary data. Can be used by third-party tools to add custom data to the model.
         */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        val data: CustomData = emptyMap()
) : Comparable<PackageReference> {
    /**
     * Return the set of identifiers for all packages references this package reference depends on.
     */
    fun collectDependencyIds(includeErroneous: Boolean = true): SortedSet<Identifier> =
            dependencies.fold(sortedSetOf<Identifier>()) { ids, ref ->
                ids.also {
                    if (ref.errors.isEmpty() || includeErroneous) it += ref.id
                    it += ref.collectDependencyIds(includeErroneous)
                }
            }

    /**
     * A comparison function to sort package references by their identifier. This function ignores all other properties
     * except for [id].
     */
    override fun compareTo(other: PackageReference) = id.compareTo(other.id)

    /**
     * Return whether the given package is a (transitive) dependency of this reference.
     */
    fun dependsOn(pkg: Package) = dependsOn(pkg.id)

    /**
     * Return whether the package identified by [pkgId] is a (transitive) dependency of this reference.
     */
    fun dependsOn(pkgId: Identifier): Boolean {
        return dependencies.find { pkgRef ->
            // Strip the package manager part from the packageIdentifier because it is not part of the PackageReference.
            pkgRef.id == pkgId || pkgRef.dependsOn(pkgId)
        } != null
    }

    /**
     * Return whether this package reference or any of its dependencies has errors.
     */
    fun hasErrors(): Boolean = errors.isNotEmpty() || dependencies.any { it.hasErrors() }

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
}
