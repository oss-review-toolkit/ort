/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
 * Copyright (C) 2021-2022 Bosch.IO GmbH
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
import java.net.URI

import kotlin.time.TimeSource

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
import org.ossreviewtoolkit.utils.common.replaceCredentialsInUri
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.common.unpack
import org.ossreviewtoolkit.utils.common.unpackTryAllTypes
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper
import org.ossreviewtoolkit.utils.ort.createOrtTempDir
import org.ossreviewtoolkit.utils.ort.log

/**
 * The class to download source code. The signatures of public functions in this class define the library API.
 */
class Downloader(private val config: DownloaderConfiguration) {
    private fun verifyOutputDirectory(outputDirectory: File) {
        require(!outputDirectory.exists() || outputDirectory.list().isEmpty()) {
            "The output directory '$outputDirectory' must not contain any files yet."
        }

        outputDirectory.apply { safeMkdirs() }
    }

    /**
     * Download the source code of the [package][pkg] to the [outputDirectory]. A [Provenance] is returned on success or
     * a [DownloadException] is thrown in case of failure.
     */
    fun download(pkg: Package, outputDirectory: File): Provenance {
        verifyOutputDirectory(outputDirectory)

        if (pkg.isMetaDataOnly) return UnknownProvenance

        val exception = DownloadException("Download failed for '${pkg.id.toCoordinates()}'.")

        config.sourceCodeOrigins.forEach { origin ->
            val provenance = when (origin) {
                SourceCodeOrigin.VCS -> handleVcsDownload(pkg, outputDirectory, exception)
                SourceCodeOrigin.ARTIFACT -> handleSourceArtifactDownload(pkg, outputDirectory, exception)
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
        exception: DownloadException
    ): Provenance? {
        val vcsMark = TimeSource.Monotonic.markNow()

        try {
            // Cargo in general builds from source tarballs, so we prefer source artifacts over VCS, but still use VCS
            // if no source artifact is given.
            val isCargoPackageWithSourceArtifact = pkg.id.type == "Cargo" && pkg.sourceArtifact != RemoteArtifact.EMPTY

            if (!isCargoPackageWithSourceArtifact) {
                val result = downloadFromVcs(pkg, outputDirectory)
                val vcsInfo = (result as RepositoryProvenance).vcsInfo

                log.info {
                    "Downloaded source code for '${pkg.id.toCoordinates()}' from $vcsInfo in ${vcsMark.elapsedNow()}."
                }

                return result
            } else {
                log.info { "Skipping VCS download for Cargo package '${pkg.id.toCoordinates()}'." }
            }
        } catch (e: DownloadException) {
            log.debug { "VCS download failed for '${pkg.id.toCoordinates()}': ${e.collectMessages()}" }

            log.info {
                "Failed attempt to download source code for '${pkg.id.toCoordinates()}' from ${pkg.vcsProcessed} " +
                        "took ${vcsMark.elapsedNow()}."
            }

            // Clean up any left-over files (force to delete read-only files in ".git" directories on Windows).
            outputDirectory.safeDeleteRecursively(force = true)
            outputDirectory.safeMkdirs()

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
        exception: DownloadException
    ): Provenance? {
        val sourceArtifactMark = TimeSource.Monotonic.markNow()

        try {
            val result = downloadSourceArtifact(pkg, outputDirectory)

            log.info {
                "Downloaded source code for '${pkg.id.toCoordinates()}' from ${pkg.sourceArtifact} in " +
                        "${sourceArtifactMark.elapsedNow()}."
            }

            return result
        } catch (e: DownloadException) {
            log.debug {
                "Source artifact download failed for '${pkg.id.toCoordinates()}': ${e.collectMessages()}"
            }

            log.info {
                "Failed attempt to download source code for '${pkg.id.toCoordinates()}' from ${pkg.sourceArtifact} " +
                        "took ${sourceArtifactMark.elapsedNow()}."
            }

            // Clean up any left-over files.
            outputDirectory.safeDeleteRecursively()
            outputDirectory.safeMkdirs()

            exception.addSuppressed(e)
        }

        return null
    }

    /**
     * Download the source code of the [package][pkg] to the [outputDirectory] using its VCS information. If [recursive]
     * is `true`, any nested repositories (like Git submodules or Mercurial subrepositories) are downloaded, too. A
     * [Provenance] is returned on success or a [DownloadException] is thrown in case of failure.
     */
    @JvmOverloads
    fun downloadFromVcs(
        pkg: Package,
        outputDirectory: File,
        recursive: Boolean = true
    ): Provenance {
        verifyOutputDirectory(outputDirectory)

        log.info {
            "Trying to download '${pkg.id.toCoordinates()}' sources to '${outputDirectory.absolutePath}' from VCS..."
        }

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

        if (pkg.vcsProcessed != pkg.vcs) {
            log.info { "Using processed ${pkg.vcsProcessed}. Original was ${pkg.vcs}." }
        } else {
            log.info { "Using ${pkg.vcsProcessed}." }
        }

        var applicableVcs: VersionControlSystem? = null

        if (pkg.vcsProcessed.type != VcsType.UNKNOWN) {
            applicableVcs = VersionControlSystem.forType(pkg.vcsProcessed.type)
            log.info {
                applicableVcs?.let {
                    "Detected VCS type '${it.type}' from type name '${pkg.vcsProcessed.type}'."
                } ?: "Could not detect VCS type from type name '${pkg.vcsProcessed.type}'."
            }
        }

        if (applicableVcs == null) {
            applicableVcs = VersionControlSystem.forUrl(pkg.vcsProcessed.url)
            log.info {
                applicableVcs?.let {
                    "Detected VCS type '${it.type}' from URL '${pkg.vcsProcessed.url}'."
                } ?: "Could not detect VCS type from URL '${pkg.vcsProcessed.url}'."
            }
        }

        if (applicableVcs == null) {
            throw DownloadException("Unsupported VCS type '${pkg.vcsProcessed.type}'.")
        }

        val workingTree = try {
            applicableVcs.download(pkg, outputDirectory, config.allowMovingRevisions, recursive)
        } catch (e: DownloadException) {
            // TODO: We should introduce something like a "strict" mode and only do these kind of fallbacks in
            //       non-strict mode.
            val vcsUrlNoCredentials = pkg.vcsProcessed.url.replaceCredentialsInUri()
            if (vcsUrlNoCredentials != pkg.vcsProcessed.url) {
                // Try once more with any username / password stripped from the URL.
                log.info {
                    "Falling back to trying to download from $vcsUrlNoCredentials which has credentials removed."
                }

                // Clean up any files left from the failed VCS download (i.e. a ".git" directory).
                outputDirectory.safeDeleteRecursively(force = true)
                outputDirectory.safeMkdirs()

                val fallbackPkg = pkg.copy(vcsProcessed = pkg.vcsProcessed.copy(url = vcsUrlNoCredentials))
                applicableVcs.download(fallbackPkg, outputDirectory, config.allowMovingRevisions, recursive)
            } else {
                throw e
            }
        }
        val resolvedRevision = workingTree.getRevision()

        log.info {
            "Finished downloading source code revision '$resolvedRevision' to '${outputDirectory.absolutePath}'."
        }

        return RepositoryProvenance(pkg.vcsProcessed, resolvedRevision)
    }

    /**
     * Download the source code of the [package][pkg] to the [outputDirectory] using its source artifact. A
     * [Provenance] is returned on success or a [DownloadException] is thrown in case of failure.
     */
    fun downloadSourceArtifact(pkg: Package, outputDirectory: File): Provenance {
        verifyOutputDirectory(outputDirectory)

        log.info {
            "Trying to download source artifact for '${pkg.id.toCoordinates()}' from ${pkg.sourceArtifact.url}..."
        }

        if (pkg.sourceArtifact.url.isBlank()) {
            throw DownloadException("No source artifact URL provided for '${pkg.id.toCoordinates()}'.")
        }

        // Some (Linux) file URIs do not start with "file://" but look like "file:/opt/android-sdk-linux".
        val isLocalFileUrl = pkg.sourceArtifact.url.startsWith("file:/")

        var tempDir: File? = null

        val sourceArchive = if (isLocalFileUrl) {
            File(URI(pkg.sourceArtifact.url))
        } else {
            tempDir = createOrtTempDir()
            OkHttpClientHelper.downloadFile(pkg.sourceArtifact.url, tempDir).getOrElse {
                tempDir.safeDeleteRecursively(force = true)
                throw DownloadException("Failed to download source artifact.", it)
            }
        }

        if (pkg.sourceArtifact.hash.algorithm != HashAlgorithm.NONE) {
            if (pkg.sourceArtifact.hash.algorithm == HashAlgorithm.UNKNOWN) {
                log.warn {
                    "Cannot verify source artifact with ${pkg.sourceArtifact.hash}, skipping verification."
                }
            } else if (!pkg.sourceArtifact.hash.verify(sourceArchive)) {
                tempDir?.safeDeleteRecursively(force = true)
                throw DownloadException("Source artifact does not match expected ${pkg.sourceArtifact.hash}.")
            }
        }

        try {
            if (sourceArchive.extension == "gem") {
                // Unpack the nested data archive for Ruby Gems.
                val gemDirectory = createOrtTempDir("gem")
                val dataFile = gemDirectory.resolve("data.tar.gz")

                try {
                    sourceArchive.unpack(gemDirectory)
                    dataFile.unpack(outputDirectory)
                } finally {
                    gemDirectory.safeDeleteRecursively(force = true)
                }
            } else {
                sourceArchive.unpackTryAllTypes(outputDirectory)
            }
        } catch (e: IOException) {
            log.error {
                "Could not unpack source artifact '${sourceArchive.absolutePath}': ${e.collectMessages()}"
            }

            tempDir?.safeDeleteRecursively(force = true)
            throw DownloadException(e)
        }

        log.info {
            "Successfully downloaded source artifact for '${pkg.id.toCoordinates()}' to " +
                    "'${outputDirectory.absolutePath}'..."
        }

        tempDir?.safeDeleteRecursively(force = true)
        return ArtifactProvenance(pkg.sourceArtifact)
    }
}

/**
 * Consolidate [projects] based on their VcsInfo without taking the path into account. As we store VcsInfo per project
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
