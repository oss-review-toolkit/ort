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
 * The scope class puts package dependencies into context.
 */
data class Scope(
        /**
         * The respective package manager's native name for the scope, e.g. "compile", " provided" etc. for Maven, or
         * "dependencies", "devDependencies" etc. for NPM.
         */
        val name: String,

        /**
         * A flag to indicate whether this scope is delivered along with the product, i.e. distributed to external
         * parties.
         */
        val delivered: Boolean,

        /**
         * The set of references to packages in this scope. Note that only the first-order packages in this set
         * actually belong to the scope of [name]. Transitive dependency packages usually belong to the scope that
         * describes the packages required to compile the product. As an example, if this was the Maven "test" scope,
         * all first-order items in [dependencies] would be packages required for testing the product. But transitive
         * dependencies would not be test dependencies of the test dependencies, but compile dependencies of test
         * dependencies.
         */
        val dependencies: SortedSet<PackageReference>
) : Comparable<Scope> {
    /**
     * A comparison function to sort scopes by their name.
     */
    override fun compareTo(other: Scope) = compareValuesBy(this, other, { it.name })

    /**
     * Returns whether the given package is contained as a (transitive) dependency in this scope.
     */
    fun contains(pkg: Package) = contains(pkg.id.toString())

    /**
     * Returns whether the package identified by [pkgId] is contained as a (transitive) dependency in this scope.
     */
    fun contains(pkgId: String): Boolean {
        return dependencies.find { pkgRef ->
            // Strip the package manager part from the packageIdentifier because it is not part of the PackageReference.
            pkgRef.id.toString() == pkgId || pkgRef.dependsOn(pkgId)
        } != null
    }
}
