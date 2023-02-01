/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.analyzer

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.analyzer.managers.utils.PackageManagerDependencyHandler
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.DependencyGraph
import org.ossreviewtoolkit.model.DependencyGraphNavigator
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.model.utils.convertToDependencyGraph
import org.ossreviewtoolkit.utils.common.getDuplicates

class AnalyzerResultBuilder {
    companion object : Logging

    private val projects = mutableSetOf<Project>()
    private val packages = mutableSetOf<Package>()
    private val issues = mutableMapOf<Identifier, List<Issue>>()
    private val dependencyGraphs = mutableMapOf<String, DependencyGraph>()

    fun build(excludes: Excludes = Excludes.EMPTY): AnalyzerResult {
        val duplicates = (projects.map { it.toPackage() } + packages).getDuplicates { it.id }
        require(duplicates.isEmpty()) {
            "Unable to create the AnalyzerResult as it contains packages and projects with the same ids: " +
                    duplicates.values
        }

        return AnalyzerResult(projects, packages, issues, dependencyGraphs)
            .convertToDependencyGraph(excludes)
            .resolvePackageManagerDependencies()
    }

    fun addResult(projectAnalyzerResult: ProjectAnalyzerResult) = apply {
        // TODO: It might be, e.g. in the case of PIP "requirements.txt" projects, that different projects with
        //       the same ID exist. We need to decide how to handle that case.
        val existingProject = projects.find { it.id == projectAnalyzerResult.project.id }

        if (existingProject != null) {
            val existingDefinitionFileUrl = existingProject.let {
                "${it.vcsProcessed.url}/${it.definitionFilePath}"
            }
            val incomingDefinitionFileUrl = projectAnalyzerResult.project.let {
                "${it.vcsProcessed.url}/${it.definitionFilePath}"
            }

            val issue = createAndLogIssue(
                source = "analyzer",
                message = "Multiple projects with the same id '${existingProject.id.toCoordinates()}' " +
                        "found. Not adding the project defined in '$incomingDefinitionFileUrl' to the " +
                        "analyzer results as it duplicates the project defined in " +
                        "'$existingDefinitionFileUrl'."
            )

            val projectIssues = issues.getOrDefault(existingProject.id, emptyList())
            issues[existingProject.id] = projectIssues + issue
        } else {
            projects += projectAnalyzerResult.project
            addPackages(projectAnalyzerResult.packages)

            if (projectAnalyzerResult.issues.isNotEmpty()) {
                issues[projectAnalyzerResult.project.id] = projectAnalyzerResult.issues
            }
        }
    }

    /**
     * Add the given [packageSet] to this builder. This function can be used for packages that have been obtained
     * independently of a [ProjectAnalyzerResult].
     */
    fun addPackages(packageSet: Set<Package>) = apply {
        packages += packageSet
    }

    /**
     * Add a [DependencyGraph][graph] with all dependencies detected by the [PackageManager] with the given
     * [name][packageManagerName] to the result produced by this builder.
     */
    fun addDependencyGraph(packageManagerName: String, graph: DependencyGraph) = apply {
        dependencyGraphs[packageManagerName] = graph
    }
}

private fun AnalyzerResult.resolvePackageManagerDependencies(): AnalyzerResult {
    if (dependencyGraphs.isEmpty()) return this

    val handler = PackageManagerDependencyHandler(this)
    val navigator = DependencyGraphNavigator(dependencyGraphs)

    val graphs = projects.groupBy { it.id.type }.entries.associate { (type, projectsForType) ->
        val builder = DependencyGraphBuilder(handler)

        projectsForType.forEach { project ->
            project.scopeNames?.forEach { scopeName ->
                val qualifiedScopeName = DependencyGraph.qualifyScope(project, scopeName)
                navigator.directDependencies(project, scopeName).forEach { node ->
                    handler.resolvePackageManagerDependency(node).forEach {
                        builder.addDependency(qualifiedScopeName, it)
                    }
                }
            }
        }

        // Package managers that do not use the dependency graph representation, might not have a check implemented to
        // verify that packages exist for all dependencies, so we need to disable the reference check here.
        type to builder.build(checkReferences = false)
    }

    return copy(dependencyGraphs = graphs)
}
