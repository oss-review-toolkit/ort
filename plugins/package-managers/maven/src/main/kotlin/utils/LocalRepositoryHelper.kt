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

package org.ossreviewtoolkit.plugins.packagemanagers.maven.utils

import java.io.File
import java.util.jar.JarInputStream
import java.util.jar.Manifest

import javax.xml.parsers.SAXParserFactory

import kotlin.io.resolve

import org.apache.logging.log4j.kotlin.logger
import org.apache.maven.repository.RepositorySystem

import org.eclipse.aether.artifact.Artifact

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/**
 * A class providing some helper functionality for accessing artifacts and metadata in the local Maven repository.
 */
class LocalRepositoryHelper(
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
            val properties = mutableMapOf<String, MutableMap<String, String>>()

            val handler = object : DefaultHandler() {
                private var currentArtifactId = ""

                override fun startElement(uri: String?, localName: String, qName: String, attributes: Attributes) {
                    when (qName) {
                        "artifact" -> {
                            currentArtifactId = "${artifact.groupId}:${attributes.getValue("id")}:" +
                                attributes.getValue("version")
                        }

                        "property" -> {
                            val key = attributes.getValue("name")
                            val value = attributes.getValue("value")

                            val artifactProperties = properties.getOrPut(currentArtifactId) { mutableMapOf() }
                            artifactProperties[key] = value
                        }
                    }
                }
            }

            val factory = SAXParserFactory.newInstance()
            val parser = factory.newSAXParser()

            logger.info { "Parsing P2 artifacts file '$file'." }

            parser.parse(file, handler)
            return properties[artifact.identifier()].orEmpty()
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
