/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2020-2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.commands

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file

import kotlin.time.measureTime

import org.ossreviewtoolkit.GlobalOptions
import org.ossreviewtoolkit.analyzer.Analyzer
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.curation.ClearlyDefinedPackageCurationProvider
import org.ossreviewtoolkit.analyzer.curation.FallbackPackageCurationProvider
import org.ossreviewtoolkit.analyzer.curation.FilePackageCurationProvider
import org.ossreviewtoolkit.analyzer.curation.Sw360PackageCurationProvider
import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.mapper
import org.ossreviewtoolkit.model.utils.mergeLabels
import org.ossreviewtoolkit.utils.ORT_CURATIONS_FILENAME
import org.ossreviewtoolkit.utils.ORT_REPO_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.expandTilde
import org.ossreviewtoolkit.utils.formatSizeInMib
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.ortConfigDirectory
import org.ossreviewtoolkit.utils.perf
import org.ossreviewtoolkit.utils.safeMkdirs

class AnalyzerCommand : CliktCommand(name = "analyze", help = "Determine dependencies of a software project.") {
    private val allPackageManagersByName = PackageManager.ALL.associateBy { it.managerName.toUpperCase() }

    private val inputDir by option(
        "--input-dir", "-i",
        help = "The project directory to analyze. As a special case, if only one package manager is activated, this " +
                "may point to a definition file for that package manager to only analyze that single project."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = true, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()
        .inputGroup()

    private val outputDir by option(
        "--output-dir", "-o",
        help = "The directory to write the analyzer result as ORT result file(s) to, in the specified output format(s)."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()
        .outputGroup()

    private val outputFormats by option(
        "--output-formats", "-f",
        help = "The list of output formats to be used for the ORT result file(s)."
    ).enum<FileFormat>().split(",").default(listOf(FileFormat.YAML)).outputGroup()

    private val packageCurationsFile by option(
        "--package-curations-file",
        help = "A file containing package curation data."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .default(ortConfigDirectory.resolve(ORT_CURATIONS_FILENAME))
        .configurationGroup()

    private val repositoryConfigurationFile by option(
        "--repository-configuration-file",
        help = "A file containing the repository configuration. If set, the '$ORT_REPO_CONFIG_FILENAME' file from " +
                "the repository is ignored."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .configurationGroup()

    private val useClearlyDefinedCurations by option(
        "--clearly-defined-curations",
        help = "Whether to fall back to package curation data from the ClearlyDefine service or not."
    ).flag()

    private val useSw360Curations by option(
        "--sw360-curations",
        help = "Whether to fall back to package curation data from the SW360 service or not."
    ).flag()

    private val labels by option(
        "--label", "-l",
        help = "Add a label to the ORT result. Can be used multiple times. For example: --label distribution=external"
    ).associate()

    private val packageManagers by option(
        "--package-managers", "-m",
        help = "The list of package managers to activate."
    ).convert { name ->
        allPackageManagersByName[name.toUpperCase()]
            ?: throw BadParameterValue("Package managers must be one or more of ${allPackageManagersByName.keys}.")
    }.split(",").default(PackageManager.ALL)

    private val globalOptionsForSubcommands by requireObject<GlobalOptions>()

    override fun run() {
        val outputFiles = outputFormats.mapTo(mutableSetOf()) { format ->
            outputDir.resolve("analyzer-result.${format.fileExtension}")
        }

        if (!globalOptionsForSubcommands.forceOverwrite) {
            val existingOutputFiles = outputFiles.filter { it.exists() }
            if (existingOutputFiles.isNotEmpty()) {
                throw UsageError("None of the output files $existingOutputFiles must exist yet.", statusCode = 2)
            }
        }

        val configurationFiles = listOfNotNull(packageCurationsFile, repositoryConfigurationFile)
                .map { it.absolutePath }
        println("The following configuration files are used:")
        println("\t" + configurationFiles.joinToString("\n\t"))

        val distinctPackageManagers = packageManagers.distinct()
        println("The following package managers are activated:")
        println("\t" + distinctPackageManagers.joinToString(", "))

        println("Analyzing project path:\n\t$inputDir")

        val config = globalOptionsForSubcommands.config
        val analyzer = Analyzer(config.analyzer)

        val curationProvider = FallbackPackageCurationProvider(
            listOfNotNull(
                packageCurationsFile.takeIf { it.isFile }?.let { FilePackageCurationProvider(it) },
                config.analyzer.sw360Configuration?.let {
                    Sw360PackageCurationProvider(it).takeIf { useSw360Curations }
                },
                ClearlyDefinedPackageCurationProvider().takeIf { useClearlyDefinedCurations }
            )
        )

        val ortResult = analyzer.analyze(
            inputDir, distinctPackageManagers, curationProvider, repositoryConfigurationFile
        ).mergeLabels(labels)

        println("Found ${ortResult.getProjects().size} project(s) in total.")

        outputDir.safeMkdirs()

        outputFiles.forEach { file ->
            println("Writing analyzer result to '$file'.")
            val duration = measureTime { file.mapper().writerWithDefaultPrettyPrinter().writeValue(file, ortResult) }

            log.perf {
                "Wrote ORT result to '${file.name}' (${file.formatSizeInMib}) in ${duration.inMilliseconds}ms."
            }
        }

        val analyzerResult = ortResult.analyzer?.result

        if (analyzerResult == null) {
            println("There was an error creating the analyzer result.")
            throw ProgramResult(1)
        }

        if (analyzerResult.hasIssues) {
            println("The analyzer result contains issues.")
            throw ProgramResult(2)
        }
    }
}
