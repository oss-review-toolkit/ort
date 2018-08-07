/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

import com.here.ort.utils.log

import java.util.SortedMap
import java.util.SortedSet

/**
 * A class that merges all information from individual AnalyzerResults created for each found build file
 */
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
         * The list of all errors.
         */
        val errors: SortedMap<Identifier, List<String>>
) : CustomData() {
    /**
     * True if there were any errors during the analysis, false otherwise.
     */
    @Suppress("UNUSED") // Not used in code, but shall be serialized.
    val hasErrors = {
        fun hasErrors(pkgReference: PackageReference): Boolean =
                pkgReference.errors.isNotEmpty() || pkgReference.dependencies.any { hasErrors(it) }

        errors.any { it.value.isNotEmpty() }
                || projects.any { it.scopes.any { it.dependencies.any { hasErrors(it) } } }
    }.invoke()
}

class AnalyzerResultBuilder {
    private val projects = sortedSetOf<Project>()
    private val packages = sortedSetOf<CuratedPackage>()
    private val errors = sortedMapOf<Identifier, List<String>>()

    fun build(): AnalyzerResult {
        return AnalyzerResult(projects, packages, errors)
    }

    fun addResult(projectAnalyzerResult: ProjectAnalyzerResult) = this.apply {
        // TODO: It might be, e.g. in the case of PIP "requirements.txt" projects, that different projects with the same
        // ID exist. We need to decide how to handle that case.
        val existingProject = projects.find { it.id == projectAnalyzerResult.project.id }

        if (existingProject != null) {
            val error = "Multiple projects with the same id '${existingProject.id}' found. Not adding the project " +
                    "defined in '${projectAnalyzerResult.project.definitionFilePath}' to the analyzer results as it " +
                    "duplicates the project defined in '${existingProject.definitionFilePath}'."

            log.error { error }

            val projectErrors = errors.getOrDefault(existingProject.id, listOf())
            errors[existingProject.id] = projectErrors + error
        } else {
            projects += projectAnalyzerResult.project
            packages += projectAnalyzerResult.packages
            errors[projectAnalyzerResult.project.id] = projectAnalyzerResult.errors
        }
    }
}
