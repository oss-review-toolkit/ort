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

package org.ossreviewtoolkit.model.utils

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage

/**
 * An interface used by [DependencyGraphBuilder] to handle the specific representations of concrete _PackageManager_
 * implementations in a generic way.
 *
 * A package manager may use its own, internal representation of a type [D] of a dependency. When constructing the
 * [org.ossreviewtoolkit.model.DependencyGraph] by passing the dependencies of the single scopes, the builder must be
 * able to extract certain information from the dependency objects. This is done via an implementation of this
 * interface.
 */
interface DependencyHandler<D> {
    /**
     * Construct a unique identifier for the given [dependency].
     */
    fun identifierFor(dependency: D): Identifier

    /**
     * Return a collection with the dependencies of the given [dependency]. [DependencyGraphBuilder] invokes this
     * function to construct the whole dependency tree spawned by this [dependency].
     */
    fun dependenciesFor(dependency: D): Collection<D>

    /**
     * Return the [PackageLinkage] for the given [dependency].
     */
    fun linkageFor(dependency: D): PackageLinkage

    /**
     * Create a [Package] to represent the given [dependency]. This is used to populate the packages in the analyzer
     * result. The creation of a package may fail, e.g. if the dependency cannot be resolved. In this case, a concrete
     * implementation is expected to return a dummy [Package] with correct coordinates and add a corresponding issue to
     * the provided [issues] list. If the [dependency] does not map to a package, an implementation should return
     * *null*.
     */
    fun createPackage(dependency: D, issues: MutableList<OrtIssue>): Package?

    /**
     * Return a collection with known issues for the given [dependency]. Some package manager implementations may
     * already encounter problems when obtaining dependency representations. These can be reported here. This base
     * implementation returns an empty collection.
     */
    fun issuesForDependency(dependency: D): Collection<OrtIssue> = emptyList()
}
