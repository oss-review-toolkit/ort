/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2020-2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.cli.commands

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.deprecated
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.analyzer.Analyzer
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.analyzer.curation.ClearlyDefinedPackageCurationProvider
import org.ossreviewtoolkit.analyzer.curation.CompositePackageCurationProvider
import org.ossreviewtoolkit.analyzer.curation.FallbackPackageCurationProvider
import org.ossreviewtoolkit.analyzer.curation.FilePackageCurationProvider
import org.ossreviewtoolkit.analyzer.curation.OrtConfigPackageCurationProvider
import org.ossreviewtoolkit.analyzer.curation.SimplePackageCurationProvider
import org.ossreviewtoolkit.analyzer.curation.Sw360PackageCurationProvider
import org.ossreviewtoolkit.cli.GlobalOptions
import org.ossreviewtoolkit.cli.utils.SeverityStats
import org.ossreviewtoolkit.cli.utils.configurationGroup
import org.ossreviewtoolkit.cli.utils.inputGroup
import org.ossreviewtoolkit.cli.utils.outputGroup
import org.ossreviewtoolkit.cli.utils.writeOrtResult
import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.readValueOrNull
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.model.utils.mergeLabels
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.ort.ORT_PACKAGE_CURATIONS_DIRNAME
import org.ossreviewtoolkit.utils.ort.ORT_PACKAGE_CURATIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_REPO_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.log
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory

private val allPackageManagersByName = PackageManager.ALL.associateBy { it.managerName }
    .toSortedMap(String.CASE_INSENSITIVE_ORDER)

class AnalyzerCommand : CliktCommand(name = "analyze", help = "Determine dependencies of a software project.") {
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

