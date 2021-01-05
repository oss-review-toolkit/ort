/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model

/**
 * A data class that represents the graph of dependencies of a project.
 *
 * This class holds information about a project's scopes and their dependencies in a format that minimizes the
 * consumption of memory. In projects with many scopes there is often a high degree of duplication in the
 * dependencies of the single scopes. To avoid this, this class separates the storage of the dependencies from the
 * storage of the scopes. The packages and their (transitive) dependencies are stored only once; an additional
 * mapping then associates the scopes with their (direct) dependencies.
 */
data class DependencyGraph(
    /**
     * A set with [PackageReference] objects serving as the entry points into the dependency graph. By traversing the
     * dependency trees contained in this set, each dependency of the represented project can be reached exactly once.
     */
    val dependencies: Set<PackageReference>,

    /**
     * A mapping from scope names to the direct dependencies of the scopes. Based on this information, the set of
     * [Scope]s of a project can be constructed from the serialized form.
     */
    val scopeMapping: Map<String, Set<Identifier>>
)
