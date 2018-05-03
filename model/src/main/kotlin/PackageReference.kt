/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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
        val dependencies: SortedSet<PackageReference>,

        /**
         * A list of errors that occurred handling this [PackageReference].
         */
        val errors: List<String> = emptyList()
) : CustomData(), Comparable<PackageReference> {
    fun collectAllDependencies(): SortedSet<Identifier> = dependencies.map { it.id }.toSortedSet().also { result ->
        dependencies.forEach { result.addAll(it.collectAllDependencies()) }
    }

    /**
     * A comparison function to sort package references by their identifier. This function ignores all other properties
     * except for [id].
     */
    override fun compareTo(other: PackageReference) = id.compareTo(other.id)

    /**
     * Returns whether the given package is a (transitive) dependency of this reference.
     */
    fun dependsOn(pkg: Package) = dependsOn(pkg.id)

    /**
     * Returns whether the package identified by [pkgId] is a (transitive) dependency of this reference.
     */
    fun dependsOn(pkgId: Identifier): Boolean {
        return dependencies.find { pkgRef ->
            // Strip the package manager part from the packageIdentifier because it is not part of the PackageReference.
            pkgRef.id == pkgId || pkgRef.dependsOn(pkgId)
        } != null
    }
}
