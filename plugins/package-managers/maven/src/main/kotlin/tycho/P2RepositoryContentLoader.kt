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
import java.net.URI

import kotlin.io.path.createTempDirectory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.common.unpack
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.ort.HttpDownloadError
import org.ossreviewtoolkit.utils.ort.createOrtTempDir
import org.ossreviewtoolkit.utils.ort.downloadFile
import org.ossreviewtoolkit.utils.ort.okHttpClient
import org.ossreviewtoolkit.utils.ort.runBlocking

/**
 * A class for downloading the XML documents listing the contained components from P2 repositories.
 *
 * A P2 repository contains an _artifacts.xml_ file with information about the components that are stored in this
 * directory. This document is available either as plain XML or compressed as a jar. It is also possible that a
 * repository references other child repositories; in this case, it contains a _compositeArtifacts.xml_ file listing
 * the children.
 *
 * This class handles these cases. It generates information about all the artifacts available within the repositories
 * referenced from a build. This is used to generate correct binary and source artifacts for the detected packages.
 */
internal class P2RepositoryContentLoader : AutoCloseable {
    companion object {
        /** The prefix for properties in the local Maven repository that store checksums for downloaded artifacts. */
        private const val CHECKSUM_PROPERTY_PREFIX = "download.checksum."

        /**
         * Download the content files of all the repositories from the given list of [repositoryUrls]. Handle
         * composite repositories by recursively loading the content of the child repositories. Handle errors by
         * creating corresponding [Issue] objects.
         */
        fun loadAllRepositoryContents(
            repositoryUrls: Collection<String>
        ): Pair<List<P2RepositoryContent>, List<Issue>> =
            P2RepositoryContentLoader().use { it.download(repositoryUrls) }

        /**
         * Download the table of contents of the P2 repository at the given [baseUrl] and return a
         * [P2RepositoryContent] object with this information. Use the given [tempDir] for storing temporary files.
         */
        internal fun loadRepositoryContent(tempDir: File, baseUrl: String): Result<P2RepositoryContent> =
            runCatching {
                val repositoryUrl = baseUrl.removeSuffix("/")
                val downloadDir = createTempDirectory(tempDir.toPath()).toFile()

                downloadCompressedFile(repositoryUrl, "artifacts", downloadDir).map { artifactsFile ->
                    artifactsFile?.let {
                        P2RepositoryContent(
                            baseUrl = repositoryUrl,
                            artifacts = parseArtifactsFile(it),
                            childRepositories = emptySet()
                        )
                    } ?: P2RepositoryContent(repositoryUrl, emptyMap(), emptySet())
                }.mapCatching { content ->
                    downloadCompressedFile(repositoryUrl, "compositeArtifacts", downloadDir)
                        .map { compositeArtifactsFile ->
                            compositeArtifactsFile?.let {
                                content.copy(childRepositories = parseCompositeArtifacts(baseUrl, it))
                            } ?: content
                        }.getOrThrow()
                }.getOrThrow()
            }

        /**
         * Download the XML file [fileName] from the repository with the given [baseUrl] to the given [tempDir] which
         * may be compressed as a jar. Try both variants, the compressed and the plain XML file. If neither can be
         * found, return a [Result] with *null*. In case of an error, return the corresponding failure [Result].
         */
        private fun downloadCompressedFile(baseUrl: String, fileName: String, tempDir: File): Result<File?> =
            downloadOptionalFile("$baseUrl/$fileName.jar", tempDir).mapCatching { jarFile ->
                jarFile?.let {
                    logger.info { "Extracting artifacts information from '$fileName.jar' for repository '$baseUrl'." }
                    it.unpack(tempDir)
                    tempDir.resolve("$fileName.xml")
                } ?: downloadOptionalFile("$baseUrl/$fileName.xml", tempDir).getOrThrow()
            }

        /**
         * Try to download a file defined by the given [url] to the given [tempDir] and return a corresponding
         * [Result]. If the download fails with a 404 response, return *null* instead of an error.
         */
        private fun downloadOptionalFile(url: String, tempDir: File): Result<File?> {
            logger.info { "Trying to download '$url'." }

            return okHttpClient.downloadFile(url, tempDir).recoverCatching { exception ->
                if (exception is HttpDownloadError && exception.code == 404) {
                    null
                } else {
                    throw exception
                }
            }
        }

        /**
         * Parse the given [file] with information about the artifacts contained in a P2 repository. Return a [Map]
         * that assigns the bundle identifiers of the artifacts to their hashes.
         */
        private fun parseArtifactsFile(file: File): Map<P2Identifier, Hash> {
            val handler = ElementHandler(ParseArtifactsState())
                .handleElement("artifact") { state, attributes, _ ->
                    state.withCurrentArtifactId(
                        P2Identifier(
                            bundleId = bundleIdentifier(attributes.getValue("id"), attributes.getValue("version")),
                            classifier = attributes.getValue("classifier")
                        )
                    )
                }.handleElement("property") { state, attributes, _ ->
                    state.withProperty(attributes.getValue("name"), attributes.getValue("value"))
                }

            val result = parseXml(file, handler)

            return result.properties.entries.associate { (artifactId, properties) ->
                artifactId to findHash(properties)
            }
        }

        /**
         * Parse the given [file] with information about the child repositories referenced by the P2 repository with
         * the given [baseUrl]. Return a [Set] with the URLs of the found child repositories.
         */
        private fun parseCompositeArtifacts(baseUrl: String, file: File): Set<String> {
            logger.info { "Parsing composite artifacts information for repository '$baseUrl'." }

            val handler = ElementHandler(ParseCompositeArtifactsState(URI.create("$baseUrl/compositeArtifacts.xml")))
                .handleElement("child") { state, attributes, _ ->
                    state.withChildRepository(attributes.getValue("location"))
                }

            val result = parseXml(file, handler)

            return result.childRepositories
        }

        /**
         * Try to find a hash value from the given [properties] of an artifact. Use the strongest hash value
         * available. Return [Hash.NONE] if no hash value can be found.
         */
        private fun findHash(properties: Map<String, String>): Hash =
            properties.mapNotNull { (key, value) ->
                key.withoutPrefix(CHECKSUM_PROPERTY_PREFIX)?.let { algorithm ->
                    Hash(value, algorithm)
                }
            }.maxByOrNull { it.value.length } ?: Hash.NONE

        /**
         * Generate an identifier for an OSGi bundle artifact based on the given [artifactId] and [version].
         */
        private fun bundleIdentifier(artifactId: String, version: String) = "$artifactId:$version"
    }

