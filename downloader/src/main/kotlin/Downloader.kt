/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.downloader

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

import kotlin.time.TimeSource

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.SourceCodeOrigin
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.replaceCredentialsInUri
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.common.unpack
import org.ossreviewtoolkit.utils.common.unpackTryAllTypes
import org.ossreviewtoolkit.utils.ort.createOrtTempDir
import org.ossreviewtoolkit.utils.ort.downloadFile
import org.ossreviewtoolkit.utils.ort.okHttpClient
import org.ossreviewtoolkit.utils.ort.ping

/**
 * The class to download source code. The signatures of public functions in this class define the library API.
 */
class Downloader(private val config: DownloaderConfiguration) {
    private fun verifyOutputDirectory(outputDirectory: File) {
        require(!outputDirectory.exists() || outputDirectory.walk().singleOrNull() == outputDirectory) {
            "The output directory '$outputDirectory' must not contain any files yet."
        }

        outputDirectory.safeMkdirs()
    }

    /**
     * Download the source code of the [package][pkg] to the [outputDirectory]. If [dryRun] is `true`, no actual
     * download happens but the source code is only checked to be available. A [Provenance] is returned on success or a
     * [DownloadException] is thrown in case of failure.
     */
    fun download(pkg: Package, outputDirectory: File, dryRun: Boolean = false): Provenance {
        verifyOutputDirectory(outputDirectory)

        if (pkg.isMetadataOnly) return UnknownProvenance

        val exception = DownloadException("Download failed for '${pkg.id.toCoordinates()}'.")

        val sourceCodeOrigins = pkg.sourceCodeOrigins ?: config.sourceCodeOrigins

        sourceCodeOrigins.forEach { origin ->
            val provenance = when (origin) {
                SourceCodeOrigin.VCS -> handleVcsDownload(pkg, outputDirectory, dryRun, exception)
                SourceCodeOrigin.ARTIFACT -> handleSourceArtifactDownload(pkg, outputDirectory, dryRun, exception)
            }

            if (provenance != null) return provenance
        }

        throw exception
    }

    /**
     * Try to download the source code from VCS. Returns null if the download failed and adds the suppressed exception
     * to [exception].
     */
    private fun handleVcsDownload(
        pkg: Package,
        outputDirectory: File,
        dryRun: Boolean,
        exception: DownloadException
    ): RepositoryProvenance? {
        val vcsMark = TimeSource.Monotonic.markNow()

        try {
            // Cargo in general builds from source tarballs, so prefer source artifacts over VCS, but still use VCS if
            // no source artifact is given.
            val isCargoPackageWithSourceArtifact = pkg.id.type == "Cargo" && pkg.sourceArtifact != RemoteArtifact.EMPTY

            if (!isCargoPackageWithSourceArtifact) {
                val result = downloadFromVcs(pkg, outputDirectory, dryRun = dryRun)

                logger.info {
                    "Downloaded source code for '${pkg.id.toCoordinates()}' from ${result.vcsInfo} in " +
                        "${vcsMark.elapsedNow()}."
                }

                return result
            } else {
                logger.info {
                    "Skipping VCS download for Cargo package '${pkg.id.toCoordinates()}' as it has a source artifact " +
                        "at ${pkg.sourceArtifact}."
                }
            }
        } catch (e: DownloadException) {
            logger.debug { "VCS download failed for '${pkg.id.toCoordinates()}': ${e.collectMessages()}" }

            logger.info {
                "Failed attempt to download source code for '${pkg.id.toCoordinates()}' from ${pkg.vcsProcessed} " +
                    "took ${vcsMark.elapsedNow()}."
            }

            // Clean up any left-over files.
            outputDirectory.safeDeleteRecursively(baseDirectory = outputDirectory)

            exception.addSuppressed(e)
        }

        return null
    }

    /**
     * Try to download the source code from the source artifact. Returns null if the download failed adds the
     * suppressed exception to [exception].
     */
    private fun handleSourceArtifactDownload(
        pkg: Package,
        outputDirectory: File,
        dryRun: Boolean,
        exception: DownloadException
    ): ArtifactProvenance? {
        val sourceArtifactMark = TimeSource.Monotonic.markNow()

        try {
            val result = downloadSourceArtifact(pkg.sourceArtifact, outputDirectory, dryRun)

            logger.info {
                "Downloaded source code for '${pkg.id.toCoordinates()}' from ${pkg.sourceArtifact} in " +
                    "${sourceArtifactMark.elapsedNow()}."
            }

            return result
        } catch (e: DownloadException) {
            logger.debug {
                "Source artifact download failed for '${pkg.id.toCoordinates()}': ${e.collectMessages()}"
            }

            logger.info {
                "Failed attempt to download source code for '${pkg.id.toCoordinates()}' from ${pkg.sourceArtifact} " +
                    "took ${sourceArtifactMark.elapsedNow()}."
            }

            // Clean up any left-over files.
            outputDirectory.safeDeleteRecursively(baseDirectory = outputDirectory)

            exception.addSuppressed(e)
        }

        return null
    }

