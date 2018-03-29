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

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

import java.io.File
import java.util.SortedMap
import java.util.SortedSet

/**
 * A class that merges all information from individual AnalyzerResults created for each found build file
 */
data class MergedAnalyzerResult(
        /**
         * If dynamic versions were allowed during the dependency resolution. If true it means that the dependency tree
         * might change with another scan if any of the (transitive) dependencies is declared with a version range and
         * a new version of this dependency was released in the meantime. It is always true for package managers that do
         * not support lock files, but do support version ranges.
         */
        @JsonProperty("allow_dynamic_versions")
        val allowDynamicVersions: Boolean,

        /**
         * Description of the scanned repository, with VCS information if available.
         */
        val repository: ScannedDirectoryDetails,

        /**
         * Sorted set of the projects, as they appear in the individual analyzer results.
         */
        val projects: SortedSet<Project>,

        /**
         * Map holding paths to the individual analyzer results for each project.
         */
        @JsonProperty("project_id_result_file_path_map")
        @JsonDeserialize(keyUsing = IdentifierFromStringKeyDeserializer::class)
        val projectResultsFiles: SortedMap<Identifier, String>,

        /**
         * The set of identified packages for all projects.
         */
        val packages: SortedSet<Package>,

        /**
         * The list of all errors.
         */
        @JsonDeserialize(keyUsing = IdentifierFromStringKeyDeserializer::class)
        val errors: SortedMap<Identifier, List<String>>
)

class MergedResultsBuilder(
        private val allowDynamicVersions: Boolean,
        private val directoryDetails: ScannedDirectoryDetails
) {
    private val projects = sortedSetOf<Project>()
    private val projectResultsFiles = sortedMapOf<Identifier, String>()
    private val packages = sortedSetOf<Package>()
    private val errors = sortedMapOf<Identifier, List<String>>()

    fun build(): MergedAnalyzerResult =
            MergedAnalyzerResult(allowDynamicVersions, directoryDetails, projects, projectResultsFiles, packages,
                    errors)

    fun addResult(analyzerResultPath: String, analyzerResult: AnalyzerResult) {
        projectResultsFiles[analyzerResult.project.id] = analyzerResultPath.replace(File.separatorChar, '/')
        projects.add(analyzerResult.project)
        packages.addAll(analyzerResult.packages)
        errors[analyzerResult.project.id] = analyzerResult.errors
    }
}

data class ScannedDirectoryDetails(
        /**
         * Name of top level input directory
         */
        val name: String,

        /**
         * Full path of analyzed directory
         */
        val path: String,

        /**
         * Original VCS-related information as defined in the [Project]'s meta-data.
         */
        val vcs: VcsInfo,

        /**
         * Normalized [VcsInfo] of the analyzed [Project].
         */
        @JsonProperty("vcs_processed")
        val vcsProcessed: VcsInfo = vcs.normalize()
)
