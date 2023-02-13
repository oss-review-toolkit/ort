/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.DependencyGraph
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.config.Excludes

/**
 * An object that supports the conversion of [AnalyzerResult]s to the dependency graph format.
 *
 * The main conversion function takes an [AnalyzerResult] and iterates over all projects contained in it. Each
 * project that still uses the dependency tree representation (i.e. the set of scopes) is converted, and a dependency
 * graph for the associated package manager is created. During this transformation, scope excludes are applied, so
 * that the result contains only dependencies from included scopes.
 *
 * Via this converter, optimized results can be generated for package managers that do not yet support this format.
 * This approach is, however, not ideal, because during the conversion both the dependency tree and the dependency
 * graph need to be hold in memory. So this is rather a temporary solution until all package managers support the new
 * format natively.
 */
object DependencyGraphConverter {
    /**
     * Convert the given [result], so that all dependencies are represented as dependency graphs. During conversion,
     * apply the scope excludes defined in [excludes]. If the [result] already contains a dependency graph that covers
     * all projects, it is returned as is.
     */
    fun convert(result: AnalyzerResult, excludes: Excludes = Excludes.EMPTY): AnalyzerResult {
        val projectsToConvert = result.projectsWithScopes()
        if (projectsToConvert.isEmpty()) return result

        val graphs = buildDependencyGraphs(projectsToConvert, excludes)
        val allGraphs = result.dependencyGraphs + graphs

        val filteredPackages = if (excludes.scopes.isEmpty()) {
            result.packages
        } else {
            filterExcludedPackages(allGraphs.values, result.packages)
        }

        return result.copy(
            dependencyGraphs = allGraphs,
            projects = result.projects.mapTo(mutableSetOf()) { it.convertToScopeNames(excludes) },
            packages = filteredPackages
        )
    }

    /**
     * Create and populate [DependencyGraphBuilder]s for the given [projects] taking [excludes] into account. The
     * resulting map contains one fully initialized graph builder for each package manager involved which can be
     * used to obtain the [DependencyGraph] and all packages.
     */
    private fun buildDependencyGraphs(projects: Set<Project>, excludes: Excludes): Map<String, DependencyGraph> {
        val graphs = mutableMapOf<String, DependencyGraph>()

        projects.groupBy { it.id.type }.forEach { (type, projectsForType) ->
            val builder = DependencyGraphBuilder(ScopesDependencyHandler)

            projectsForType.forEach { project ->
                project.scopes.filterNot { excludes.isScopeExcluded(it.name) }.forEach { scope ->
                    val scopeName = DependencyGraph.qualifyScope(project, scope.name)
                    scope.dependencies.forEach { dependency ->
                        builder.addDependency(scopeName, dependency)
                    }
                }
            }

            graphs[type] = builder.build(checkReferences = false)
        }

        return graphs
    }

    /**
     * Filter out all [packages] that are no longer referenced by one of the given [dependency graphs][graphs]. These
     * packages have been subject of scope excludes.
     */
    private fun filterExcludedPackages(
        graphs: Collection<DependencyGraph>,
        packages: Collection<Package>
    ): Set<Package> {
        val includedPackages = graphs.flatMapTo(mutableSetOf()) { it.packages }
        return packages.filterTo(mutableSetOf()) { it.id in includedPackages }
    }

    /**
     * Determine the projects in this [AnalyzerResult] that require a conversion. These are the projects that manage
     * their dependencies in a scope structure.
     */
    private fun AnalyzerResult.projectsWithScopes(): Set<Project> =
        projects.filterTo(mutableSetOf()) { it.scopeDependencies?.isNotEmpty() ?: false }

    /**
     * Convert the dependency representation used by this [Project] to the dependency graph format, i.e. a set of
     * scope names. Use the given [excludes] to filter out excluded scopes. Return the same project if this format is
     * already in use.
     */
    private fun Project.convertToScopeNames(excludes: Excludes): Project =
        takeIf { scopeNames != null } ?: copy(
            scopeNames = scopes.filterNot { excludes.isScopeExcluded(it.name) }.mapTo(sortedSetOf()) { it.name },
            scopeDependencies = null
        )

    /**
     * A special [DependencyHandler] implementation that operates on [PackageReference] objects. A
     * [DependencyGraphBuilder] equipped with this handler is able to transform a dependency tree structure into an
     * equivalent dependency graph.
     */
    private object ScopesDependencyHandler : DependencyHandler<PackageReference> {
        override fun identifierFor(dependency: PackageReference): Identifier = dependency.id

        override fun dependenciesFor(dependency: PackageReference): Collection<PackageReference> =
            dependency.dependencies

        override fun linkageFor(dependency: PackageReference): PackageLinkage = dependency.linkage

        override fun createPackage(dependency: PackageReference, issues: MutableList<Issue>): Package? = null

        override fun issuesForDependency(dependency: PackageReference): Collection<Issue> =
            dependency.issues
    }
}

fun AnalyzerResult.convertToDependencyGraph(excludes: Excludes = Excludes.EMPTY) =
    DependencyGraphConverter.convert(this, excludes)
