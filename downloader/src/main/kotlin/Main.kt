/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter

import com.here.ort.model.AnalyzerResult
import com.here.ort.model.HashAlgorithm
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.VcsInfo
import com.here.ort.model.mapper
import com.here.ort.utils.ARCHIVE_EXTENSIONS
import com.here.ort.utils.OkHttpClientHelper
import com.here.ort.utils.PARAMETER_ORDER_HELP
import com.here.ort.utils.PARAMETER_ORDER_LOGGING
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.PARAMETER_ORDER_OPTIONAL
import com.here.ort.utils.encodeOrUnknown
import com.here.ort.utils.log
import com.here.ort.utils.packZip
import com.here.ort.utils.printStackTrace
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.safeMkdirs
import com.here.ort.utils.showStackTrace
import com.here.ort.utils.unpack

import java.io.File
import java.io.IOException
import java.time.Instant

import kotlin.system.exitProcess

import okhttp3.Request

import okio.Okio

import org.apache.commons.codec.digest.DigestUtils

/**
 * The main entry point of the application.
 */
object Main {
    const val TOOL_NAME = "downloader"
    const val HTTP_CACHE_PATH = "$TOOL_NAME/cache/http"

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

    @Parameter(description = "A dependencies analysis file to use. Must not be used together with '--project-url'.",
            names = ["--dependencies-file", "-d"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var dependenciesFile: File? = null

    @Parameter(description = "A VCS or archive URL of a project to download. Must not be used together with " +
            "'--dependencies-file'.",
            names = ["--project-url"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var projectUrl: String? = null

    @Parameter(description = "The speaking name of the project to download. Will be ignored if '--dependencies-file' " +
            "is also specified.",
            names = ["--project-name"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var projectName: String? = null

    @Parameter(description = "The output directory to download the source code to.",
            names = ["--output-dir", "-o"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    @Suppress("LateinitUsage")
    private lateinit var outputDir: File

    @Parameter(description = "Archive the downloaded source code as ZIP files.",
            names = ["--archive", "-a"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var archive = false

    @Parameter(description = "The data entities from the dependencies analysis file to download.",
            names = ["--entities", "-e"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var entities = enumValues<DataEntity>().asList()

    @Parameter(description = "Allow the download of moving revisions (like e.g. HEAD or master in Git). By default " +
            "these revision are forbidden because they are not pointing to a stable revision of the source code.",
            names = ["--allow-moving-revisions"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var allowMovingRevisions = false

    @Parameter(description = "Enable info logging.",
            names = ["--info"],
            order = PARAMETER_ORDER_LOGGING)
    private var info = false

    @Parameter(description = "Enable debug logging and keep any temporary files.",
            names = ["--debug"],
            order = PARAMETER_ORDER_LOGGING)
    private var debug = false

    @Parameter(description = "Print out the stacktrace for all exceptions.",
            names = ["--stacktrace"],
            order = PARAMETER_ORDER_LOGGING)
    private var stacktrace = false

    @Parameter(description = "Display the command line help.",
            names = ["--help", "-h"],
            help = true,
            order = PARAMETER_ORDER_HELP)
    private var help = false

    /**
     * The entry point for the application.
     *
     * @param args The list of application arguments.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val jc = JCommander(this)
        jc.parse(*args)
        jc.programName = TOOL_NAME

        if (info) {
            log.level = ch.qos.logback.classic.Level.INFO
        }

        if (debug) {
            log.level = ch.qos.logback.classic.Level.DEBUG
        }

        if (help) {
            jc.usage()
            exitProcess(1)
        }

        // Make the parameter globally available.
        printStackTrace = stacktrace

        if ((dependenciesFile != null) == (projectUrl != null)) {
            throw IllegalArgumentException(
                    "Either '--dependencies-file' or '--project-url' must be specified.")
        }

        val packages = dependenciesFile?.let {
            require(it.isFile) {
                "Provided path is not a file: ${it.absolutePath}"
            }

            val analyzerResult = it.mapper().readValue(dependenciesFile, AnalyzerResult::class.java)

            mutableListOf<Package>().apply {
                if (DataEntity.PROJECT in entities) {
                    addAll(analyzerResult.projects.map { it.toPackage() })
                }

                if (DataEntity.PACKAGES in entities) {
                    addAll(analyzerResult.packages.map { it.pkg })
                }
            }
        } ?: run {
            allowMovingRevisions = true

            val projectFile = File(projectUrl)
            if (projectName == null) {
                projectName = projectFile.nameWithoutExtension
            }

            val dummyId = Identifier.EMPTY.copy(name = projectName!!)
            val dummyPackage = if (ARCHIVE_EXTENSIONS.any { projectFile.name.endsWith(it) }) {
                Package.EMPTY.copy(
                        id = dummyId,
                        sourceArtifact = RemoteArtifact(
                                url = projectUrl!!,
                                hash = "",
                                hashAlgorithm = HashAlgorithm.UNKNOWN
                        )
                )
            } else {
                // TODO: Allow to specify the VCS revision as a parameter.
                val vcs = VcsInfo.EMPTY.copy(url = projectUrl!!)

                Package.EMPTY.copy(id = dummyId, vcs = vcs, vcsProcessed = vcs)
            }

            listOf(dummyPackage)
        }

        var error = false

        packages.forEach { pkg ->
            try {
                val result = download(pkg, outputDir)

                if (archive) {
                    val zipFile = File(outputDir,
                            "${pkg.id.provider.encodeOrUnknown()}-${pkg.id.namespace.encodeOrUnknown()}-" +
                                    "${pkg.id.name.encodeOrUnknown()}-${pkg.id.version.encodeOrUnknown()}.zip")

                    log.info {
                        "Archiving directory '${result.downloadDirectory.absolutePath}' to '${zipFile.absolutePath}'."
                    }

                    try {
                        result.downloadDirectory.packZip(zipFile,
                                "${pkg.id.name.encodeOrUnknown()}/${pkg.id.version.encodeOrUnknown()}/")
                    } catch (e: IllegalArgumentException) {
                        e.showStackTrace()

                        log.error { "Could not archive '${pkg.id}': ${e.message}" }
                    } finally {
                        val relativePath = outputDir.toPath().relativize(result.downloadDirectory.toPath()).first()
                        File(outputDir, relativePath.toString()).safeDeleteRecursively()
                    }
                }
            } catch (e: DownloadException) {
                e.showStackTrace()

                log.error { "Could not download '${pkg.id}': ${e.message}" }

                error = true
            }
        }

        if (error) exitProcess(1)
    }

    /**
     * Download the source code of the [target] package to a folder inside [outputDirectory]. The folder name is created
     * from the [name][Identifier.name] and [version][Identifier.version] of the [target] package [id][Package.id].
     *
     * @param target The description of the package to download.
     * @param outputDirectory The parent directory to download the source code to.
     *
     * @return The [DownloadResult].
     *
     * @throws DownloadException In case the download failed.
     */
    fun download(target: Package, outputDirectory: File): DownloadResult {
        log.info { "Trying to download source code for '${target.id}'." }

        val targetDir = File(outputDirectory, target.id.toPath()).apply { safeMkdirs() }

        if (target.vcsProcessed.url.isBlank()) {
            log.info { "No VCS URL provided for '${target.id}'." }
        } else {
            try {
                return downloadFromVcs(target, targetDir)
            } catch (e: DownloadException) {
                e.showStackTrace()

                log.info { "VCS download failed for '${target.id}': ${e.message}" }

                // Clean up any files left from the failed VCS download (i.e. a ".git" directory).
                targetDir.safeDeleteRecursively()
                targetDir.safeMkdirs()
            }
        }

        return downloadSourceArtifact(target, targetDir)
    }

    private fun downloadFromVcs(target: Package, outputDirectory: File): DownloadResult {
        log.info {
            "Trying to download '${target.id}' sources to '${outputDirectory.absolutePath}' from VCS..."
        }

        if (target.vcsProcessed != target.vcs) {
            println("Using processed ${target.vcsProcessed}.")
            println("Original was ${target.vcs}.")
        } else {
            println("Using ${target.vcsProcessed}.")
        }

        var applicableVcs: VersionControlSystem? = null

        if (target.vcsProcessed.type.isNotBlank()) {
            applicableVcs = VersionControlSystem.forType(target.vcsProcessed.type)
            log.info {
                if (applicableVcs != null) {
                    "Detected VCS type '$applicableVcs' from type name '${target.vcsProcessed.type}'."
                } else {
                    "Could not detect VCS type from type name '${target.vcsProcessed.type}'."
                }
            }
        }

        if (applicableVcs == null) {
            applicableVcs = VersionControlSystem.forUrl(target.vcsProcessed.url)
            log.info {
                if (applicableVcs != null) {
                    "Detected VCS type '$applicableVcs' from URL '${target.vcsProcessed.url}'."
                } else {
                    "Could not detect VCS type from URL '${target.vcsProcessed.url}'."
                }
            }
        }

        if (applicableVcs == null) {
            throw DownloadException("Could not find an applicable VCS type.")
        }

        val startTime = Instant.now()
        val workingTree = applicableVcs.download(target, outputDirectory, allowMovingRevisions)
        val revision = workingTree.getRevision()

        log.info { "Finished downloading source code revision '$revision' to '${outputDirectory.absolutePath}'." }

        val vcsInfo = VcsInfo(
                type = applicableVcs.toString(),
                url = target.vcsProcessed.url,
                revision = target.vcsProcessed.revision.takeIf { it.isNotBlank() } ?: revision,
                resolvedRevision = revision,
                path = target.vcsProcessed.path // TODO: Needs to check if the VCS used the sparse checkout.
        )
        return DownloadResult(startTime, outputDirectory, vcsInfo = vcsInfo,
                originalVcsInfo = target.vcsProcessed.takeIf { it != vcsInfo })
    }

    private fun downloadSourceArtifact(target: Package, outputDirectory: File): DownloadResult {
        if (target.sourceArtifact.url.isBlank()) {
            throw DownloadException("No source artifact URL provided for '${target.id}'.")
        }

        log.info {
            "Trying to download source artifact for '${target.id}' from '${target.sourceArtifact.url}'..."
        }

        val request = Request.Builder().get().url(target.sourceArtifact.url).build()

        val startTime = Instant.now()
        val response = OkHttpClientHelper.execute(HTTP_CACHE_PATH, request)
        val body = response.body()
        if (!response.isSuccessful || body == null) {
            throw DownloadException("Failed to download source artifact: $response")
        }

        val sourceArchive = createTempFile(suffix = target.sourceArtifact.url.substringAfterLast("/"))
        Okio.buffer(Okio.sink(sourceArchive)).use { it.writeAll(body.source()) }

        verifyChecksum(sourceArchive, target.sourceArtifact.hash, target.sourceArtifact.hashAlgorithm)

        try {
            if (sourceArchive.extension == "gem") {
                // Unpack the nested data archive for Ruby Gems.
                val gemDirectory = createTempDir()
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
        } finally {
            if (!sourceArchive.delete()) {
                log.warn { "Unable to delete temporary file '$sourceArchive'." }
            }
        }

        log.info {
            "Successfully downloaded source artifact for '${target.id}' to '${outputDirectory.absolutePath}'..."
        }

        return DownloadResult(startTime, outputDirectory, sourceArtifact = target.sourceArtifact)
    }

    private fun verifyChecksum(file: File, hash: String, hashAlgorithm: HashAlgorithm) {
        val digest = file.inputStream().use {
            when (hashAlgorithm) {
                HashAlgorithm.MD2 -> DigestUtils.md2Hex(it)
                HashAlgorithm.MD5 -> DigestUtils.md5Hex(it)
                HashAlgorithm.SHA1 -> DigestUtils.sha1Hex(it)
                HashAlgorithm.SHA256 -> DigestUtils.sha256Hex(it)
                HashAlgorithm.SHA384 -> DigestUtils.sha384Hex(it)
                HashAlgorithm.SHA512 -> DigestUtils.sha512Hex(it)
                HashAlgorithm.UNKNOWN -> {
                    log.warn { "Unknown hash algorithm." }
                    ""
                }
            }
        }

        if (digest != hash) {
            throw DownloadException("Calculated $hashAlgorithm hash '$digest' differs from expected hash '$hash'.")
        }
    }
}
