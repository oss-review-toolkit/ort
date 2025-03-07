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

import java.util.concurrent.ConcurrentHashMap

import org.apache.logging.log4j.kotlin.logger

import org.codehaus.plexus.DefaultPlexusContainer
import org.codehaus.plexus.PlexusContainer
import org.codehaus.plexus.logging.BaseLoggerManager
import org.codehaus.plexus.logging.Logger

import org.eclipse.aether.artifact.Artifact

import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.plugins.packagemanagers.maven.utils.MavenLogger
import org.ossreviewtoolkit.utils.common.withoutPrefix

/**
 * A helper class for Tycho that tracks the artifacts downloaded from P2 repositories.
 *
 * The information collected by this class is used by the Tycho package manager implementation to generate correct
 * remote artifacts for package binaries and source bundles. Unfortunately, it is rather hard to gather this
 * information. The Tycho extension circumvents Maven's standard artifact resolution, so that access via the Maven
 * session, the repository system, or event listener APIs is not possible. What this class does instead is to monitor
 * the build log output for messages about downloaded artifacts. It is then possible to associate artifacts with the
 * source URLs from where they have been loaded. This might be a bit fragile, but it has the advantage that no further
 * steps are required to locate artifacts in repositories; the work done by Tycho during the build is basically reused.
 */
internal class P2ArtifactTracker(
    /** The helper object for accessing the local Maven repository. */
    private val repositoryHelper: LocalRepositoryHelper
) {
    companion object {
        /** The name of the logger used by Tycho for log messages about downloaded artifacts. */
        internal const val TYCHO_TRANSPORT_LOGGER = "org.eclipse.tycho.p2maven.transport.TychoRepositoryTransport"

        /**
         * A regular expression to detect log messages about downloaded artifacts. Via the groups, the ID and the
         * version of the affected artifact can be extracted.
         */
        private val regexDownload = Regex("Downloaded from p2: ((.+)/([^/]*)_(.+)\\.jar).*")

        /** The prefix for properties in the local Maven repository that store checksums for downloaded artifacts. */
        private const val CHECKSUM_PROPERTY_PREFIX = "download.checksum."

        /**
         * Perform the necessary setup in the given [container], so that the provided [tracker] receives log messages
         * about downloaded artifacts.
         */
        fun install(tracker: P2ArtifactTracker, container: PlexusContainer) {
            (container as? DefaultPlexusContainer)?.loggerManager = object : BaseLoggerManager() {
                override fun createLogger(name: String): Logger =
                    if (TYCHO_TRANSPORT_LOGGER == name) {
                        createDownloadTrackingLogger(tracker.downloadedArtifacts)
                    } else {
                        MavenLogger(logger.delegate.level)
                    }
            }.also {
                logger.info { "Installed logger for tracking P2 downloads." }
            }
        }

        /**
         * Create a special [Logger] that tracks log messages about downloaded artifacts and stores the extracted
         * information in the given [trackingMap].
         */
        private fun createDownloadTrackingLogger(trackingMap: MutableMap<String, String>): Logger {
            val mavenLogger = MavenLogger(logger.delegate.level)

            return object : Logger by mavenLogger {
                override fun info(message: String) {
                    mavenLogger.info(message)

                    regexDownload.matchEntire(message)?.also { result ->
                        val sourceUrl = result.groupValues[1]
                        val artifactId = result.groupValues[3]
                        val version = result.groupValues[4]
                        trackingMap[downloadKey(artifactId, version)] = sourceUrl

                        logger.info { "Recorded source URL '$sourceUrl' for '$artifactId:$version'." }
                    }
                }
            }
        }

        /**
         * Generate a key into the map with download information for the given [artifactId] and [version].
         */
        private fun downloadKey(artifactId: String, version: String) = "$artifactId:$version"

        /**
         * Return an [Artifact] to represent the source code bundle of the given [artifact].
         */
        private fun mapToSourceArtifact(artifact: Artifact): Artifact =
            object : Artifact by artifact {
                override fun getArtifactId(): String = "${artifact.artifactId}.source"
            }
    }

    /** The [Map] with information about downloaded artifacts. */
    private val downloadedArtifacts = ConcurrentHashMap<String, String>()

    /**
     * Construct a [RemoteArtifact] object for the binary of the given [artifact] that contains all information
     * available. If this artifact has been downloaded from a P2 repository, set the correct source URL. Also try to
     * obtain a checksum from the properties stored in the local Maven repository.
     */
    fun getBinaryArtifactFor(artifact: Artifact): RemoteArtifact =
        downloadedArtifacts[downloadKey(artifact.artifactId, artifact.version)]?.let { sourceUrl ->
            RemoteArtifact(sourceUrl, findHash(artifact))
        } ?: RemoteArtifact.EMPTY

    /**
     * Construct a [RemoteArtifact] object for the source code bundle of the given [artifact] that contains all
     * information available. This function works analogously to [getSourceArtifactFor], but looks for an artifact
     * with the extension `.source`. This is the way how Tycho handles source code bundles.
     */
    fun getSourceArtifactFor(artifact: Artifact): RemoteArtifact = getBinaryArtifactFor(mapToSourceArtifact(artifact))

    /**
     * Try to find a hash value for the given [artifact] in the local Maven repository. Use the strongest hash value
     * available. Return [Hash.NONE] if no hash value can be found.
     */
    private fun findHash(artifact: Artifact): Hash =
        repositoryHelper.p2Properties(artifact)?.let { properties ->
            properties.mapNotNull { (key, value) ->
                key.withoutPrefix(CHECKSUM_PROPERTY_PREFIX)?.let { algorithm ->
                    Hash(value, algorithm)
                }
            }.maxByOrNull { it.value.length }
        } ?: Hash.NONE
}