    private val packageCurationsFile by option(
        "--package-curations-file",
        help = "A file containing package curation data."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .default(ortConfigDirectory.resolve(ORT_PACKAGE_CURATIONS_FILENAME))
        .configurationGroup()

    private val packageCurationsDir by option(
        "--package-curations-dir",
        help = "A directory containing package curation data."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .default(ortConfigDirectory.resolve(ORT_PACKAGE_CURATIONS_DIRNAME))
        .configurationGroup()

    private val repositoryConfigurationFile by option(
        "--repository-configuration-file",
        help = "A file containing the repository configuration. If set, the '$ORT_REPO_CONFIG_FILENAME' file from " +
                "the repository is ignored."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .configurationGroup()

    private val resolutionsFile by option(
        "--resolutions-file",
        help = "A file containing issue and rule violation resolutions."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .default(ortConfigDirectory.resolve(ORT_RESOLUTIONS_FILENAME))
        .configurationGroup()

    private val useClearlyDefinedCurations by option(
        "--clearly-defined-curations",
        help = "Whether to fall back to package curation data from the ClearlyDefine service or not."
    ).flag()

    private val useOrtCurations by option(
        "--ort-curations",
        help = "Whether to fall back to package curation data from the ort-config repository or not."
    ).flag()

    private val useSw360Curations by option(
        "--sw360-curations",
        help = "Whether to fall back to package curation data from the SW360 service or not."
    ).flag()

    private val labels by option(
        "--label", "-l",
        help = "Set a label in the ORT result, overwriting any existing label of the same name. Can be used multiple " +
                "times. For example: --label distribution=external"
    ).associate()

    private val activatedPackageManagers by option(
        "--package-managers", "-m",
        help = "The comma-separated package managers to activate, any of ${allPackageManagersByName.keys}. Note that " +
                "deactivation overrides activation. If set, package manager activation settings from configuration " +
                "files are ignored."
    ).convert { name ->
        allPackageManagersByName[name]
            ?: throw BadParameterValue("Package managers must be one or more of ${allPackageManagersByName.keys}.")
    }.split(",").deprecated(
        message = "--package-managers is deprecated, use -P ort.analyzer.enabledPackageManagers=... on the ort " +
                "command instead.",
        tagValue = "use -P ort.analyzer.enabledPackageManagers=... on the ort command instead"
    )

    private val deactivatedPackageManagers by option(
        "--not-package-managers", "-n",
        help = "The comma-separated package managers to deactivate, any of ${allPackageManagersByName.keys}. Note " +
                "that deactivation overrides activation. If set, package manager activation settings from " +
                "configuration files are ignored."
    ).convert { name ->
        allPackageManagersByName[name]
            ?: throw BadParameterValue("Package managers must be one or more of ${allPackageManagersByName.keys}.")
    }.split(",").deprecated(
        message = "--not-package-managers is deprecated, use -P ort.analyzer.disabledPackageManagers=... on the ort " +
                "command instead.",
        tagValue = "use -P ort.analyzer.disabledPackageManagers=... on the ort command instead"
    )

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

        // Manually set a default value based on another option to avoid a "Cannot read from option delegate before
        // parsing command line" error.
        val actualRepositoryConfigurationFile = repositoryConfigurationFile
            ?: inputDir.resolve(ORT_REPO_CONFIG_FILENAME)

        val configurationFiles = listOf(
            packageCurationsFile,
            packageCurationsDir,
            actualRepositoryConfigurationFile,
            resolutionsFile
        )

        val configurationInfo = configurationFiles.joinToString("\n\t") { file ->
            file.absolutePath + " (does not exist)".takeIf { !file.exists() }.orEmpty()
        }

        println("Looking for analyzer-specific configuration in the following files and directories:")
        println("\t" + configurationInfo)

        val config = globalOptionsForSubcommands.config

        val enabledPackageManagers = if (activatedPackageManagers != null || deactivatedPackageManagers != null) {
            (activatedPackageManagers?.toSet() ?: PackageManager.ALL) - deactivatedPackageManagers.orEmpty().toSet()
        } else {
            config.analyzer.determineEnabledPackageManagers()
        }

        println("The following package managers are enabled:")
        println("\t" + enabledPackageManagers.joinToString())

        println("Analyzing project path:\n\t$inputDir")

        val repositoryConfiguration = actualRepositoryConfigurationFile.takeIf { it.isFile }?.readValueOrNull()
            ?: RepositoryConfiguration()

        val analyzerConfiguration =
            repositoryConfiguration.analyzer?.let { config.analyzer.merge(it) } ?: config.analyzer

        val analyzer = Analyzer(analyzerConfiguration, labels)

        val defaultCurationProviders = buildList {
            if (useOrtCurations) add(OrtConfigPackageCurationProvider())

            add(FilePackageCurationProvider.from(packageCurationsFile, packageCurationsDir))

            val repositoryPackageCurations = repositoryConfiguration.curations.packages

            if (config.enableRepositoryPackageCurations) {
                add(SimplePackageCurationProvider(repositoryPackageCurations))
            } else if (repositoryPackageCurations.isNotEmpty()) {
                this@AnalyzerCommand.log.warn {
                    "Existing package curations from '$ORT_REPO_CONFIG_FILENAME' are not applied because the feature " +
                            "is disabled."
                }
            }
        }

        val curationProviders = listOfNotNull(
            CompositePackageCurationProvider(defaultCurationProviders),
            config.analyzer.sw360Configuration?.let {
                Sw360PackageCurationProvider(it).takeIf { useSw360Curations }
            },
            ClearlyDefinedPackageCurationProvider().takeIf { useClearlyDefinedCurations }
        )

        val curationProvider = FallbackPackageCurationProvider(curationProviders)

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

            println("Found $count definition file(s) from ${filesPerManager.size} package manager(s) in total.")
        }

        val ortResult = analyzer.analyze(info, curationProvider).mergeLabels(labels)

        val projectCount = ortResult.getProjects().size
        val packageCount = ortResult.getPackages().size
        println("Found $projectCount project(s) and $packageCount package(s) in total (not counting excluded ones).")

        val curationCount = ortResult.getPackages().sumOf { it.curations.size }
        println("Applied $curationCount curation(s) from ${curationProviders.size} provider(s).")

        outputDir.safeMkdirs()
        writeOrtResult(ortResult, outputFiles, "analyzer")

        val analyzerResult = ortResult.analyzer?.result

        if (analyzerResult == null) {
            println("There was an error creating the analyzer result.")
            throw ProgramResult(1)
        }

        val resolutionProvider = DefaultResolutionProvider.create(ortResult, resolutionsFile)
        val (resolvedIssues, unresolvedIssues) =
            analyzerResult.collectIssues().flatMap { it.value }.partition { resolutionProvider.isResolved(it) }
        val severityStats = SeverityStats.createFromIssues(resolvedIssues, unresolvedIssues)

        severityStats.print().conclude(config.severeIssueThreshold, 2)
    }
}

private fun AnalyzerConfiguration.determineEnabledPackageManagers(): Set<PackageManagerFactory> {
    val enabled = enabledPackageManagers?.mapNotNull { allPackageManagersByName[it] }?.toSet() ?: PackageManager.ALL
    val disabled = disabledPackageManagers?.mapNotNull { allPackageManagersByName[it] }?.toSet().orEmpty()

    return enabled - disabled
}