    /**
     * Download the source code of the [package][pkg] to the [outputDirectory] using its VCS information. If [recursive]
     * is `true`, any nested repositories (like Git submodules or Mercurial subrepositories) are downloaded, too. If
     * [dryRun] is `true`, no actual download happens but the source code is only checked to be available. A
     * [RepositoryProvenance] is returned on success or a [DownloadException] is thrown in case of failure.
     */
    @JvmOverloads
    fun downloadFromVcs(
        pkg: Package,
        outputDirectory: File,
        recursive: Boolean = true,
        dryRun: Boolean = false
    ): RepositoryProvenance {
        if (pkg.vcsProcessed.url.isBlank()) {
            val hint = when (pkg.id.type) {
                "Bundler", "Gem" ->
                    " Please define the \"source_code_uri\" in the \"metadata\" of the Gemspec, see: " +
                        "https://guides.rubygems.org/specification-reference/#metadata"
                "Gradle" ->
                    " Please make sure the published POM file includes the SCM connection, see: " +
                        "https://docs.gradle.org/current/userguide/publishing_maven.html#" +
                        "sec:modifying_the_generated_pom"
                "Maven" ->
                    " Please define the \"connection\" tag within the \"scm\" tag in the POM file, see: " +
                        "https://maven.apache.org/pom.html#SCM"
                "NPM" ->
                    " Please define the \"repository\" in the package.json file, see: " +
                        "https://docs.npmjs.com/cli/v7/configuring-npm/package-json#repository"
                "PIP", "PyPI" ->
                    " Please make sure the setup.py defines the 'Source' attribute in 'project_urls', see: " +
                        "https://packaging.python.org/guides/distributing-packages-using-setuptools/#project-urls"
                "SBT" ->
                    " Please make sure the published POM file includes the SCM connection, see: " +
                        "https://maven.apache.org/pom.html#SCM"
                else -> ""
            }

            throw DownloadException("No VCS URL provided for '${pkg.id.toCoordinates()}'.$hint")
        }

        verifyOutputDirectory(outputDirectory)

        logger.info {
            "Trying to download '${pkg.id.toCoordinates()}' sources to '${outputDirectory.absolutePath}' from VCS..."
        }

        if (pkg.vcsProcessed != pkg.vcs) {
            logger.info { "Using processed ${pkg.vcsProcessed}. Original was ${pkg.vcs}." }
        } else {
            logger.info { "Using ${pkg.vcsProcessed}." }
        }

        var applicableVcs: VersionControlSystem? = null

        if (pkg.vcsProcessed.type != VcsType.UNKNOWN) {
            applicableVcs = VersionControlSystem.forType(pkg.vcsProcessed.type)
            logger.info {
                applicableVcs?.let {
                    "Detected VCS type '${it.type}' from type name '${pkg.vcsProcessed.type}'."
                } ?: "Could not detect VCS type from type name '${pkg.vcsProcessed.type}'."
            }
        }

        if (applicableVcs == null) {
            applicableVcs = VersionControlSystem.forUrl(pkg.vcsProcessed.url)
            logger.info {
                applicableVcs?.let {
                    "Detected VCS type '${it.type}' from URL ${pkg.vcsProcessed.url}."
                } ?: "Could not detect VCS type from URL ${pkg.vcsProcessed.url}."
            }
        }

        if (applicableVcs == null) {
            throw DownloadException("Unsupported VCS type '${pkg.vcsProcessed.type}'.")
        }

        if (dryRun) {
            // TODO: For performance reasons, the current check only works if the VCS revision is present (does not have
            //       to be guessed) and if the VCS host is recognized. The trick is to do a HTTP HEAD request on the
            //       archive download URL instead of implementing existence checks for all supported VCS. While this
            //       does not cover all cases, it works for many cases and is quite fast.
            val url = VcsHost.fromUrl(pkg.vcsProcessed.url)?.toArchiveDownloadUrl(pkg.vcsProcessed)
                ?: throw DownloadException("Unhandled VCS URL ${pkg.vcsProcessed.url}.")

            val response = runCatching { okHttpClient.ping(url) }

            if (response.getOrNull()?.code != HttpURLConnection.HTTP_OK) {
                throw DownloadException("Cannot verify existence of ${pkg.vcsProcessed}.")
            }

            return RepositoryProvenance(pkg.vcsProcessed, pkg.vcsProcessed.revision)
        }

        val workingTree = try {
            applicableVcs.download(pkg, outputDirectory, config.allowMovingRevisions, recursive)
        } catch (e: DownloadException) {
            // TODO: Introduce something like a "strict" mode and only do these kind of fallbacks in non-strict mode.
            val vcsUrlNoCredentials = pkg.vcsProcessed.url.replaceCredentialsInUri()
            if (vcsUrlNoCredentials != pkg.vcsProcessed.url) {
                // Try once more with any username / password stripped from the URL.
                logger.info {
                    "Falling back to trying to download from $vcsUrlNoCredentials which has credentials removed."
                }

                // Clean up any files left from the failed VCS download (i.e. a ".git" directory).
                outputDirectory.safeDeleteRecursively(baseDirectory = outputDirectory)

                val fallbackPkg = pkg.copy(vcsProcessed = pkg.vcsProcessed.copy(url = vcsUrlNoCredentials))
                applicableVcs.download(fallbackPkg, outputDirectory, config.allowMovingRevisions, recursive)
            } else {
                throw e
            }
        }

        val resolvedRevision = workingTree.getRevision()

        logger.info {
            "Finished downloading source code revision '$resolvedRevision' to '${outputDirectory.absolutePath}'."
        }

        return RepositoryProvenance(pkg.vcsProcessed, resolvedRevision)
    }

