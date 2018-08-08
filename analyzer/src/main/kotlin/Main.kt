/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

package com.here.ort.analyzer

import ch.frankel.slf4k.*

import com.beust.jcommander.IStringConverter
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException

import com.here.ort.model.OutputFormat
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.utils.PARAMETER_ORDER_HELP
import com.here.ort.utils.PARAMETER_ORDER_LOGGING
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.PARAMETER_ORDER_OPTIONAL
import com.here.ort.utils.log
import com.here.ort.utils.printStackTrace
import com.here.ort.utils.safeMkdirs

import java.io.File

import kotlin.system.exitProcess

/**
 * The main entry point of the application.
 */
object Main {
    const val TOOL_NAME = "analyzer"
    const val HTTP_CACHE_PATH = "$TOOL_NAME/cache/http"

    private class PackageManagerConverter : IStringConverter<PackageManagerFactory<PackageManager>> {
        companion object {
            // Map upper-cased package manager class names to their instances.
            val PACKAGE_MANAGER_NAMES = PackageManager.ALL.associateBy { it.toString().toUpperCase() }
        }

        override fun convert(name: String): PackageManagerFactory<PackageManager> {
            return PACKAGE_MANAGER_NAMES[name.toUpperCase()]
                    ?: throw ParameterException("Package managers must be contained in ${PACKAGE_MANAGER_NAMES.keys}.")
        }
    }

    @Parameter(description = "A list of package managers to activate.",
            names = ["--package-managers", "-m"],
            converter = PackageManagerConverter::class,
            order = PARAMETER_ORDER_OPTIONAL)
    private var packageManagers = PackageManager.ALL

    @Parameter(description = "The project directory to scan.",
            names = ["--input-dir", "-i"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    @Suppress("LateinitUsage")
    private lateinit var inputDir: File

    @Parameter(description = "The directory to write dependency information to.",
            names = ["--output-dir", "-o"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    @Suppress("LateinitUsage")
    private lateinit var outputDir: File

    @Parameter(description = "The list of output formats used for the result file(s).",
            names = ["--output-formats", "-f"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var outputFormats = listOf(OutputFormat.YAML)

    @Parameter(description = "Ignore versions of required tools. NOTE: This may lead to erroneous results.",
            names = ["--ignore-tool-versions"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var ignoreToolVersions = false

    @Parameter(description = "Allow dynamic versions of dependencies. This can result in unstable results when " +
            "dependencies use version ranges. This option only affects package managers that support lock files, " +
            "like NPM.",
            names = ["--allow-dynamic-versions"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var allowDynamicVersions = false

    @Parameter(description = "By default the components configured to be excluded in the .ort.yml file of the input" +
            "directory will still be analyzed, but marked as excluded in the result. With this option enabled they " +
            "will not be analyzed and not appear in the result.",
            names = ["--remove-excludes-from-result"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var removeExcludesFromResult = false

    @Parameter(description = "A YAML file that contains package curation data.",
            names = ["--package-curations-file"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var packageCurationsFile: File? = null

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
            exitProcess(0)
        }

        // Make the parameter globally available.
        printStackTrace = stacktrace

        val absoluteOutputPath = outputDir.absoluteFile
        if (absoluteOutputPath.exists()) {
            log.error { "The output directory '$absoluteOutputPath' must not exist yet." }
            exitProcess(1)
        }

        println("The following package managers are activated:")
        println("\t" + packageManagers.joinToString(", "))

        val absoluteProjectPath = inputDir.absoluteFile
        println("Scanning project path:\n\t$absoluteProjectPath")

        val config = AnalyzerConfiguration(ignoreToolVersions, allowDynamicVersions, removeExcludesFromResult)

        val ortResult = Analyzer().analyze(config, absoluteProjectPath, packageManagers, packageCurationsFile)

        absoluteOutputPath.safeMkdirs()

        outputFormats.forEach { format ->
            val outputFile = File(absoluteOutputPath, "analyzer-result.${format.fileExtension}")
            println("Writing analyzer result to '$outputFile'.")
            format.mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, ortResult)
        }
    }
}
