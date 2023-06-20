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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.annotation.JsonSerialize

import org.ossreviewtoolkit.model.utils.PackageSortedSetConverter
import org.ossreviewtoolkit.model.utils.ProjectSortedSetConverter

/**
 * A class that merges all information from individual [ProjectAnalyzerResult]s created for each found definition file.
 */
data class AnalyzerResult(
    /**
     * Sorted set of the projects, as they appear in the individual analyzer results.
     */
    @JsonSerialize(converter = ProjectSortedSetConverter::class)
    val projects: Set<Project>,

    /**
     * The set of identified packages for all projects.
     */
    @JsonSerialize(converter = PackageSortedSetConverter::class)
    val packages: Set<Package>,

    /**
     * The lists of [Issue]s that occurred within the analyzed projects themselves. Issues related to project
     * dependencies are contained in the dependencies of the project's scopes.
     * This property is not serialized if the map is empty to reduce the size of the result file. If there are no issues
     * at all, [AnalyzerResult.hasIssues] already contains that information.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonPropertyOrder(alphabetic = true)
    val issues: Map<Identifier, List<Issue>> = emptyMap(),

    /**
     * A map with [DependencyGraph]s keyed by the name of the package manager that created this graph. Package
     * managers supporting this feature can construct a shared [DependencyGraph] over all projects and store it in
     * this map.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonPropertyOrder(alphabetic = true)
    val dependencyGraphs: Map<String, DependencyGraph> = emptyMap()
) {
    companion object {
        /**
         * A constant for an [AnalyzerResult] where all properties are empty.
         */
        @JvmField
        val EMPTY = AnalyzerResult(
            projects = emptySet(),
            packages = emptySet(),
            issues = emptyMap()
        )
    }

    /**
     * Return a map of all de-duplicated [Issue]s associated by [Identifier].
     */
    @JsonIgnore
    fun getAllIssues(): Map<Identifier, Set<Issue>> =
        buildMap<Identifier, MutableSet<Issue>> {
            putAll(issues.mapValues { it.value.toMutableSet() })

            // Collecting issues from projects is necessary only if they use the dependency tree format; otherwise, the
            // issues can be retrieved from the graph. So, once analyzer results are created with dependency graphs
            // exclusively, this step can be removed.
            projects.filter { it.scopeDependencies != null }.forEach { project ->
                val projectDependencies = project.scopeDependencies.orEmpty().asSequence().flatMap(Scope::dependencies)
                DependencyNavigator.collectIssues(projectDependencies).forEach { (id, issues) ->
                    getOrPut(id) { mutableSetOf() } += issues
                }
            }

            dependencyGraphs.values.forEach { graph ->
                graph.collectIssues().forEach { (id, issues) ->
                    getOrPut(id) { mutableSetOf() } += issues
                }
            }
        }

    /**
     * Return a result, in which all contained [Project]s have their scope information resolved. If this result
     * has shared dependency graphs, the projects referring to one of these graphs are replaced by corresponding
     * instances that store their dependencies in the classic [Scope]-based format. Otherwise, this instance is
     * returned without changes.
     */
    fun withResolvedScopes(): AnalyzerResult =
        if (dependencyGraphs.isNotEmpty()) {
            copy(
                projects = projects.mapTo(mutableSetOf()) { it.withResolvedScopes(dependencyGraphs[it.id.type]) },
                dependencyGraphs = emptyMap()
            )
        } else {
            this
        }
}