    /**
     * Download the [sourceArtifact] and unpack it to the [outputDirectory]. If [dryRun] is `true`, no actual download
     * happens but the source code is only checked to be available. An [ArtifactProvenance] is returned on success or a
     * [DownloadException] is thrown in case of failure.
     */
    fun downloadSourceArtifact(
        sourceArtifact: RemoteArtifact,
        outputDirectory: File,
        dryRun: Boolean = false
    ): ArtifactProvenance {
        if (sourceArtifact.url.isBlank()) {
            throw DownloadException("No source artifact URL provided.")
        }

        verifyOutputDirectory(outputDirectory)

        logger.info {
            "Trying to download source artifact from ${sourceArtifact.url}..."
        }

        // Some (Linux) file URIs do not start with "file://" but look like "file:/opt/android-sdk-linux".
        val isLocalFileUrl = sourceArtifact.url.startsWith("file:/")

        var tempDir: File? = null

        val sourceArchive = if (isLocalFileUrl) {
            File(URI(sourceArtifact.url))
        } else {
            tempDir = createOrtTempDir()
            okHttpClient.downloadFile(sourceArtifact.url, tempDir).getOrElse {
                tempDir.safeDeleteRecursively()
                throw DownloadException("Failed to download source artifact from ${sourceArtifact.url}.", it)
            }
        }

        if (dryRun) {
            if (sourceArchive.isFile) return ArtifactProvenance(sourceArtifact)

            val response = okHttpClient.ping(sourceArtifact.url)

            if (response.code != HttpURLConnection.HTTP_OK) {
                throw DownloadException("Cannot verify existence of ${sourceArtifact.url}.")
            }

            return ArtifactProvenance(sourceArtifact)
        }

        if (sourceArtifact.hash.algorithm != HashAlgorithm.NONE) {
            if (sourceArtifact.hash.algorithm == HashAlgorithm.UNKNOWN) {
                logger.warn {
                    "Cannot verify source artifact with ${sourceArtifact.hash}, skipping verification."
                }
            } else if (!sourceArtifact.hash.verify(sourceArchive)) {
                tempDir?.safeDeleteRecursively()
                throw DownloadException("Source artifact does not match expected ${sourceArtifact.hash}.")
            }
        }

        try {
            if (sourceArchive.extension == "gem") {
                // Unpack the nested data archive for Ruby Gems.
                val gemDirectory = createOrtTempDir("gem")
                val dataFile = gemDirectory / "data.tar.gz"

                try {
                    sourceArchive.unpack(gemDirectory)
                    dataFile.unpack(outputDirectory)
                } finally {
                    gemDirectory.safeDeleteRecursively()
                }
            } else {
                sourceArchive.unpackTryAllTypes(outputDirectory)
            }
        } catch (e: IOException) {
            logger.error {
                "Could not unpack source artifact '${sourceArchive.absolutePath}': ${e.collectMessages()}"
            }

            tempDir?.safeDeleteRecursively()
            throw DownloadException(e)
        }

        logger.info {
            "Successfully unpacked ${sourceArtifact.url} to '${outputDirectory.absolutePath}'..."
        }

        tempDir?.safeDeleteRecursively()
        return ArtifactProvenance(sourceArtifact)
    }
}

/**
 * Consolidate [projects] based on their VcsInfo without taking the path into account. As VcsInfo is stored per project
 * but many project definition files actually reside in different subdirectories of the same VCS working tree, it does
 * not make sense to download (and scan) all of them individually, not even if doing sparse checkouts. Return a map that
 * associates packages for projects in distinct VCS working trees with all other projects from the same VCS working
 * tree.
 */
fun consolidateProjectPackagesByVcs(projects: Collection<Project>): Map<Package, List<Package>> {
    // TODO: In case of GitRepo, we still download the whole GitRepo working tree *and* any individual Git
    //       repositories that contain project definition files, which in many cases is doing duplicate work.
    val projectPackages = projects.map { it.toPackage() }
    val projectPackagesByVcs = projectPackages.groupBy { it.vcsProcessed.copy(path = "") }

    return projectPackagesByVcs.entries.associate { (sameVcs, projectsWithSameVcs) ->
        // Find the original project which has the empty path, if any, or simply take the first project
        // and clear the path unless it is a GitRepo project (where the path refers to the manifest).
        val referencePackage = projectsWithSameVcs.find { it.vcsProcessed.path.isEmpty() }
            ?: projectsWithSameVcs.first()

        val otherPackages = (projectsWithSameVcs - referencePackage).map { it.copy(vcsProcessed = sameVcs) }

        Pair(referencePackage.copy(vcsProcessed = sameVcs), otherPackages)
    }
}
