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

package com.here.ort.commands

import ch.frankel.slf4k.*

import com.beust.jcommander.IStringConverter
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException
import com.beust.jcommander.Parameters

import com.here.ort.CommandWithHelp
import com.here.ort.analyzer.Analyzer
import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.PackageManagerFactory
import com.here.ort.model.OutputFormat
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.mapper
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.PARAMETER_ORDER_OPTIONAL
import com.here.ort.utils.log
import com.here.ort.utils.safeMkdirs

import java.io.File

@Parameters(commandNames = ["analyze"], commandDescription = "Determine dependencies of a software project.")
object AnalyzerCommand : CommandWithHelp() {
    private class PackageManagerConverter : IStringConverter<PackageManagerFactory> {
        companion object {
            // Map upper-cased package manager names to their instances.
            val PACKAGE_MANAGERS = PackageManager.ALL.associateBy { it.toString().toUpperCase() }
        }

        override fun convert(name: String): PackageManagerFactory {
            return PACKAGE_MANAGERS[name.toUpperCase()]
                    ?: throw ParameterException("Package managers must be contained in ${PACKAGE_MANAGERS.keys}.")
        }
    }

    @Parameter(description = "The list of package managers to activate.",
            names = ["--package-managers", "-m"],
            converter = PackageManagerConverter::class,
            order = PARAMETER_ORDER_OPTIONAL)
    private var packageManagers = PackageManager.ALL

    @Parameter(description = "The project directory to analyze.",
            names = ["--input-dir", "-i"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    @Suppress("LateinitUsage")
    private lateinit var inputDir: File

    @Parameter(description = "The directory to write the analyzer result as ORT result file(s) to, in the specified " +
            "output format(s).",
            names = ["--output-dir", "-o"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    @Suppress("LateinitUsage")
    private lateinit var outputDir: File

    @Parameter(description = "The list of output formats to be used for the ORT result file(s).",
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

    @Parameter(description = "A YAML file that contains package curation data.",
            names = ["--package-curations-file"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var packageCurationsFile: File? = null

    @Parameter(description = "A file containing the repository configuration. If set the .ort.yml file from the " +
            "repository will be ignored.",
            names = ["--repository-configuration-file"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var repositoryConfigurationFile: File? = null

    override fun runCommand(jc: JCommander): Int {
        val absoluteOutputDir = outputDir.absoluteFile.normalize()

        val outputFiles = outputFormats.distinct().map { format ->
            File(absoluteOutputDir, "analyzer-result.${format.fileExtension}")
        }

        val existingOutputFiles = outputFiles.filter { it.exists() }
        if (existingOutputFiles.isNotEmpty()) {
            log.error { "None of the output files $existingOutputFiles must exist yet." }
            return 2
        }

        require(packageCurationsFile?.isFile != false) {
            "The package curations file '${packageCurationsFile!!.invariantSeparatorsPath}' could not be found."
        }

        require(repositoryConfigurationFile?.isFile != false) {
            "The repository configuration file '${repositoryConfigurationFile!!.invariantSeparatorsPath}' could " +
                    "not be found."
        }

        packageManagers = packageManagers.distinct()

        println("The following package managers are activated:")
        println("\t" + packageManagers.joinToString(", "))

        val absoluteInputDir = inputDir.absoluteFile.normalize()
        println("Scanning project path:\n\t$absoluteInputDir")

        val config = AnalyzerConfiguration(ignoreToolVersions, allowDynamicVersions)
        val analyzer = Analyzer(config)
        val ortResult = analyzer.analyze(absoluteInputDir, packageManagers, packageCurationsFile,
                repositoryConfigurationFile)

        println("Found ${ortResult.analyzer?.result?.projects.orEmpty().size} project(s) in total.")

        absoluteOutputDir.safeMkdirs()

        outputFiles.forEach { file ->
            println("Writing analyzer result to '$file'.")
            file.mapper().writerWithDefaultPrettyPrinter().writeValue(file, ortResult)
        }

        return 0
    }
}
