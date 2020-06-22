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

package org.ossreviewtoolkit.downloader

import org.ossreviewtoolkit.downloader.vcs.GitRepo
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.OkHttpClientHelper
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.safeDeleteRecursively
import org.ossreviewtoolkit.utils.safeMkdirs
import org.ossreviewtoolkit.utils.stripCredentialsFromUrl
import org.ossreviewtoolkit.utils.unpack

import java.io.File
import java.io.IOException
import java.net.URI
import java.time.Instant

import okhttp3.Request

import okio.buffer
import okio.sink

/**
 * The class to download source code. The signatures of public functions in this class define the library API.
 */
object Downloader {
    /**
     * The choice of data entities to download.
     */
    enum class DataEntity {
        /**
         * Identifier for package entities.
         */
        PACKAGES,

        /**
         * Identifier for project entities.
         */
        PROJECTS;
    }

    /**
     * This class describes what was downloaded by any of the download functions.
     */
    data class DownloadResult(
        /**
         * The date and time when the download was started.
         */
        val dateTime: Instant,

        /**
         * The directory to which files were downloaded.
         */
        val downloadDirectory: File,

        /**
         * The source artifact that was downloaded, or null if the download was performed from a [VCS][vcsInfo].
         */
        val sourceArtifact: RemoteArtifact? = null,

        /**
         * Information about the VCS from which was downloaded, or null if a [source artifact][sourceArtifact] was
         * downloaded.
         */
        val vcsInfo: VcsInfo? = null,

        /**
         * The original VCS information that was passed to the download function. It can be different to [vcsInfo] if
         * any automatic detection took place.
         */
        val originalVcsInfo: VcsInfo? = null
    ) {
        init {
            require((sourceArtifact == null) != (vcsInfo == null)) {
                "Either sourceArtifact or vcsInfo must be set, but not both."
            }
        }
    }

    /**
     * Download the source code of the [target] package to a sub-directory inside [outputDirectory]. The sub-directory
     * hierarchy is inferred from the [name][Identifier.name] and [version][Identifier.version] of the package. The
     * [allowMovingRevisions] parameter indicates whether VCS downloads accept symbolic names, like branches, instead of
     * only fixed revisions. A [DownloadResult] is returned on success or a [DownloadException] is thrown in case of
     * failure.
     */
    fun download(target: Package, outputDirectory: File, allowMovingRevisions: Boolean = false): DownloadResult {
        log.info { "Trying to download source code for '${target.id.toCoordinates()}'." }

        require(!outputDirectory.exists() || outputDirectory.list().isEmpty()) {
            "The output directory '$outputDirectory' must not contain any files yet."
        }

        outputDirectory.apply { safeMkdirs() }

        val exception = DownloadException("Download failed for '${target.id.toCoordinates()}'.")

        // Try downloading from VCS.
        try {
            // Cargo in general builds from source tarballs, so we prefer source artifacts to VCS.
            if (target.id.type != "Cargo" || target.sourceArtifact == RemoteArtifact.EMPTY) {
                return downloadFromVcs(target, outputDirectory, allowMovingRevisions)
            } else {
                log.info { "Skipping VCS download for Cargo package '${target.id.toCoordinates()}'." }
            }
        } catch (e: DownloadException) {
            log.debug { "VCS download failed for '${target.id.toCoordinates()}': ${e.collectMessagesAsString()}" }

            // Clean up any left-over files (force to delete read-only files in ".git" directories on Windows).
            outputDirectory.safeDeleteRecursively(force = true)
            outputDirectory.safeMkdirs()

            exception.addSuppressed(e)
        }

        // Try downloading the source artifact.
        try {
            return downloadSourceArtifact(target, outputDirectory)
        } catch (e: DownloadException) {
            log.debug {
                "Source artifact download failed for '${target.id.toCoordinates()}': ${e.collectMessagesAsString()}"
            }

            // Clean up any left-over files.
            outputDirectory.safeDeleteRecursively()
            outputDirectory.safeMkdirs()

            exception.addSuppressed(e)
        }

        throw exception
    }

