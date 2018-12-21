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
        val dependencies: SortedSet<PackageReference> = sortedSetOf(),

        /**
         * A map that holds arbitrary data. Can be used by third-party tools to add custom data to the model.
         */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        val data: CustomData = emptyMap()
) : Comparable<Scope> {
    /**
     * Return the set of [PackageReference]s in this [Scope], up to and including a depth of [maxDepth] where counting
     * starts at 0 (for the [Scope] itself) and 1 are direct dependencies etc. A value below 0 means to not limit the
     * depth. If [includeErroneous] is true, [PackageReference]s with errors (but not their dependencies without errors)
     * are excluded, otherwise they are included.
     */
    fun collectDependencies(maxDepth: Int = -1, includeErroneous: Boolean = true) =
            dependencies.fold(sortedSetOf<PackageReference>()) { refs, ref ->
                refs.also {
                    if (maxDepth != 0) {
                        if (ref.errors.isEmpty() || includeErroneous) it += ref
                        it += ref.collectDependencies(maxDepth - 1, includeErroneous)
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
}
