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

package com.here.ort.downloader

import ch.frankel.slf4k.*

import com.here.ort.downloader.vcs.GitRepo
import com.here.ort.model.Hash
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.Project
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.VcsInfo
import com.here.ort.utils.OkHttpClientHelper
import com.here.ort.utils.collectMessages
import com.here.ort.utils.hash
import com.here.ort.utils.log
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.safeMkdirs
import com.here.ort.utils.stripCredentialsFromUrl
import com.here.ort.utils.unpack

import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URL
import java.time.Instant
import java.util.SortedSet

import okhttp3.Request

import okio.Okio

const val TOOL_NAME = "downloader"
const val HTTP_CACHE_PATH = "$TOOL_NAME/cache/http"

/**
 * The class to download source code. The signatures of public functions in this class define the library API.
 */
class Downloader {
    companion object {
        /**
         * Consolidate projects based on their VcsInfo without taking the path into account. As we store VcsInfo per
         * project but many project definition files actually reside in different sub-directories of the same VCS
         * working tree, it does not make sense to download (and scan) all of them individually, not even if doing
         * sparse checkouts.
         *
         * @param projects A set of projects to consolidate into packages.
         * @return A map that associates packages for projects with distinct VCS working trees to all other projects
         *         from the same VCS working tree.
         */
        fun consolidateProjectPackagesByVcs(projects: SortedSet<Project>): Map<Package, List<Package>> {
            // TODO: In case of GitRepo, we still download the whole GitRepo working tree *and* any individual
            // Git repositories that contain project definition files, which in many cases is doing duplicate
            // work.
            val projectPackages = projects.map { it.toPackage() }
            val projectPackagesByVcs = projectPackages.groupBy {
                if (it.vcsProcessed.type == GitRepo().type) {
                    it.vcsProcessed
                } else {
                    it.vcsProcessed.copy(path = "")
                }
            }

            return projectPackagesByVcs.entries.associate { (sameVcs, projectsWithSameVcs) ->
                // Find the original project which has the empty path, if any, or simply take the first project
                // and clear the path unless it is a GitRepo project (where the path refers to the manifest).
                val referencePackage = projectsWithSameVcs.find { it.vcsProcessed.path.isEmpty() }
                        ?: projectsWithSameVcs.first()

                val otherPackages = (projectsWithSameVcs - referencePackage).map { it.copy(vcsProcessed = sameVcs) }

                Pair(referencePackage.copy(vcsProcessed = sameVcs), otherPackages)
            }
        }
    }

    /**
     * The choice of data entities to download.
     */
    enum class DataEntity {
        PACKAGES,
        PROJECT;
    }

    /**
     * This class describes what was downloaded by [download] to the [downloadDirectory] or if any exception occured.
     * Either [sourceArtifact] or [vcsInfo] is set to a non-null value. The download was started at [dateTime].
     */
    data class DownloadResult(
            val dateTime: Instant,
            val downloadDirectory: File,
            val sourceArtifact: RemoteArtifact? = null,
            val vcsInfo: VcsInfo? = null,
            val originalVcsInfo: VcsInfo? = null
    ) {
        init {
            require((sourceArtifact == null) != (vcsInfo == null)) {
                "Either sourceArtifact or vcsInfo must be set, but not both."
            }
        }
    }