    private fun downloadFromVcs(target: Package, outputDirectory: File, allowMovingRevisions: Boolean): DownloadResult {
        log.info {
            "Trying to download '${target.id.toCoordinates()}' sources to '${outputDirectory.absolutePath}' from VCS..."
        }

        if (target.vcsProcessed.url.isBlank()) {
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

            throw DownloadException("No VCS URL provided for '${target.id.toCoordinates()}'.$hint")
        }

        if (target.vcsProcessed != target.vcs) {
            log.info { "Using processed ${target.vcsProcessed}. Original was ${target.vcs}." }
        } else {
            log.info { "Using ${target.vcsProcessed}." }
        }

        var applicableVcs: VersionControlSystem? = null

        if (target.vcsProcessed.type != VcsType.NONE) {
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
            //       non-strict mode.
            val vcsUrlNoCredentials = target.vcsProcessed.url.stripCredentialsFromUrl()
            if (vcsUrlNoCredentials != target.vcsProcessed.url) {
                // Try once more with any user name / password stripped from the URL.
                log.info {
                    "Falling back to trying to download from $vcsUrlNoCredentials which has credentials removed."
                }

                // Clean up any files left from the failed VCS download (i.e. a ".git" directory).
                outputDirectory.safeDeleteRecursively(force = true)
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
            path = target.vcsProcessed.path
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
                OkHttpClientHelper.execute(request).use { response ->
                    val body = response.body
                    if (!response.isSuccessful || body == null) {
                        throw DownloadException("Failed to download source artifact: $response")
                    }

                    // Use the filename from the request for the last redirect.
                    val tempFileName = response.request.url.pathSegments.last()
                    createTempFile(ORT_NAME, tempFileName).also { tempFile ->
                        tempFile.sink().buffer().use { it.writeAll(body.source()) }
                        tempFile.deleteOnExit()
                    }
                }
            } catch (e: IOException) {
                throw DownloadException("Failed to download source artifact.", e)
            }
        }

        if (!target.sourceArtifact.hash.canVerify) {
            log.warn {
                "Cannot verify source artifact hash ${target.sourceArtifact.hash}, skipping verification."
            }
        } else if (!target.sourceArtifact.hash.verify(sourceArchive)) {
            throw DownloadException(
                "Source artifact does not match expected ${target.sourceArtifact.hash.algorithm} hash " +
                        "'${target.sourceArtifact.hash.value}'."
            )
        }

        try {
            if (sourceArchive.extension == "gem") {
                // Unpack the nested data archive for Ruby Gems.
                val gemDirectory = createTempDir(ORT_NAME, "gem")
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
            log.error {
                "Could not unpack source artifact '${sourceArchive.absolutePath}': ${e.collectMessagesAsString()}"
            }
            throw DownloadException(e)
        }

        log.info {
            "Successfully downloaded source artifact for '${target.id.toCoordinates()}' to " +
                    "'${outputDirectory.absolutePath}'..."
        }

        return DownloadResult(startTime, outputDirectory, sourceArtifact = target.sourceArtifact)
    }
}

/**
 * Consolidate [projects] based on their VcsInfo without taking the path into account. As we store VcsInfo per project
 * but many project definition files actually reside in different sub-directories of the same VCS working tree, it does
 * not make sense to download (and scan) all of them individually, not even if doing sparse checkouts. Return a map that
 * associates packages for projects in distinct VCS working trees with all other projects from the same VCS working
 * tree.
 */
fun consolidateProjectPackagesByVcs(projects: Collection<Project>): Map<Package, List<Package>> {
    // TODO: In case of GitRepo, we still download the whole GitRepo working tree *and* any individual Git
    //       repositories that contain project definition files, which in many cases is doing duplicate work.
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
