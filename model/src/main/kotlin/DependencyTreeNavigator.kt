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

/**
 * A [DependencyNavigator] implementation based on the classic dependency tree format, which is represented by the
 * [Project.scopes] property.
 */
object DependencyTreeNavigator : DependencyNavigator {
    override fun scopeNames(project: Project): Set<String> = project.scopes.mapTo(mutableSetOf(), Scope::name)

    override fun directDependencies(project: Project, scopeName: String): Sequence<DependencyNode> =
        project.findScope(scopeName)?.dependencies?.asSequence().orEmpty()

    override fun dependenciesForScope(
        project: Project,
        scopeName: String,
        maxDepth: Int,
        matcher: DependencyMatcher
    ): Set<Identifier> =
        project.findScope(scopeName)?.collectDependencies(maxDepth) { matcher(it) }.orEmpty()

    override fun scopeDependencies(
        project: Project,
        maxDepth: Int,
        matcher: DependencyMatcher
    ): Map<String, Set<Identifier>> =
        // Override the base implementation because a more efficient access to single scopes is possible.
        project.scopes.associate { scope ->
            scope.name to scope.collectDependencies(maxDepth, matcher.forReference())
        }

    override fun packageDependencies(
        project: Project,
        packageId: Identifier,
        maxDepth: Int,
        matcher: DependencyMatcher
    ): Set<Identifier> =
        project.findReferences(packageId).flatMapTo(mutableSetOf()) { ref ->
            ref.collectDependencies(maxDepth, matcher.forReference())
        }
}

/**
 * Return a predicate to match [PackageReference]s that evaluates the criteria of this [DependencyMatcher]. This is
 * useful when invoking the classic API, which often uses such predicates as filters.
 */
private fun DependencyMatcher.forReference(): (PackageReference) -> Boolean = { invoke(it) }

/**
 * Return the scope of this [Project] with the given [name] or *null* if it does not exist.
 */
private fun Project.findScope(name: String): Scope? = scopes.find { it.name == name }
