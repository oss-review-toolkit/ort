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

import com.here.ort.model.Identifier
import com.here.ort.model.OutputFormat
import com.here.ort.model.Package
import com.here.ort.model.AnalyzerResult
import com.here.ort.model.HashAlgorithm
import com.here.ort.utils.OkHttpClientHelper
import com.here.ort.utils.PARAMETER_ORDER_HELP
import com.here.ort.utils.PARAMETER_ORDER_LOGGING
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.PARAMETER_ORDER_OPTIONAL
import com.here.ort.utils.jsonMapper
import com.here.ort.utils.log
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.safeMkdirs
import com.here.ort.utils.yamlMapper

import java.io.File
import java.io.IOException

import kotlin.system.exitProcess

import okhttp3.Request

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

    private class DataEntityConverter : IStringConverter<DataEntity> {
        override fun convert(name: String): DataEntity {
            try {
                return DataEntity.valueOf(name.toUpperCase())
            } catch (e: IllegalArgumentException) {
                if (com.here.ort.utils.printStackTrace) {
                    e.printStackTrace()
                }

                throw ParameterException("Data entities must be contained in ${DataEntity.ALL}.")
            }
        }
    }

    @Parameter(description = "The dependencies analysis file to use.",
            names = ["--dependencies-file", "-d"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    @Suppress("LateinitUsage")
    private lateinit var dependenciesFile: File

    @Parameter(description = "The output directory to download the source code to.",
            names = ["--output-dir", "-o"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    @Suppress("LateinitUsage")
    private lateinit var outputDir: File

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
        com.here.ort.utils.printStackTrace = stacktrace

        require(dependenciesFile.isFile) {
            "Provided path is not a file: ${dependenciesFile.absolutePath}"
        }

        val mapper = when (dependenciesFile.extension) {
            OutputFormat.JSON.fileExtension -> jsonMapper
            OutputFormat.YAML.fileExtension -> yamlMapper
            else -> throw IllegalArgumentException("Provided input file is neither JSON nor YAML.")
        }

        val analyzerResult = mapper.readValue(dependenciesFile, AnalyzerResult::class.java)
        val packages = mutableListOf<Package>()

        if (entities.contains(DataEntity.PROJECT)) {
            packages.add(analyzerResult.project.toPackage())
        }

        if (entities.contains(DataEntity.PACKAGES)) {
            packages.addAll(analyzerResult.packages)
        }

        packages.forEach { pkg ->
            try {
                download(pkg, outputDir)
            } catch (e: DownloadException) {
                if (com.here.ort.utils.printStackTrace) {
                    e.printStackTrace()
                }

                log.error { "Could not download '${pkg.id}': ${e.message}" }
            }
        }
    }

    /**
     * Download the source code of the [target] package to a folder inside [outputDirectory]. The folder name is created
     * from the [name][Identifier.name] and [version][Identifier.version] of the [target] package [id][Package.id].
     *
     * @param target The description of the package to download.
     * @param outputDirectory The parent directory to download the source code to.
     *
     * @return The directory the source code was downloaded to.
     *
     * @throws DownloadException In case the download failed.
     */
    fun download(target: Package, outputDirectory: File): File {
        // TODO: add namespace to path
        val targetDir = File(outputDirectory, "${target.normalizedName}/${target.id.version}").apply { safeMkdirs() }

        if (target.vcsProcessed.url.isBlank()) {
            log.info { "No VCS URL provided for '${target.id}'." }
        } else {
            try {
                return downloadFromVcs(target, targetDir)
            } catch (e: DownloadException) {
                if (com.here.ort.utils.printStackTrace) {
                    e.printStackTrace()
                }

                log.info { "VCS download failed for '${target.id}': ${e.message}" }

                // Clean up any files left from the failed VCS download (i.e. a ".git" directory).
                targetDir.safeDeleteRecursively()
                targetDir.safeMkdirs()
            }
        }

        return downloadSourceArtifact(target, targetDir)
    }

    private fun downloadFromVcs(target: Package, outputDirectory: File): File {
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

        if (target.vcsProcessed.provider.isNotBlank()) {
            applicableVcs = VersionControlSystem.forProvider(target.vcsProcessed.provider)
            log.info {
                if (applicableVcs != null) {
                    "Detected VCS provider '$applicableVcs' from provider name '${target.vcsProcessed.provider}'."
                } else {
                    "Could not detect VCS provider from provider name '${target.vcsProcessed.provider}'."
                }
            }
        }

        if (applicableVcs == null) {
            applicableVcs = VersionControlSystem.forUrl(target.vcsProcessed.url)
            log.info {
                if (applicableVcs != null) {
                    "Detected VCS provider '$applicableVcs' from URL '${target.vcsProcessed.url}'."
                } else {
                    "Could not detect VCS provider from URL '${target.vcsProcessed.url}'."
                }
            }
        }

        if (applicableVcs == null) {
            throw DownloadException("Could not find an applicable VCS provider.")
        }

        val workingTree = applicableVcs.download(target.vcsProcessed, target.id.version, outputDirectory,
                allowMovingRevisions)
        val revision = workingTree.getRevision()

        log.info { "Finished downloading source code revision '$revision' to '${outputDirectory.absolutePath}'." }

        return outputDirectory
    }

    private fun downloadSourceArtifact(target: Package, outputDirectory: File): File {
        if (target.sourceArtifact.url.isBlank()) {
            throw DownloadException("No source artifact URL provided for '${target.id}'.")
        }

        log.info {
            "Trying to download source artifact for '${target.id}' from '${target.sourceArtifact.url}'..."
        }

        val request = Request.Builder().url(target.sourceArtifact.url).build()

        val response = OkHttpClientHelper.execute(HTTP_CACHE_PATH, request)
        if (!response.isSuccessful || response.body() == null) {
            throw DownloadException("Failed to download source artifact: $response")
        }

        val tempFile = createTempFile(suffix = target.sourceArtifact.url.substringAfterLast("/"))

        tempFile.outputStream().use { stream ->
            stream.write(response.body()!!.bytes())
        }

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

        return outputDirectory
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
