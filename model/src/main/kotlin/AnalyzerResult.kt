/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package com.here.ort.model

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

import java.util.SortedMap
import java.util.SortedSet

/**
 * A class that merges all information from individual [ProjectAnalyzerResult]s created for each found definition file.
 */
@JsonIgnoreProperties(value = ["has_issues"], allowGetters = true)
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
    val issues: SortedMap<Identifier, List<OrtIssue>> = sortedMapOf()
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
            val issues = curatedPackage.pkg.collectIssues()
            if (issues.isNotEmpty()) {
                collectedIssues.getOrPut(curatedPackage.pkg.id) { mutableSetOf() } += issues
            }
        }

        return collectedIssues
    }

    /**
     * True if there were any issues during the analysis, false otherwise.
     */
    @Suppress("UNUSED") // Not used in code, but shall be serialized.
    val hasIssues by lazy {
        issues.any { it.value.isNotEmpty() }
                || projects.any { it.scopes.any { it.dependencies.any { it.hasIssues() } } }
    }
}

class AnalyzerResultBuilder {
    private val projects = sortedSetOf<Project>()
    private val packages = sortedSetOf<CuratedPackage>()
    private val issues = sortedMapOf<Identifier, List<OrtIssue>>()

    fun build() = AnalyzerResult(projects, packages, issues)

    fun addResult(projectAnalyzerResult: ProjectAnalyzerResult) =
        also {
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
                packages += projectAnalyzerResult.packages
                if (projectAnalyzerResult.issues.isNotEmpty()) {
                    issues[projectAnalyzerResult.project.id] = projectAnalyzerResult.issues
                }
            }
        }
}
