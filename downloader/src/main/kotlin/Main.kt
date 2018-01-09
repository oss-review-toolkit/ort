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

import com.here.ort.model.OutputFormat
import com.here.ort.model.Package
import com.here.ort.model.AnalyzerResult
import com.here.ort.utils.OkHttpClientHelper
import com.here.ort.utils.jsonMapper
import com.here.ort.utils.log
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
                if (stacktrace) {
                    e.printStackTrace()
                }

                throw ParameterException("Data entities must be contained in ${DataEntity.ALL}.")
            }
        }
    }

    @Parameter(description = "The dependencies analysis file to use.",
            names = ["--dependencies-file", "-d"],
            required = true,
            order = 0)
    @Suppress("LateinitUsage")
    private lateinit var dependenciesFile: File

    @Parameter(description = "The output directory to download the source code to.",
            names = ["--output-dir", "-o"],
            required = true,
            order = 0)
    @Suppress("LateinitUsage")
    private lateinit var outputDir: File

    @Parameter(description = "The data entities from the dependencies analysis file to download.",
            names = ["--entities", "-e"],
            converter = DataEntityConverter::class,
            order = 0)
    private var entities = DataEntity.ALL

    @Parameter(description = "Enable info logging.",
            names = ["--info"],
            order = 0)
    private var info = false

    @Parameter(description = "Enable debug logging and keep any temporary files.",
            names = ["--debug"],
            order = 0)
    private var debug = false

    @Parameter(description = "Print out the stacktrace for all exceptions.",
            names = ["--stacktrace"],
            order = 0)
    var stacktrace = false

    @Parameter(description = "Display the command line help.",
            names = ["--help", "-h"],
            help = true,
            order = 100)
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

        require(dependenciesFile.isFile) {
            "Provided path is not a file: ${dependenciesFile.absolutePath}"
        }

        val mapper = when (dependenciesFile.extension) {
            OutputFormat.JSON.fileEnding -> jsonMapper
            OutputFormat.YAML.fileEnding -> yamlMapper
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
                if (stacktrace) {
                    e.printStackTrace()
                }

                log.error { "Could not download '${pkg.identifier}': ${e.message}" }
            }
        }
    }

    /**
     * Download the source code of the [target] package to a folder inside [outputDirectory]. The folder name is created
     * from the [name][Package.name] and [version][Package.version] of the [target] package.
     *
     * @param target The description of the package to download.
     * @param outputDirectory The parent directory to download the source code to.
     *
     * @return The directory the source code was downloaded to.
     *
     * @throws DownloadException In case the download failed.
     */
    @Suppress("ComplexMethod")
    fun download(target: Package, outputDirectory: File): File {
        // TODO: return also SHA1 which was finally cloned
        val p = fun(string: String) = println("${target.identifier}: $string")

        // TODO: add namespace to path
        val targetDir = File(outputDirectory, "${target.normalizedName}/${target.version}").apply { safeMkdirs() }
        p("Downloading source code to '${targetDir.absolutePath}'...")

        if (target.vcsProcessed.url.isNotBlank()) {
            p("Trying to download from URL '${target.vcsProcessed.url}'...")

            if (target.vcsProcessed.url != target.vcs.url) {
                p("URL was normalized, original URL was '${target.vcs.url}'.")
            }

            if (target.vcsProcessed.revision.isBlank()) {
                p("WARNING: No VCS revision provided, downloaded source code does likely not match revision " +
                        target.version)
            } else {
                p("Downloading revision '${target.vcsProcessed.revision}'.")
            }

            var applicableVcs: VersionControlSystem? = null

            p("Trying to detect VCS...")

            if (target.vcsProcessed.provider.isNotBlank()) {
                p("from provider name '${target.vcsProcessed.provider}'...")
                applicableVcs = VersionControlSystem.forProvider(target.vcsProcessed.provider)
            }

            if (applicableVcs == null) {
                p("from URL '${target.vcsProcessed.url}'...")
                applicableVcs = VersionControlSystem.forUrl(target.vcsProcessed.url)
            }

            if (applicableVcs == null) {
                throw DownloadException("Could not find an applicable VCS provider.")
            }

            p("Using VCS provider '$applicableVcs'.")

            try {
                val revision = applicableVcs.download(target.vcsProcessed, target.version, targetDir)
                p("Finished downloading source code revision '$revision' to '${targetDir.absolutePath}'.")
                return targetDir
            } catch (e: DownloadException) {
                if (stacktrace) {
                    e.printStackTrace()
                }

                throw DownloadException("Could not download source code.", e)
            }
        } else {
            p("No VCS URL provided.")
            // TODO: This should also be tried if the VCS checkout does not work.

            p("Trying to download source package ...")
            return downloadSourcePackage(target, outputDirectory)
        }
    }

    private fun downloadSourcePackage(target: Package, outputDirectory: File): File {
        if (target.sourceArtifact.url.isBlank()) {
            throw DownloadException("No source artifact URL provided.")
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

        return outputDirectory
    }

    private fun verifyChecksum(file: File, hash: String, hashAlgorithm: String) {
        val digest = when (hashAlgorithm.toLowerCase()) {
            "md5" -> file.inputStream().use { DigestUtils.md5Hex(it) }
            "sha1", "sha-1" -> file.inputStream().use { DigestUtils.sha1Hex(it) }
            else -> {
                log.error { "Unknown hash algorithm: $hashAlgorithm" }
                ""
            }
        }

        if (digest != hash) {
            throw DownloadException("Calculated $hashAlgorithm hash '$digest' differs from expected hash '$hash'.")
        }
    }
}