    /** The directory in which all downloads are stored. */
    private val downloadDir = createOrtTempDir()

    /** The list of contents from the processed repositories. */
    private val repositoryContents = mutableListOf<P2RepositoryContent>()

    /** The list of issues that occurred while processing the repositories. */
    private val issues = mutableListOf<Issue>()

    /**
     * A set for storing the repository URLs encountered so far. This is used to avoid processing the same repository
     * multiple times and to handle cycles in the repository references.
     */
    private val knownRepositories = mutableSetOf<String>()

    /** A mutex to synchronize access to the shared state. */
    private val mutex = Mutex()

    override fun close() {
        downloadDir.safeDeleteRecursively()
    }

    /**
     * Download the content files of all the repositories from the given list of [repositoryUrls] and the child
     * repositories they may reference. Return a list with the resulting [P2RepositoryContent] objects and the
     * issues that occurred during the download.
     */
    private fun download(repositoryUrls: Collection<String>): Pair<List<P2RepositoryContent>, List<Issue>> =
        runBlocking(Dispatchers.IO + SupervisorJob()) {
            repositoryUrls.forEach { startRepositoryDownload(this, it) }

            repositoryContents to issues
        }

    /**
     * Starts the download of artifact information for the given [repositoryUrl] asynchronously.
     */
    private suspend fun startRepositoryDownload(scope: CoroutineScope, repositoryUrl: String) {
        val repositoryIsUnknown = mutex.withLock { knownRepositories.add(repositoryUrl) }

        if (repositoryIsUnknown) {
            scope.launch {
                downloadRepository(scope, repositoryUrl)
            }
        }
    }

