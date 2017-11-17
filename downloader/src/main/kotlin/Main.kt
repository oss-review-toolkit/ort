/*
 * Copyright (c) 2017 HERE Europe B.V.
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
import com.here.ort.util.jsonMapper
import com.here.ort.util.log
import com.here.ort.util.safeMkdirs
import com.here.ort.util.yamlMapper

import java.io.File

import kotlin.system.exitProcess

/**
 * The main entry point of the application.
 */
object Main {

    enum class DataEntity {
        PACKAGES,
        PROJECT;

        companion object {
            /**
             * The list of all available data entities.
             */
            @JvmField val ALL = DataEntity.values().asList()
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
            names = arrayOf("--dependencies-file", "-d"),
            required = true,
            order = 0)
    @Suppress("LateinitUsage")
    private lateinit var dependenciesFile: File

    @Parameter(description = "The output directory to download the source code to.",
            names = arrayOf("--output-dir", "-o"),
            required = true,
            order = 0)
    @Suppress("LateinitUsage")
    private lateinit var outputDir: File

    @Parameter(description = "The data entities from the dependencies analysis file to download.",
            names = arrayOf("--entities", "-e"),
            converter = DataEntityConverter::class,
            order = 0)
    private var entities = DataEntity.ALL

    @Parameter(description = "Enable info logging.",
            names = arrayOf("--info"),
            order = 0)
    private var info = false

    @Parameter(description = "Enable debug logging and keep any temporary files.",
            names = arrayOf("--debug"),
            order = 0)
    private var debug = false

    @Parameter(description = "Print out the stacktrace for all exceptions.",
            names = arrayOf("--stacktrace"),
            order = 0)
    var stacktrace = false

    @Parameter(description = "Display the command line help.",
            names = arrayOf("--help", "-h"),
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
        jc.programName = "downloader"

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
     * @return The directory containing the source code, or null if the source code could not be downloaded.
     */
    @Suppress("ComplexMethod")
    fun download(target: Package, outputDirectory: File): File {
        // TODO: return also SHA1 which was finally cloned
        val p = fun(string: String) = println("${target.identifier}: $string")

        // TODO: add namespace to path
        val targetDir = File(outputDirectory, "${target.name}/${target.version}").apply { safeMkdirs() }
        p("Downloading source code to '${targetDir.absolutePath}'...")

        if (!target.normalizedVcsUrl.isBlank()) {
            p("Trying to download from URL '${target.normalizedVcsUrl}'...")

            if (target.vcsUrl != target.normalizedVcsUrl) {
                p("URL was normalized, original URL was '${target.vcsUrl}'.")
            }

            if (target.vcsRevision.isBlank()) {
                p("WARNING: No VCS revision provided, downloaded source code does likely not match revision " +
                        target.version)
            } else {
                p("Downloading revision '${target.vcsRevision}'.")
            }

            val applicableVcs = mutableListOf<VersionControlSystem>()

            if (target.vcsProvider.isNotBlank()) {
                p("Detecting VCS from provider name '${target.vcsProvider}'...")
                applicableVcs.addAll(VersionControlSystem.fromProvider(target.vcsProvider))
            }

            if (applicableVcs.isEmpty()) {
                if (target.normalizedVcsUrl.isNotBlank()) {
                    p("Could not find a VCS provider for '${target.vcsProvider}', trying to detect provider " +
                            "from URL '${target.normalizedVcsUrl}'...")
                    applicableVcs.addAll(VersionControlSystem.fromUrl(target.normalizedVcsUrl))
                }
            }

            when {
                applicableVcs.isEmpty() ->
                    // TODO: Decide if we want to do a trial-and-error with all available VCS here.
                    throw DownloadException("Could not find an applicable VCS provider.")
                applicableVcs.size > 1 ->
                    throw DownloadException("Multiple applicable VCS providers found: ${applicableVcs.joinToString()}")
                else -> {
                    val vcs = applicableVcs.first()
                    p("Using VCS provider '${vcs.javaClass.simpleName}'.")
                    try {
                        val revision = vcs.download(target.normalizedVcsUrl, target.vcsRevision, target.vcsPath,
                                target.version, targetDir)
                        p("Finished downloading source code revision '$revision' to '${targetDir.absolutePath}'.")
                        return targetDir
                    } catch (e: DownloadException) {
                        if (stacktrace) {
                            e.printStackTrace()
                        }

                        throw DownloadException("Could not download source code.", e)
                    }
                }
            }
        } else {
            p("No VCS URL provided.")
            // TODO: This should also be tried if the VCS checkout does not work.
            p("Trying to download source package ...")
            // TODO: Implement downloading of source package.
            throw DownloadException("No source code package URL provided.")
        }
    }

}
