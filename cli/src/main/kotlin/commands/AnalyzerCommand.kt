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

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file

import com.here.ort.analyzer.Analyzer
import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.curation.ClearlyDefinedPackageCurationProvider
import com.here.ort.analyzer.curation.FallbackPackageCurationProvider
import com.here.ort.analyzer.curation.FilePackageCurationProvider
import com.here.ort.model.OutputFormat
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.mapper
import com.here.ort.utils.expandTilde
import com.here.ort.utils.safeMkdirs

import java.io.File

class AnalyzerCommand : CliktCommand(name = "analyze", help = "Determine dependencies of a software project.") {
    private val packageManagersOption by option(
        "--package-managers", "-m",
        help = "The list of package managers to activate."
    ).convert { packageManagerName ->
        // Map upper-cased package manager names to their instances.
        val packageManagers = PackageManager.ALL.associateBy { it.managerName.toUpperCase() }
        packageManagers[packageManagerName.toUpperCase()]
            ?: throw BadParameterValue("Package managers must be contained in ${packageManagers.keys}.")
    }.split(",").default(PackageManager.ALL)

    private val inputDir by option(
        "--input-dir", "-i",
        help = "The project directory to analyze."
    ).file(exists = true, fileOkay = false, folderOkay = true, writable = false, readable = true).required()

    private val outputDir by option(
        "--output-dir", "-o",
        help = "The directory to write the analyzer result as ORT result file(s) to, in the specified output format(s)."
    ).file(exists = false, fileOkay = false, folderOkay = true, writable = false, readable = false).required()

    private val outputFormats by option(
        "--output-formats", "-f",
        help = "The list of output formats to be used for the ORT result file(s)."
    ).enum<OutputFormat>().split(",").default(listOf(OutputFormat.YAML))

    private val ignoreToolVersions by option(
        "--ignore-tool-versions",
        help = "Ignore versions of required tools. NOTE: This may lead to erroneous results."
    ).flag()

    private val allowDynamicVersions by option(
        "--allow-dynamic-versions",
        help = "Allow dynamic versions of dependencies. This can result in unstable results when dependencies use " +
                "version ranges. This option only affects package managers that support lock files, like NPM."
    ).flag()

    private val packageCurationsFile by option(
        "--package-curations-file",
        help = "A file containing package curation data."
    ).file(exists = true, fileOkay = true, folderOkay = false, writable = false, readable = true)

    private val useClearlyDefinedCurations by option(
        "--clearly-defined-curations",
        help = "Whether to fall back to package curation data from the ClearlyDefine service or not."
    ).flag()

    private val repositoryConfigurationFile by option(
        "--repository-configuration-file",
        help = "A file containing the repository configuration. If set the .ort.yml file from the repository will be " +
                "ignored."
    ).file(exists = true, fileOkay = true, folderOkay = false, writable = false, readable = true)

    override fun run() {
        val absoluteOutputDir = outputDir.expandTilde().normalize()

        val outputFiles = outputFormats.distinct().map { format ->
            File(absoluteOutputDir, "analyzer-result.${format.fileExtension}")
        }

        val existingOutputFiles = outputFiles.filter { it.exists() }
        if (existingOutputFiles.isNotEmpty()) {
            throw UsageError("None of the output files $existingOutputFiles must exist yet.", statusCode = 2)
        }

        val absolutePackageCurationsFile = packageCurationsFile?.expandTilde()
        val absoluteRepositoryConfigurationFile = repositoryConfigurationFile?.expandTilde()

        val packageManagers = packageManagersOption.distinct()

        println("The following package managers are activated:")
        println("\t" + packageManagers.joinToString(", "))

        val absoluteInputDir = inputDir.expandTilde().normalize()
        println("Analyzing project path:\n\t$absoluteInputDir")

        val analyzerConfig = AnalyzerConfiguration(ignoreToolVersions, allowDynamicVersions)
        val analyzer = Analyzer(analyzerConfig)

        val curationProvider = FallbackPackageCurationProvider(
            listOfNotNull(
                absolutePackageCurationsFile?.let { FilePackageCurationProvider(it) },
                ClearlyDefinedPackageCurationProvider().takeIf { useClearlyDefinedCurations }
            )
        )

        val ortResult = analyzer.analyze(
            absoluteInputDir, packageManagers, curationProvider, absoluteRepositoryConfigurationFile
        )

        println("Found ${ortResult.getProjects().size} project(s) in total.")

        absoluteOutputDir.safeMkdirs()

        outputFiles.forEach { file ->
            println("Writing analyzer result to '$file'.")
            file.mapper().writerWithDefaultPrettyPrinter().writeValue(file, ortResult)
        }
    }
}
