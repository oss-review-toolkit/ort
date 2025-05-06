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

        /** The name of the root folder under which binary artifacts are stored. */
        private const val BINARY_ROOT = "binary"
    }

    /**
     * Try to locate the folder for the given [artifact] (which is expected to reference an OSGi bundle per default
     * or can be a [binary][isBinary] artifact) in the local Maven repository. Return *null* if this artifact is not
     * found in the local repository.
     */
    fun folderForOsgiArtifact(artifact: Artifact, isBinary: Boolean = false): File? =
        localRepositoryRoot.resolve(P2_ROOT)
            .resolve(if (isBinary) BINARY_ROOT else BUNDLE_ROOT)
            .resolve(artifact.artifactId)
            .resolve(artifact.version)
            .takeIf { it.isDirectory }

    /**
     * Try to locate the file for the given [artifact] (which is expected to reference an OSGi bundle per default
     * or can be a [binary][isBinary] artifact) in the local Maven repository. Return *null* if the file could not be
     * found.
     */
    fun fileForOsgiArtifact(artifact: Artifact, isBinary: Boolean = false): File? =
        folderForOsgiArtifact(artifact, isBinary)
            ?.resolve("${artifact.artifactId}-${artifact.version}.jar")
            ?.takeIf { it.isFile }

    /**
     * Return the OSGi manifest for the given [artifact] (which is expected to reference an OSGi bundle per default
     * or can be a [binary][isBinary] artifact). If the artifact cannot be resolved, return *null*.
     */
    fun osgiManifest(artifact: Artifact, isBinary: Boolean = false): Manifest? =
        fileForOsgiArtifact(artifact, isBinary)?.let { artifactFile ->
            logger.info {
                "Reading metadata for '${artifact.identifier()}' from local repository '$artifactFile'."
            }

            JarInputStream(artifactFile.inputStream()).use { jar ->
                jar.manifest ?: Manifest()
            }
        }
}
