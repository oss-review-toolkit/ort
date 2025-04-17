/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.maven.tycho

import java.io.File

import org.apache.logging.log4j.kotlin.logger

import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact

/**
 * A helper class to manage information stored in Tycho target files.
 *
 * The target platform plays an important role in Tycho builds as it contains essential information for resolving
 * dependencies. The Tycho package manager implementation delegates to Tycho itself for the dependency resolution.
 * However, some information from target files is nevertheless needed to obtain correct metadata for dependencies.
 * This class is responsible for detecting target files in the analysis root directory and extracting the relevant
 * data.
 *
 * See https://wiki.eclipse.org/Tycho/Target_Platform/.
 */
internal class TargetHandler(
    /** A set with the URLs of the P2 repositories defined in the target files. */
    val repositoryUrls: Set<String>,

    /** A set listing the IDs of features that have been declared in the target files. */
    val featureIds: Set<String>,

    /** A map storing the declared Maven dependencies with the coordinates used by Tycho. */
    private val mavenDependencies: Map<String, Artifact>
) {
    companion object {
        /**
         * Create an instance of [TargetHandler] that loads its data from target files found below the given
         * [projectRoot] folder.
         */
        fun create(projectRoot: File): TargetHandler {
            val states = collectTargetFiles(projectRoot)
            val repositoryIds = states.flatMapTo(mutableSetOf(), ParseTargetFileState::repositoryUrls)
            val featureIds = states.flatMapTo(mutableSetOf(), ParseTargetFileState::featureIds)
            val mavenDependencies = states.flatMap(ParseTargetFileState::mavenDependencies)
                .associateBy(::tychoId)

            return TargetHandler(repositoryIds, featureIds, mavenDependencies)
        }

        /**
         * Collect all Tycho target files that can be found under the given [projectRoot] and parse them. Return the
         * resulting [ParseTargetFileState] objects.
         */
        private fun collectTargetFiles(projectRoot: File): List<ParseTargetFileState> {
            // TODO: There may be a better way to locate target files by inspecting the projects found in the build.
            val targetFiles = projectRoot.walkTopDown().filter {
                it.name.endsWith(".target") && it.isFile
            }.toList()

            return targetFiles.map(::parseTargetFile)
        }

        /**
         * Parse the given [targetFile] and extract the repository URLs referenced in it.
         */
        private fun parseTargetFile(targetFile: File): ParseTargetFileState {
            val handler = ElementHandler(ParseTargetFileState())
                .handleElement("repository") { state, attributes, _ ->
                    state.repositoryUrls += attributes.getValue("location")
                    state
                }.handleElement("groupId") { state, _, body ->
                    state.addDependencyAttribute("groupId", body)
                }.handleElement("artifactId") { state, _, body ->
                    state.addDependencyAttribute("artifactId", body)
                }.handleElement("classifier") { state, _, body ->
                    state.addDependencyAttribute("classifier", body)
                }.handleElement("type") { state, _, body ->
                    state.addDependencyAttribute("packaging", body)
                }.handleElement("version") { state, _, body ->
                    state.addDependencyAttribute("version", body)
                }.handleElement("dependency") { state, _, _ ->
                    state.storeMavenDependency()
                }.handleElement("feature") { state, attributes, _ ->
                    state.addFeature(attributes.getValue("id"))
                }

            return parseXml(targetFile, handler)
        }

        /**
         * Generate an identifier for the given [artifact] that can be used in a mapping from Tycho artifacts to Maven
         * dependencies.
         */
        private fun tychoId(artifact: Artifact): String = "${artifact.groupId}.${artifact.artifactId}"
    }

    /**
     * Try to map the given [tychoArtifact] to a Maven dependency based on dependency declarations found in the
     * processed target files. In target files, it is possible to declare features that contain a number of regular
     * Maven dependencies. There are also options to change the default resolution mechanism for such dependencies.
     * If this is done, Tycho sometimes creates alternative identifiers for the dependencies. This function attempts
     * to find the original Maven coordinates for affected artifacts. They are needed to retrieve the correct metadata.
     * Result is *null* if no matching Maven dependency is found.
     */
    fun mapToMavenDependency(tychoArtifact: Artifact): Artifact? =
        mavenDependencies[tychoArtifact.artifactId]?.also { dep ->
            logger.info {
                "Mapping Tycho artifact '${tychoArtifact.groupId}:${tychoArtifact.artifactId}' to Maven " +
                    "dependency '${dep.groupId}:${dep.artifactId}'."
            }
        }
}

/**
 * A data class to store the state during parsing of a target file.
 */
private data class ParseTargetFileState(
    /** The [Set] with the URLs of repositories that have been found so far. */
    val repositoryUrls: MutableSet<String> = mutableSetOf(),

    /** The [List] with Maven dependencies that have been declared in the target file. */
    val mavenDependencies: MutableList<Artifact> = mutableListOf(),

    /** Stores the attributes of a currently processed dependency. */
    val dependencyAttributes: MutableMap<String, String> = mutableMapOf(),

    /** Stores the IDs of features declared in the target file. */
    val featureIds: MutableSet<String> = mutableSetOf()
) {
    /**
     * Update this state by adding an attribute with the given [name] and [value] for a currently processed dependency.
     */
    fun addDependencyAttribute(name: String, value: String): ParseTargetFileState {
        dependencyAttributes[name] = value
        return this
    }

    /**
     * Update this state by adding the [featureId] of a feature that have been found while parsing the target file.
     */
    fun addFeature(featureId: String): ParseTargetFileState {
        featureIds += featureId
        return this
    }

    /**
     * Update this state by adding a new Maven dependency based on the dependency attributes that have been set
     * previously.
     */
    fun storeMavenDependency(): ParseTargetFileState {
        val artifact = DefaultArtifact(
            dependencyAttributes["groupId"],
            dependencyAttributes["artifactId"],
            dependencyAttributes["classifier"],
            dependencyAttributes["packaging"],
            dependencyAttributes["version"]
        )
        mavenDependencies += artifact
        dependencyAttributes.clear()

        return this
    }
}
