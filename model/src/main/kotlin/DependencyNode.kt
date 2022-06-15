/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

/**
 * A generic interface describing the properties of a node in a dependency tree independent on its concrete
 * representation.
 *
 * The purpose of this interface is to abstract over the data structures used to store dependency information in a
 * specific format. Via the properties and functions defined here, all the relevant information about a single
 * dependency can be queried, and it is possible to navigate the dependency tree.
 *
 * Note that client code should not store [DependencyNode] instances. This is because the underlying data structures
 * may not map directly to the properties defined by this interface. Rather than creating adapter objects (which would
 * be rather costly for big graphs), an implementation may therefore decide to reuse objects during an iteration over
 * nodes. Then these instances would change their properties whenever iteration moves on to the next node (refer also
 * to the [visitDependencies] function). For use cases, in which a permanent reference to a [DependencyNode] is needed
 * that stays valid even after an iteration, use the [getStableReference] function. A concrete implementation is
 * required to create an adapter object that can be accessed safely if this is necessary.
 */
interface DependencyNode {
    /** The identifier of this dependency. */
    val id: Identifier

    /** The [PackageLinkage] of this dependency. */
    val linkage: PackageLinkage

    /** A list with issues that occurred while resolving this dependency. */
    val issues: List<OrtIssue>

    /**
     * Visit the direct dependencies of this [DependencyNode] by calling the specified [block] with a sequence of all
     * child nodes. The code block can produce a result, which is returned by this function. The function is the basis
     * for traversing the whole dependency graph; each call yields the direct dependencies of a node, by calling the
     * function recursively on these dependencies, the transitive dependency set can be visited.
     *
     * Notes:
     * - Not all storage formats used for dependency information use structures that can be mapped directly to this
     *   interface; therefore, some kind of wrapping may be required. To allow for optimizations, this function
     *   operates of a sequence rather than a collection; a collection would require wrapping a whole level of
     *   dependencies, while this is not necessarily required with a sequence.
     * - When constructing dependency graphs, package managers are required to prevent cycles. Therefore, code using
     *   this function does not have to implement its own check for cycles to prevent infinite loops. With other words:
     *   It is safe to recursively call [visitDependencies] on the nodes in the sequence.
     */
    fun <T> visitDependencies(block: (Sequence<DependencyNode>) -> T): T

    /**
     * Return a permanent reference to this [DependencyNode], which keeps its properties during an iteration over the
     * dependency graph. This means that a concrete implementation has to create an adapter object that stores the
     * properties of a node if necessary. The returned [DependencyNode] should also have meaningful implementations
     * for _equals()_ and _hashCode()_. Refer to the class comment for further information. This base implementation
     * returns the same instance assuming that this object is directly mapped to an underlying data structure.
     */
    @JsonIgnore
    fun getStableReference(): DependencyNode = this

    /**
     * Return a unique ID for this [DependencyNode]. This ID can be used for instance during traversal of the
     * dependency graph to detect nodes that have already been visited. As pointed out in the class comment, the
     * reference to this [DependencyNode] itself is not suitable for this purpose. A concrete implementation
     * typically returns a reference to an underlying node structure.
     */
    @JsonIgnore
    fun getInternalId(): Any = this
}
