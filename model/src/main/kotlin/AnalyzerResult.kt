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

import ch.frankel.slf4k.*

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

import com.here.ort.utils.log

import java.util.SortedMap
import java.util.SortedSet

/**
 * A class that merges all information from individual [ProjectAnalyzerResult]s created for each found definition file.
 */
@JsonIgnoreProperties(value = ["has_errors"], allowGetters = true)
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
         * The lists of errors that occurred within the analyzed projects themselves. Errors related to project
         * dependencies are contained in the dependencies of the project's scopes.
         */
        // Do not serialize if empty to reduce the size of the result file. If there are no errors at all,
        // [AnalyzerResult.hasErrors] already contains that information.
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        val errors: SortedMap<Identifier, List<OrtIssue>> = sortedMapOf(),

        /**
         * A map that holds arbitrary data. Can be used by third-party tools to add custom data to the model.
         */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        val data: CustomData = emptyMap()
) {
    companion object {
        /**
         * A constant for an [AnalyzerResult] where all properties are empty.
         */
        @JvmField
        val EMPTY = AnalyzerResult(
                projects = sortedSetOf(),
                packages = sortedSetOf(),
                errors = sortedMapOf()
        )
    }

    /**
     * Return a map of all de-duplicated errors associated by [Identifier].
     */
    fun collectErrors(): Map<Identifier, Set<OrtIssue>> {
        val collectedErrors = errors.mapValuesTo(mutableMapOf()) { it.value.toMutableSet() }

        projects.forEach { project ->
            project.collectErrors().forEach { id, errors ->
                collectedErrors.getOrPut(id) { mutableSetOf() } += errors
            }
        }

        return collectedErrors
    }

    /**
     * True if there were any errors during the analysis, false otherwise.
     */
    @Suppress("UNUSED") // Not used in code, but shall be serialized.
    val hasErrors by lazy {
        errors.any { it.value.isNotEmpty() } ||
                projects.any { it.scopes.any { it.dependencies.any { it.hasErrors() } } }
    }
}

class AnalyzerResultBuilder {
    private val projects = sortedSetOf<Project>()
    private val packages = sortedSetOf<CuratedPackage>()
    private val errors = sortedMapOf<Identifier, List<OrtIssue>>()

    fun build(): AnalyzerResult {
        return AnalyzerResult(projects, packages, errors)
    }

    fun addResult(projectAnalyzerResult: ProjectAnalyzerResult) =
            also {
                // TODO: It might be, e.g. in the case of PIP "requirements.txt" projects, that different projects with
                // the same ID exist. We need to decide how to handle that case.
                val existingProject = projects.find { it.id == projectAnalyzerResult.project.id }

                if (existingProject != null) {
                    val existingDefinitionFileUrl = existingProject.let {
                        "${it.vcsProcessed.url}/${it.definitionFilePath}"
                    }
                    val incomingDefinitionFileUrl = projectAnalyzerResult.project.let {
                        "${it.vcsProcessed.url}/${it.definitionFilePath}"
                    }

                    val error = OrtIssue(
                            source = "analyzer",
                            message = "Multiple projects with the same id '${existingProject.id.toCoordinates()}' " +
                                    "found. Not adding the project defined in $incomingDefinitionFileUrl to the " +
                                    "analyzer results as it duplicates the project defined in " +
                                    "$existingDefinitionFileUrl."
                    )

                    log.error { error.message }

                    val projectErrors = errors.getOrDefault(existingProject.id, listOf())
                    errors[existingProject.id] = projectErrors + error
                } else {
                    projects += projectAnalyzerResult.project
                    packages += projectAnalyzerResult.packages
                    if (projectAnalyzerResult.errors.isNotEmpty()) {
                        errors[projectAnalyzerResult.project.id] = projectAnalyzerResult.errors
                    }
                }
            }
}