    /**
     * Download the source code of the [target] package to a folder inside [outputDirectory]. The folder name is created
     * from the [name][Identifier.name] and [version][Identifier.version] of the [target] package [id][Package.id].
     *
     * @param target The description of the package to download.
     * @param outputDirectory The parent directory to download the source code to.
     * @param allowMovingRevisions Indicate whether VCS downloads may use symbolic names to moving revisions.
     *
     * @return The [DownloadResult].
     *
     * @throws DownloadException In case the download failed.
     */
    fun download(target: Package, outputDirectory: File, allowMovingRevisions: Boolean = false): DownloadResult {
        log.info { "Trying to download source code for '${target.id.toCoordinates()}'." }

        val targetDir = File(outputDirectory, target.id.toPath()).apply { safeMkdirs() }

        require(!targetDir.exists() || targetDir.list().isEmpty()) {
            "The output directory '$targetDir' must not contain any files yet."
        }

        var previousException: DownloadException? = null

        // Try downloading from VCS.
        try {
            return downloadFromVcs(target, targetDir, allowMovingRevisions)
        } catch (e: DownloadException) {
            val message = if (target.vcsProcessed.url.isBlank()) {
                val hint = when (target.id.type) {
                    "Bundler" -> " Please define the \"source_code_uri\" in the \"metadata\" of the Gemspec, " +
                            "see: https://guides.rubygems.org/specification-reference/#metadata"
                    "Gradle" -> " Please make sure the release POM file includes the SCM connection, see: " +
                            "https://docs.gradle.org/current/userguide/publishing_maven.html#" +
                            "example_customizing_the_pom_file"
                    "Maven" -> " Please define the \"connection\" tag within the \"scm\" tag in the POM file, " +
                            "see: http://maven.apache.org/pom.html#SCM"
                    "NPM" -> " Please define the \"repository\" in the package.json file, see: " +
                            "https://docs.npmjs.com/files/package.json#repository"
                    "PIP", "PyPI" -> " Please make sure the setup.py defines the 'Source' attribute in " +
                            "'project_urls', see: https://packaging.python.org/guides/" +
                            "distributing-packages-using-setuptools/#project-urls"
                    "SBT" -> " Please make sure the released POM file includes the SCM connection, see: " +
                            "http://maven.apache.org/pom.html#SCM"
                    else -> ""
                }
                e.message + hint
            } else {
                e.message
            }

            log.debug { "VCS download failed for '${target.id.toCoordinates()}': ${message}" }
            previousException = DownloadException(message, previousException)

            // Clean up any left-over files.
            targetDir.safeDeleteRecursively()
            targetDir.safeMkdirs()
        }

        // Try downloading the source artifact.
        try {
            return downloadSourceArtifact(target, targetDir)
        } catch (e: DownloadException) {
            log.debug { "Source artifact download failed for '${target.id.toCoordinates()}': ${e.message}" }

            // Clean up any left-over files.
            targetDir.safeDeleteRecursively()
            targetDir.safeMkdirs()

            e.initCause(previousException)
            previousException = e
        }

        // Try downloading the Maven POM.
        if (target.id.type == "Maven") {
            try {
                return downloadPomArtifact(target, targetDir)
            } catch (e: DownloadException) {
                log.debug { "POM artifact download failed for '${target.id.toCoordinates()}': ${e.message}" }

                // Clean up any left-over files.
                targetDir.safeDeleteRecursively()
                targetDir.safeMkdirs()

                e.initCause(previousException)
                previousException = e
            }
        }

        // By now we know there must have been a previous exception, otherwise we would have returned earlier.
        throw previousException!!
    }

    private fun downloadFromVcs(target: Package, outputDirectory: File, allowMovingRevisions: Boolean): DownloadResult {
        log.info {
            "Trying to download '${target.id.toCoordinates()}' sources to '${outputDirectory.absolutePath}' from VCS..."
        }

        if (target.vcsProcessed.url.isBlank()) {
            throw DownloadException("No VCS URL provided for '${target.id.toCoordinates()}'.")
        }

        if (target.vcsProcessed != target.vcs) {
            log.info { "Using processed ${target.vcsProcessed}. Original was ${target.vcs}." }
        } else {
            log.info { "Using ${target.vcsProcessed}." }
        }

        var applicableVcs: VersionControlSystem? = null

        if (target.vcsProcessed.type.isNotBlank()) {
            applicableVcs = VersionControlSystem.forType(target.vcsProcessed.type)
            log.info {
                applicableVcs?.let {
                    "Detected VCS type '${it.type}' from type name '${target.vcsProcessed.type}'."
                } ?: "Could not detect VCS type from type name '${target.vcsProcessed.type}'."
            }
        }

        if (applicableVcs == null) {
            applicableVcs = VersionControlSystem.forUrl(target.vcsProcessed.url)
            log.info {
                applicableVcs?.let {
                    "Detected VCS type '${it.type}' from URL '${target.vcsProcessed.url}'."
                } ?: "Could not detect VCS type from URL '${target.vcsProcessed.url}'."
            }
        }

        if (applicableVcs == null) {
            throw DownloadException("Unsupported VCS type '${target.vcsProcessed.type}'.")
        }

        val startTime = Instant.now()
        val workingTree = try {
            applicableVcs.download(target, outputDirectory, allowMovingRevisions)
        } catch (e: DownloadException) {
            // TODO: We should introduce something like a "strict" mode and only do these kind of fallbacks in
            // non-strict mode.
            val vcsUrlNoCredentials = target.vcsProcessed.url.stripCredentialsFromUrl()
            if (vcsUrlNoCredentials != target.vcsProcessed.url) {
                // Try once more with any user name / password stripped from the URL.
                log.info {
                    "Falling back to trying to download from $vcsUrlNoCredentials which has credentials removed."
                }

                // Clean up any files left from the failed VCS download (i.e. a ".git" directory).
                outputDirectory.safeDeleteRecursively()
                outputDirectory.safeMkdirs()

                val fallbackTarget = target.copy(vcsProcessed = target.vcsProcessed.copy(url = vcsUrlNoCredentials))
                applicableVcs.download(fallbackTarget, outputDirectory, allowMovingRevisions)
            } else {
                throw e
            }
        }
        val revision = workingTree.getRevision()

        log.info { "Finished downloading source code revision '$revision' to '${outputDirectory.absolutePath}'." }

        val vcsInfo = VcsInfo(
                type = applicableVcs.type,
                url = target.vcsProcessed.url,
                revision = target.vcsProcessed.revision.takeIf { it.isNotBlank() } ?: revision,
                resolvedRevision = revision,
                path = target.vcsProcessed.path // TODO: Needs to check if the VCS used the sparse checkout.
        )
        return DownloadResult(startTime, outputDirectory, vcsInfo = vcsInfo,
                originalVcsInfo = target.vcsProcessed.takeIf { it != vcsInfo })
    }