    /**
     * Handles the download of the artifact information for the given [repositoryUrl].
     */
    private suspend fun downloadRepository(scope: CoroutineScope, repositoryUrl: String) {
        loadRepositoryContent(downloadDir, repositoryUrl).onSuccess { content ->
            logger.info { "Found ${content.artifacts.size} artifacts in repository '$repositoryUrl'." }

            content.childRepositories.forEach { startRepositoryDownload(scope, it) }

            val issue = content.takeIf { it.artifacts.isEmpty() && it.childRepositories.isEmpty() }?.let {
                createAndLogIssue(
                    "Tycho",
                    "The P2 repository at '$repositoryUrl' does not contain any artifacts or child repositories.",
                    Severity.WARNING
                )
            }

            mutex.withLock {
                repositoryContents += content
                issue?.let { issues += it }
            }
        }.onFailure { exception ->
            val issue = createAndLogIssue(
                "Tycho",
                "Failed to load P2 repository content from '$repositoryUrl': ${exception.message}",
                Severity.ERROR
            )

            mutex.withLock { issues += issue }
        }
    }
}

/**
 * A data class to represent a unique identifier for an artifact downloaded from a P2 repository. It consists of an
 * actual identifier for the artifact plus a classifier which determines the artifact type.
 */
internal data class P2Identifier(
    /** The identifier for this artifact (which also includes the version). */
    val bundleId: String,

    /** The classifier for this artifact. */
    val classifier: String = "osgi.bundle"
)

/**
 * A data class storing information about a P2 repository including the artifacts it contains.
 */
internal data class P2RepositoryContent(
    /** The base URL of this repository. */
    val baseUrl: String,

    /**
     * A map with the artifacts contained in this repository. The keys are the bundle identifiers of the artifacts, the
     * values are their hashes.
     */
    val artifacts: Map<P2Identifier, Hash>,

    /** A set with the URLs of child repositories that are referenced from this repository. */
    val childRepositories: Set<String>
)

/**
 * A data class to hold the state while parsing the document of artifacts and their properties contained in a P2
 * repository.
 */
private data class ParseArtifactsState(
    /** The properties of the current artifact. */
    val currentProperties: MutableMap<String, String> = mutableMapOf(),

    /** A [Map] with the aggregated properties of all encountered artifacts. */
    val properties: MutableMap<P2Identifier, Map<String, String>> = mutableMapOf()
) {
    /**
     * Return an updated [ParseArtifactsState] instance that has the given [artifactId] set as current artifact.
     */
    fun withCurrentArtifactId(artifactId: P2Identifier?): ParseArtifactsState {
        artifactId?.also { id ->
            properties[id] = HashMap(currentProperties)
        }

        currentProperties.clear()
        return this
    }

    /**
     * Return an updated [ParseArtifactsState] instance that stores the property defined by the given [key] and [value]
     * for the current artifact.
     */
    fun withProperty(key: String, value: String): ParseArtifactsState {
        currentProperties[key] = value

        return this
    }
}

/**
 * A data class to hold the state while parsing the composite artifacts document of a P2 repository. In this
 * document, the repository lists the child repositories it references.
 */
private data class ParseCompositeArtifactsState(
    /** The base URL of the repository. */
    val baseUrl: URI,

    /** A set with the URLs of the referenced child repositories. */
    val childRepositories: MutableSet<String> = mutableSetOf()
) {
    /**
     * Return an updated [ParseCompositeArtifactsState] instance that includes the given [url] as a child repository.
     */
    fun withChildRepository(url: String): ParseCompositeArtifactsState {
        childRepositories.add(baseUrl.resolve(url).normalize().toString())
        return this
    }
}
