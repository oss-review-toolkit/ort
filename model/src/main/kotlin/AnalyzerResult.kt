/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

import java.util.SortedMap
import java.util.SortedSet

/**
 * A class that merges all information from individual [ProjectAnalyzerResult]s created for each found definition file.
 */
@JsonIgnoreProperties(value = ["has_issues", /* Backwards-compatibility: */ "has_errors"], allowGetters = true)
data class AnalyzerResult(
    /**
     * Sorted set of the projects, as they appear in the individual analyzer results.
     */
    val projects: SortedSet<Project>,

    /**
     * The set of identified packages for all projects.
     */
    val packages: SortedSet<CuratedPackage>,

    /**
     * The lists of [OrtIssue]s that occurred within the analyzed projects themselves. Issues related to project
     * dependencies are contained in the dependencies of the project's scopes.
     * This property is not serialized if the map is empty to reduce the size of the result file. If there are no issues
     * at all, [AnalyzerResult.hasIssues] already contains that information.
     */
    @JsonAlias("errors")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val issues: SortedMap<Identifier, List<OrtIssue>> = sortedMapOf(),

    /**
     * A map with [DependencyGraph]s keyed by the name of the package manager that created this graph. Package
     * managers supporting this feature can construct a shared [DependencyGraph] over all projects and store it in
     * this map.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val dependencyGraphs: Map<String, DependencyGraph> = sortedMapOf()
) {
    companion object {
        /**
         * A constant for an [AnalyzerResult] where all properties are empty.
         */
        @JvmField
        val EMPTY = AnalyzerResult(
            projects = sortedSetOf(),
            packages = sortedSetOf(),
            issues = sortedMapOf()
        )
    }

    /**
     * Return a map of all de-duplicated [OrtIssue]s associated by [Identifier].
     */
    fun collectIssues(): Map<Identifier, Set<OrtIssue>> {
        val collectedIssues = issues.mapValuesTo(mutableMapOf()) { it.value.toMutableSet() }

        // Collecting issues from projects is necessary only if they use the dependency tree format; otherwise, the
        // issues can be retrieved from the graph. So, once analyzer results are created with dependency graphs
        // exclusively, this step can be removed.
        projects.filter { it.scopeDependencies != null }.forEach { project ->
            val projectDependencies = project.scopeDependencies.orEmpty().asSequence().flatMap(Scope::dependencies)
            DependencyNavigator.collectIssues(projectDependencies).forEach { (id, issues) ->
                collectedIssues.getOrPut(id) { mutableSetOf() } += issues
            }
        }

        dependencyGraphs.values.forEach { graph ->
            graph.collectIssues().forEach { (id, issues) ->
                collectedIssues.getOrPut(id) { mutableSetOf() } += issues
            }
        }

        return collectedIssues
    }

    /**
     * True if there were any issues during the analysis, false otherwise.
     */
    val hasIssues by lazy { collectIssues().isNotEmpty() }

    /**
     * Return a result, in which all contained [Project]s have their scope information resolved. If this result
     * has shared dependency graphs, the projects referring to one of these graphs are replaced by corresponding
     * instances that store their dependencies in the classic [Scope]-based format. Otherwise, this instance is
     * returned without changes.
     */
    fun withResolvedScopes(): AnalyzerResult =
        if (dependencyGraphs.isNotEmpty()) {
            copy(
                projects = projects.map { it.withResolvedScopes(dependencyGraphs[it.id.type]) }.toSortedSet(),
                dependencyGraphs = sortedMapOf()
            )
        } else {
            this
        }
}