    private fun downloadSourceArtifact(target: Package, outputDirectory: File): DownloadResult {
        log.info {
            "Trying to download source artifact for '${target.id.toCoordinates()}' from ${target.sourceArtifact.url}..."
        }

        if (target.sourceArtifact.url.isBlank()) {
            throw DownloadException("No source artifact URL provided for '${target.id.toCoordinates()}'.")
        }

        val startTime = Instant.now()

        // Some (Linux) file URIs do not start with "file://" but look like "file:/opt/android-sdk-linux".
        val sourceArchive = if (target.sourceArtifact.url.startsWith("file:/")) {
            File(URI(target.sourceArtifact.url))
        } else {
            val request = Request.Builder()
                    // Disable transparent gzip, otherwise we might end up writing a tar file to disk and expecting to
                    // find a tar.gz file, thus failing to unpack the archive.
                    // See https://github.com/square/okhttp/blob/parent-3.10.0/okhttp/src/main/java/okhttp3/internal/ \
                    // http/BridgeInterceptor.java#L79
                    .addHeader("Accept-Encoding", "identity")
                    .get()
                    .url(target.sourceArtifact.url)
                    .build()

            try {
                OkHttpClientHelper.execute(HTTP_CACHE_PATH, request).use { response ->
                    val body = response.body()
                    if (!response.isSuccessful || body == null) {
                        throw DownloadException("Failed to download source artifact: $response")
                    }

                    createTempFile("ort", target.sourceArtifact.url.substringAfterLast("/")).also { tempFile ->
                        Okio.buffer(Okio.sink(tempFile)).use { it.writeAll(body.source()) }
                        tempFile.deleteOnExit()
                    }
                }
            } catch (e: IOException) {
                throw DownloadException("Failed to download source artifact: ${e.collectMessages()}", e)
            }
        }

        if (target.sourceArtifact.hash.isEmpty()) {
            log.warn { "Source artifact has no hash, skipping verification." }
        } else if (!Hash(target.sourceArtifact.hashAlgorithm, target.sourceArtifact.hash).verify(sourceArchive)) {
            throw DownloadException("Source artifact does not match expected ${target.sourceArtifact.hashAlgorithm} " +
                    "hash '${target.sourceArtifact.hash}'.")
        }

        try {
            if (sourceArchive.extension == "gem") {
                // Unpack the nested data archive for Ruby Gems.
                val gemDirectory = createTempDir("ort", "gem")
                val dataFile = File(gemDirectory, "data.tar.gz")

                try {
                    sourceArchive.unpack(gemDirectory)
                    dataFile.unpack(outputDirectory)
                } finally {
                    if (!gemDirectory.deleteRecursively()) {
                        log.warn { "Unable to delete temporary directory '$gemDirectory'." }
                    }
                }
            } else {
                sourceArchive.unpack(outputDirectory)
            }
        } catch (e: IOException) {
            log.error { "Could not unpack source artifact '${sourceArchive.absolutePath}': ${e.message}" }
            throw DownloadException(e)
        }

        log.info {
            "Successfully downloaded source artifact for '${target.id.toCoordinates()}' to " +
                    "'${outputDirectory.absolutePath}'..."
        }

        return DownloadResult(startTime, outputDirectory, sourceArtifact = target.sourceArtifact)
    }

    private fun downloadPomArtifact(target: Package, outputDirectory: File): DownloadResult {
        val pomFilename = "${target.id.name}-${target.id.version}.pom"
        val pomUrl = target.binaryArtifact.url.replaceAfterLast('/', pomFilename)

        if (pomUrl.isEmpty()) {
            // TODO: Investigate why the binary artifact URL is actually empty and update the implementation according
            // to root cause.
            throw DownloadException("Binary artifact URL for '${target.id.toCoordinates()}' is empty.")
        }

        log.info {
            "Trying to download POM artifact for '${target.id.toCoordinates()}' from $pomUrl..."
        }

        return try {
            val startTime = Instant.now()

            val pomFile = File(outputDirectory, pomFilename)
            pomFile.writeBytes(URL(pomUrl).readBytes())

            val pomArtifact = RemoteArtifact(
                    url = pomUrl,
                    hash = pomFile.hash(target.binaryArtifact.hashAlgorithm.toString()),
                    hashAlgorithm = target.binaryArtifact.hashAlgorithm
            )

            DownloadResult(startTime, outputDirectory, sourceArtifact = pomArtifact)
        } catch (e: IOException) {
            throw DownloadException("Failed to download the Maven POM for '${target.id.toCoordinates()}': ${e.message}")
        }
    }
}
