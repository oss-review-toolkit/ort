/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.commands.analyzer

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file

import java.time.Duration

import kotlin.time.toKotlinDuration

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.analyzer.Analyzer
import org.ossreviewtoolkit.analyzer.determineEnabledPackageManagers
import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.ResolvedPackageCurations.Companion.REPOSITORY_CONFIGURATION_PROVIDER_ID
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.readValueOrNull
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.model.utils.mergeLabels
import org.ossreviewtoolkit.plugins.commands.api.OrtCommand
import org.ossreviewtoolkit.plugins.commands.api.utils.SeverityStats
import org.ossreviewtoolkit.plugins.commands.api.utils.configurationGroup
import org.ossreviewtoolkit.plugins.commands.api.utils.inputGroup
import org.ossreviewtoolkit.plugins.commands.api.utils.outputGroup
import org.ossreviewtoolkit.plugins.commands.api.utils.writeOrtResult
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProviderFactory
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.SimplePackageCurationProvider
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.ort.ORT_REPO_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory

class AnalyzerCommand : OrtCommand(
    name = "analyze",
    help = "Determine dependencies of a software project."
) {
    private companion object : Logging

    private val inputDir by option(
        "--input-dir", "-i",
        help = "The project directory to analyze. As a special case, if only one package manager is enabled, this " +
                "may point to a definition file for that package manager to only analyze that single project."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = true, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()
        .inputGroup()

    private val outputDir by option(
        "--output-dir", "-o",
        help = "The directory to write the ORT result file with analyzer results to."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()
        .outputGroup()

    private val outputFormats by option(
        "--output-formats", "-f",
        help = "The list of output formats to be used for the ORT result file(s)."
    ).enum<FileFormat>().split(",").default(listOf(FileFormat.YAML)).outputGroup()

    private val repositoryConfigurationFile by option(
        "--repository-configuration-file",
        help = "A file containing the repository configuration. If set, overrides any repository configuration " +
                "contained in a '$ORT_REPO_CONFIG_FILENAME' file in the repository."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .defaultLazy { inputDir.resolve(ORT_REPO_CONFIG_FILENAME) }
        .configurationGroup()

    private val resolutionsFile by option(
        "--resolutions-file",
        help = "A file containing issue and rule violation resolutions."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .default(ortConfigDirectory.resolve(ORT_RESOLUTIONS_FILENAME))
        .configurationGroup()

    private val labels by option(
        "--label", "-l",
        help = "Set a label in the ORT result, overwriting any existing label of the same name. Can be used multiple " +
                "times. For example: --label distribution=external"
    ).associate()

    private val dryRun by option(
        "--dry-run",
        help = "Do not actually run the project analysis but only show the package managers that would be used."
    ).flag()

    override fun run() {
        val outputFiles = outputFormats.mapTo(mutableSetOf()) { format ->
            outputDir.resolve("analyzer-result.${format.fileExtension}")
        }

        validateOutputFiles(outputFiles)

        val configurationFiles = listOf(
            repositoryConfigurationFile,
            resolutionsFile
        )

        val configurationInfo = configurationFiles.joinToString("\n\t") { file ->
            file.absolutePath + " (does not exist)".takeIf { !file.exists() }.orEmpty()
        }

        println("Looking for analyzer-specific configuration in the following files and directories:")
        println("\t" + configurationInfo)

        val repositoryConfiguration = repositoryConfigurationFile.takeIf { it.isFile }?.readValueOrNull()
            ?: RepositoryConfiguration()

        val analyzerConfiguration = repositoryConfiguration.analyzer?.let { ortConfig.analyzer.merge(it) }
            ?: ortConfig.analyzer

        val enabledPackageManagers = analyzerConfiguration.determineEnabledPackageManagers()

        println("The following ${enabledPackageManagers.size} package manager(s) are enabled:")
        println("\t" + enabledPackageManagers.joinToString().ifEmpty { "<None>" })

        val analyzer = Analyzer(analyzerConfiguration, labels)

        val enabledCurationProviders = buildList {
            val repositoryPackageCurations = repositoryConfiguration.curations.packages

            if (ortConfig.enableRepositoryPackageCurations) {
                add(REPOSITORY_CONFIGURATION_PROVIDER_ID to SimplePackageCurationProvider(repositoryPackageCurations))
            } else if (repositoryPackageCurations.isNotEmpty()) {
                logger.warn {
                    "Existing package curations from '${repositoryConfigurationFile.absolutePath}' are not applied " +
                            "because the feature is disabled."
                }
            }

            addAll(PackageCurationProviderFactory.create(ortConfig.packageCurationProviders))
        }

        println("The following ${enabledCurationProviders.size} package curation provider(s) are enabled:")
        println("\t" + enabledCurationProviders.joinToString { it.first }.ifEmpty { "<None>" })

        println("Analyzing project path:\n\t$inputDir")

        val info = analyzer.findManagedFiles(inputDir, enabledPackageManagers, repositoryConfiguration)
        if (info.managedFiles.isEmpty()) {
            println("No definition files found.")
        } else {
            val filesPerManager = info.managedFiles.mapKeysTo(sortedMapOf()) { it.key.managerName }
            var count = 0

            filesPerManager.forEach { (manager, files) ->
                count += files.size
                println("Found ${files.size} $manager definition file(s) at:")

                files.forEach { file ->
                    val relativePath = file.toRelativeString(inputDir).takeIf { it.isNotEmpty() } ?: "."
                    println("\t$relativePath")
                }
            }

            println(
                "Found in total $count definition file(s) from the following ${filesPerManager.size} package " +
                    "manager(s):\n\t${filesPerManager.keys.joinToString()}"
            )
        }

        if (dryRun) {
            println("Not performing the actual project analysis as this is a dry run.")
            return
        }

        val ortResult = analyzer.analyze(info, enabledCurationProviders).mergeLabels(labels)

        outputDir.safeMkdirs()
        writeOrtResult(ortResult, outputFiles, "analyzer")

        val analyzerRun = ortResult.analyzer
        if (analyzerRun == null) {
            println("No analyzer run was created.")
            throw ProgramResult(1)
        }

        val duration = with(analyzerRun) { Duration.between(startTime, endTime).toKotlinDuration() }
        println("The analysis took $duration.")

        val projects = ortResult.getProjects(omitExcluded = true)
        val packages = ortResult.getPackages(omitExcluded = true)
        println(
            "Found ${projects.size} project(s) and ${packages.size} package(s) in total (not counting excluded ones)."
        )

        val curationCount = packages.sumOf { it.curations.size }
        val providerCount = ortResult.resolvedConfiguration.packageCurations.count { it.curations.isNotEmpty() }
        println(
            "Applied $curationCount curation(s) from $providerCount of ${enabledCurationProviders.size} provider(s)."
        )

        val resolutionProvider = DefaultResolutionProvider.create(ortResult, resolutionsFile)
        val (resolvedIssues, unresolvedIssues) = analyzerRun.result.getAllIssues().flatMap { it.value }
            .partition { resolutionProvider.isResolved(it) }
        val severityStats = SeverityStats.createFromIssues(resolvedIssues, unresolvedIssues)

        severityStats.print().conclude(ortConfig.severeIssueThreshold, 2)
    }
}
