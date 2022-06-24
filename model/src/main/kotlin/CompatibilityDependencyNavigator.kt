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
 * A [DependencyNavigator] implementation that can handle all kinds of [OrtResult]s (and is therefore the most
 * compatible one).
 *
 * While in future newly written [OrtResult] files are going to use the dependency graph format exclusively, existing
 * files may still store their dependency information in the classic dependency tree format, or even in a mixture of
 * both formats (if some package managers in use have already been ported to the new format while others have not). In
 * such a constellation, it has to be decided on a per-project basis, how dependency information has to be accessed.
 * This is exactly what this implementation does: For each request, it determines the dependency format of the project
 * in question and then delegates to a corresponding [DependencyNavigator] instance.
 */
class CompatibilityDependencyNavigator internal constructor(
    /** The [DependencyNavigator] to handle projects using the graph format. */
    val graphNavigator: DependencyNavigator,

    /** The [DependencyNavigator] to handle projects using the tree format. */
    val treeNavigator: DependencyNavigator
) : DependencyNavigator {
    companion object {
        /**
         * Create a [DependencyNavigator] that supports the given [ortResult] in the most efficient way. If all
         * projects in the result use the graph format, result is a [DependencyGraphNavigator]; if all projects use
         * the tree format, result is a [DependencyTreeNavigator]; otherwise, return a
         * [CompatibilityDependencyNavigator].
         */
        fun create(ortResult: OrtResult): DependencyNavigator {
            if (ortResult.analyzer?.result?.dependencyGraphs.orEmpty().isEmpty()) {
                return DependencyTreeNavigator
            }

            val (treeProjects, graphProjects) = ortResult.getProjects().partition(Project::usesTree)

            return when {
                graphProjects.isNotEmpty() && treeProjects.isNotEmpty() ->
                    CompatibilityDependencyNavigator(
                        DependencyGraphNavigator(ortResult),
                        DependencyTreeNavigator
                    )
                graphProjects.isEmpty() -> DependencyTreeNavigator
                else -> DependencyGraphNavigator(ortResult)
            }
        }
    }

    override fun scopeNames(project: Project): Set<String> =
        project.invokeNavigator(DependencyNavigator::scopeNames)

    override fun directDependencies(project: Project, scopeName: String): Sequence<DependencyNode> =
        project.invokeNavigator { directDependencies(it, scopeName) }

    override fun dependenciesForScope(
        project: Project,
        scopeName: String,
        maxDepth: Int,
        matcher: DependencyMatcher
    ): Set<Identifier> =
        project.invokeNavigator { dependenciesForScope(it, scopeName, maxDepth, matcher) }

    override fun packageDependencies(
        project: Project,
        packageId: Identifier,
        maxDepth: Int,
        matcher: DependencyMatcher
    ): Set<Identifier> =
        project.invokeNavigator { packageDependencies(it, packageId, maxDepth, matcher) }

    /**
     * Choose the correct [DependencyNavigator] to handle this [Project] and invoke [block] on it.
     */
    private fun <T> Project.invokeNavigator(block: DependencyNavigator.(Project) -> T): T {
        val navigator = if (usesTree()) treeNavigator else graphNavigator
        return navigator.block(this)
    }
}

/**
 * Return *true* if this project uses the dependency tree format to represent its dependencies and *false* otherwise.
 */
private fun Project.usesTree(): Boolean = scopeNames == null
