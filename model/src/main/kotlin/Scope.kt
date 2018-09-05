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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

import java.util.SortedSet

/**
 * The scope class puts package dependencies into context.
 */
@JsonIgnoreProperties("delivered", "distributed")
data class Scope(
        /**
         * The respective package manager's native name for the scope, e.g. "compile", " provided" etc. for Maven, or
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
        val dependencies: SortedSet<PackageReference>,

        /**
         * A flag to indicate whether this scope should be excluded. This is set based on the .ort.yml configuration
         * file.
         */
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        val excluded: Boolean = false,

        /**
         * A map that holds arbitrary data. Can be used by third-party tools to add custom data to the model.
         */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        val data: Map<String, Any> = emptyMap()
) : Comparable<Scope> {
    fun collectDependencyIds(includeErroneous: Boolean = true, includeExcluded: Boolean = true) =
            dependencies.fold(sortedSetOf<Identifier>()) { ids, ref ->
                ids.also {
                    if ((ref.errors.isEmpty() || includeErroneous) && (!ref.excluded || includeExcluded)) it += ref.id
                    it += ref.collectDependencyIds(includeErroneous, includeExcluded)
                }
            }

    /**
     * A comparison function to sort scopes by their name.
     */
    override fun compareTo(other: Scope) = compareValuesBy(this, other) { it.name }

    /**
     * Return whether the given package is contained as a (transitive) dependency in this scope.
     */
    operator fun contains(pkg: Package) = contains(pkg.id)

    /**
     * Return whether the package identified by [pkgId] is contained as a (transitive) dependency in this scope.
     */
    operator fun contains(pkgId: Identifier) = dependencies.find { pkgRef ->
        // Strip the package manager part from the packageIdentifier because it is not part of the PackageReference.
        pkgRef.id == pkgId || pkgRef.dependsOn(pkgId)
    } != null
}
