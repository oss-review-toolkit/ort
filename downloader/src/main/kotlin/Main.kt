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

import com.beust.jcommander.IStringConverter
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException

import com.fasterxml.jackson.databind.JsonMappingException

import com.here.ort.model.AnalyzerResult
import com.here.ort.model.HashAlgorithm
import com.here.ort.model.Identifier
import com.here.ort.model.MergedAnalyzerResult
import com.here.ort.model.Package
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.VcsInfo
import com.here.ort.model.mapper
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

        companion object {
            /**
             * The list of all available data entities.
             */
            @JvmField
            val ALL = DataEntity.values().asList()
        }
    }

    /**
     * This class describes what was downloaded by [download] to the [downloadDirectory] or if any exception occured.
     * Either [sourceArtifact] or [vcsInfo] is set to a non-null value. The download was started at [dateTime].
     */
    data class DownloadResult(
            val dateTime: Instant,
            val downloadDirectory: File,
            val sourceArtifact: RemoteArtifact? = null,
            val vcsInfo: VcsInfo? = null
    ) {
        init {
            require((sourceArtifact == null) != (vcsInfo == null)) {
                "Either sourceArtifact or vcsInfo must be set, but not both."
            }
        }
    }

    private class DataEntityConverter : IStringConverter<DataEntity> {
        override fun convert(name: String): DataEntity {
            try {
                return DataEntity.valueOf(name.toUpperCase())
            } catch (e: IllegalArgumentException) {
                e.showStackTrace()

                throw ParameterException("Data entities must be contained in ${DataEntity.ALL}.")
            }
        }
    }

    @Parameter(description = "A dependencies analysis file to use. Must not be used with '--project-url'.",
            names = ["--dependencies-file", "-d"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var dependenciesFile: File? = null

    @Parameter(description = "A VCS URL to a project to download. Must not be used with '--dependencies-file'.",
            names = ["--project-url"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var projectUrl: String? = null

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
            converter = DataEntityConverter::class,
            order = PARAMETER_ORDER_OPTIONAL)
    private var entities = DataEntity.ALL

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

        if (dependenciesFile != null && projectUrl != null) {
            throw IllegalArgumentException(
                    "The '--dependencies-file' and '--project-url' parameters must not be used together.")
        }

        val packages = dependenciesFile?.let {
            require(it.isFile) {
                "Provided path is not a file: ${it.absolutePath}"
            }

            val mapper = it.mapper()

            // Read packages assuming the dependencies file contains an AnalyzerResult.
            try {
                val analyzerResult = mapper.readValue(dependenciesFile, AnalyzerResult::class.java)

                mutableListOf<Package>().apply {
                    if (entities.contains(DataEntity.PROJECT)) {
                        add(analyzerResult.project.toPackage())
                    }

                    if (entities.contains(DataEntity.PACKAGES)) {
                        addAll(analyzerResult.packages)
                    }
                }
            } catch (e: JsonMappingException) {
                // Read packages assuming the dependencies file contains a MergedAnalyzerResult.
                val mergedAnalyzerResult = mapper.readValue(dependenciesFile, MergedAnalyzerResult::class.java)

                mutableListOf<Package>().apply {
                    if (entities.contains(DataEntity.PROJECT)) {
                        addAll(mergedAnalyzerResult.projects.map { it.toPackage() })
                    }

                    if (entities.contains(DataEntity.PACKAGES)) {
                        addAll(mergedAnalyzerResult.packages)
                    }
                }
            }
        } ?: run {
            // TODO: Allow to specify the project name as a parameter.
            val projectName = projectUrl!!.substringAfterLast('/').substringBeforeLast('.')

            // TODO: Allow to specify the VCS revision as a parameter.
            allowMovingRevisions = true
            val vcs = VcsInfo.EMPTY.copy(url = projectUrl!!)

            val dummyId = Identifier.EMPTY.copy(name = projectName)
            val dummyPackage = Package.EMPTY.copy(id = dummyId, vcs = vcs, vcsProcessed = vcs)

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
                type = applicableVcs.javaClass.simpleName,
                url = target.vcsProcessed.url,
                revision = target.vcsProcessed.revision.takeIf { it.isNotBlank() } ?: revision,
                resolvedRevision = revision,
                path = target.vcsProcessed.path // TODO: Needs to check if the VCS used the sparse checkout.
        )
        return DownloadResult(startTime, outputDirectory, vcsInfo = vcsInfo)
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

        val tempFile = createTempFile(suffix = target.sourceArtifact.url.substringAfterLast("/"))
        Okio.buffer(Okio.sink(tempFile)).use { it.writeAll(body.source()) }

        verifyChecksum(tempFile, target.sourceArtifact.hash, target.sourceArtifact.hashAlgorithm)

        try {
            tempFile.unpack(outputDirectory)
        } catch (e: IOException) {
            log.error { "Could not unpack source artifact '${tempFile.absolutePath}': ${e.message}" }
            throw DownloadException(e)
        } finally {
            if (!tempFile.delete()) {
                log.warn { "Unable to delete temporary file '$tempFile'." }
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
                    log.error { "Unknown hash algorithm." }
                    ""
                }
            }
        }

        if (digest != hash) {
            throw DownloadException("Calculated $hashAlgorithm hash '$digest' differs from expected hash '$hash'.")
        }
    }
}
