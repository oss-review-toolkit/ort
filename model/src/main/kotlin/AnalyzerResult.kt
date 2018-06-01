/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

import com.fasterxml.jackson.annotation.JsonProperty

import com.here.ort.utils.log

import java.util.SortedMap
import java.util.SortedSet

/**
 * A class that merges all information from individual AnalyzerResults created for each found build file
 */
data class AnalyzerResult(
        /**
         * If dynamic versions were allowed during the dependency resolution. If true it means that the dependency tree
         * might change with another scan if any of the (transitive) dependencies is declared with a version range and
         * a new version of this dependency was released in the meantime. It is always true for package managers that do
         * not support lock files, but do support version ranges.
         */
        @JsonProperty("allow_dynamic_versions")
        val allowDynamicVersions: Boolean,

        /**
         * The [VcsInfo] of the analyzed repository.
         */
        val vcs: VcsInfo,

        /**
         * The normalized [VcsInfo] of the analyzed repository.
         */
        @JsonProperty("vcs_processed")
        val vcsProcessed: VcsInfo,

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
     * Create the individual [ProjectAnalyzerResult]s this [AnalyzerResult] was built from.
     */
    fun createProjectAnalyzerResults() = projects.map { project ->
            val allDependencies = project.collectAllDependencies()
            val projectPackages = packages.filter { allDependencies.contains(it.pkg.id) }.toSortedSet()
            ProjectAnalyzerResult(allowDynamicVersions, project, projectPackages, errors[project.id] ?: emptyList())
        }
}

class AnalyzerResultBuilder(
        private val allowDynamicVersions: Boolean,
        private val vcsInfo: VcsInfo
) {
    private val projects = sortedSetOf<Project>()
    private val packages = sortedSetOf<CuratedPackage>()
    private val errors = sortedMapOf<Identifier, List<String>>()

    fun build(): AnalyzerResult {
        return AnalyzerResult(allowDynamicVersions, vcsInfo, vcsInfo.normalize(), projects, packages, errors)
    }

    fun addResult(projectAnalyzerResult: ProjectAnalyzerResult) = this.apply {
        val existingProject = projects.find { it.id == projectAnalyzerResult.project.id }
        if (existingProject != null) {
            val error = "Multiple projects with the same id '${existingProject.id}' found. Not adding the project " +
                    "defined in '${projectAnalyzerResult.project.definitionFilePath}' to the analyzer results as it " +
                    "duplicates the project defined in '${existingProject.definitionFilePath}'."

            log.error { error }

            val projectErrors = errors.getOrDefault(existingProject.id, listOf())
            errors[existingProject.id] = projectErrors + error
        } else {
            projects.add(projectAnalyzerResult.project)
            packages.addAll(projectAnalyzerResult.packages)
            errors[projectAnalyzerResult.project.id] = projectAnalyzerResult.errors
        }
    }
}
