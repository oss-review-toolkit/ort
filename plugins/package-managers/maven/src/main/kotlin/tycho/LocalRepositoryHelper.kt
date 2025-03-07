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
import java.util.jar.JarInputStream
import java.util.jar.Manifest

import kotlin.collections.getOrPut
import kotlin.io.resolve

import org.apache.logging.log4j.kotlin.logger
import org.apache.maven.repository.RepositorySystem

import org.eclipse.aether.artifact.Artifact

import org.ossreviewtoolkit.plugins.packagemanagers.maven.utils.identifier

/**
 * A class providing some helper functionality for accessing artifacts and metadata in the local Maven repository.
 */
internal class LocalRepositoryHelper(
    /** The root directory of the local Maven repository. */
    private val localRepositoryRoot: File = RepositorySystem.defaultUserLocalRepository
) {
    companion object {
        /** The name of the root folder that stores artifacts downloaded from P2 repositories. */
        private const val P2_ROOT = "p2"

        /** The name of the folder that stores OSGi bundles. */
        private const val BUNDLE_ROOT = "osgi/bundle"

        /**
         * The suffix of the file with properties for P2 artifacts. When downloading artifacts from a P2 repository,
         * Tycho creates an additional file with the same base name as the artifact JAR file, but with this suffix that
         * contains further metadata.
         */
        private const val ARTIFACTS_FILE_SUFFIX = "p2artifacts.xml"

        /**
         * Parse the given [file] with information about artifacts and extract the properties for the given [artifact].
         */
        private fun parseArtifactsFile(file: File, artifact: Artifact): Map<String, String> {
            val handler = ElementHandler(ParsePropertiesState())
                .handleElement("artifact") { state, attributes ->
                    state.withCurrentArtifactId(
                        bundleIdentifier(attributes.getValue("id"), attributes.getValue("version"))
                    )
                }.handleElement("property") { state, attributes ->
                    state.withProperty(attributes.getValue("name"), attributes.getValue("value"))
                }

            logger.info { "Parsing P2 artifacts file '$file'." }

            val result = parseXml(file, handler)
            return result.properties[bundleIdentifier(artifact.artifactId, artifact.version)].orEmpty()
        }
    }

    /**
     * Try to locate the folder for the given [artifact] (which is expected to reference an OSGi bundle) in the local
     * Maven repository. Return *null* if this artifact is not found in the local repository.
     */
    fun folderForOsgiArtifact(artifact: Artifact): File? =
        localRepositoryRoot.resolve("$P2_ROOT/$BUNDLE_ROOT")
            .resolve(artifact.artifactId)
            .resolve(artifact.version)
            .takeIf { it.isDirectory }

    /**
     * Try to locate the file for the given [artifact] (which is expected to reference an OSGi bundle) in the local
     * Maven repository. Return *null* if the file could not be found.
     */
    fun fileForOsgiArtifact(artifact: Artifact): File? =
        folderForOsgiArtifact(artifact)
            ?.resolve("${artifact.artifactId}-${artifact.version}.jar")
            ?.takeIf { it.isFile }

    /**
     * Return the OSGi manifest for the given [artifact]. If the artifact cannot be resolved, return *null*.
     */
    fun osgiManifest(artifact: Artifact): Manifest? =
        fileForOsgiArtifact(artifact)?.let { artifactFile ->
            logger.info {
                "Reading metadata for '${artifact.identifier()}' from local repository '$artifactFile'."
            }

            JarInputStream(artifactFile.inputStream()).use { jar ->
                jar.manifest ?: Manifest()
            }
        }

    /**
     * Read the XML file with properties for the given P2 [artifact] and return the properties as a [Map]. If the
     * artifact cannot be resolved, return *null*. If the XML file does not exist or cannot be parsed, return an empty
     * [Map].
     */
    fun p2Properties(artifact: Artifact): Map<String, String>? =
        folderForOsgiArtifact(artifact)?.let { artifactsFolder ->
            val artifactsFile =
                artifactsFolder.resolve("${artifact.artifactId}-${artifact.version}-$ARTIFACTS_FILE_SUFFIX")
            runCatching {
                parseArtifactsFile(artifactsFile, artifact)
            }.onFailure {
                logger.error(it) { "Could not parse P2 artifacts file '$artifactsFile'." }
            }.getOrDefault(emptyMap())
        }
}

/**
 * Generate an identifier for an OSGi bundle artifact based on the given [artifactId] and [version].
 */
private fun bundleIdentifier(artifactId: String, version: String) = "$artifactId:$version"

/**
 * A data class to hold the state while parsing the properties of a P2 artifact.
 */
private data class ParsePropertiesState(
    /** The ID of the artifact whose properties are currently processed. */
    val currentArtifactId: String = "",

    /** A [Map] with the aggregated properties of all encountered artifacts. */
    val properties: MutableMap<String, MutableMap<String, String>> = mutableMapOf<String, MutableMap<String, String>>()
) {
    /**
     * Return an update [ParsePropertiesState] instance that has the given [artifactId] set as current artifact.
     */
    fun withCurrentArtifactId(artifactId: String) = copy(currentArtifactId = artifactId)

    /**
     * Return an update [ParsePropertiesState] instance that stores the property defined by the given [key] and [value]
     * for the current artifact.
     */
    fun withProperty(key: String, value: String): ParsePropertiesState {
        val artifactProperties = properties.getOrPut(currentArtifactId) { mutableMapOf() }
        artifactProperties[key] = value

        return this
    }
}
