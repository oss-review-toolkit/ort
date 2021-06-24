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
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.StdSerializer

import java.util.SortedMap
import java.util.SortedSet

import org.ossreviewtoolkit.model.utils.DependencyGraphConverter

/**
 * A class that merges all information from individual [ProjectAnalyzerResult]s created for each found definition file.
 */
@JsonIgnoreProperties(value = ["has_issues", /* Backwards-compatibility: */ "has_errors"], allowGetters = true)
@JsonSerialize(using = AnalyzerResultSerializer::class)
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

        projects.forEach { project ->
            project.collectIssues().forEach { (id, issues) ->
                collectedIssues.getOrPut(id) { mutableSetOf() } += issues
            }
        }

        packages.forEach { curatedPackage ->
            val issues = curatedPackage.collectIssues()
            if (issues.isNotEmpty()) {
                collectedIssues.getOrPut(curatedPackage.pkg.id) { mutableSetOf() } += issues
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
    fun withScopesResolved(): AnalyzerResult =
        if (dependencyGraphs.isNotEmpty()) {
            copy(
                projects = projects.map { it.withResolvedScopes(dependencyGraphs[it.id.type]) }.toSortedSet(),
                dependencyGraphs = sortedMapOf()
            )
        } else {
            this
        }
}

/**
 * A custom serializer for [AnalyzerResult] instances.
 *
 * This serializer makes sure that [AnalyzerResult]s always use the dependency graph representation when they are
 * serialized. This is achieved by processing the result by [DependencyGraphConverter] before it is written out.
 */
private class AnalyzerResultSerializer : StdSerializer<AnalyzerResult>(AnalyzerResult::class.java) {
    override fun serialize(result: AnalyzerResult, gen: JsonGenerator, provider: SerializerProvider?) {
        val resultWithGraph = DependencyGraphConverter.convert(result)

        gen.writeStartObject()

        with(resultWithGraph) {
            gen.writeObjectField("projects", projects)
            gen.writeObjectField("packages", packages)

            if (issues.isNotEmpty()) {
                gen.writeObjectField("issues", issues)
            }

            gen.writeObjectField("dependency_graphs", dependencyGraphs)
            gen.writeBooleanField("has_issues", hasIssues)
        }

        gen.writeEndObject()
    }
}
