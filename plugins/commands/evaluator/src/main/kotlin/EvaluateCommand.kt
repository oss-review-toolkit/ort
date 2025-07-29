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

package org.ossreviewtoolkit.plugins.commands.evaluator

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.mordant.rendering.Theme

import java.io.File
import java.net.URI
import java.time.Duration

import kotlin.time.toKotlinDuration

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.evaluator.Evaluator
import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.ResolvedPackageCurations.Companion.REPOSITORY_CONFIGURATION_PROVIDER_ID
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.LicenseFilePatterns
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.createFileArchiver
import org.ossreviewtoolkit.model.config.orEmpty
import org.ossreviewtoolkit.model.licenses.DefaultLicenseInfoProvider
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.licenses.orEmpty
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.readValueOrDefault
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.model.utils.mergeLabels
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.commands.api.OrtCommand
import org.ossreviewtoolkit.plugins.commands.api.OrtCommandFactory
import org.ossreviewtoolkit.plugins.commands.api.utils.SeverityStatsPrinter
import org.ossreviewtoolkit.plugins.commands.api.utils.configurationGroup
import org.ossreviewtoolkit.plugins.commands.api.utils.inputGroup
import org.ossreviewtoolkit.plugins.commands.api.utils.outputGroup
import org.ossreviewtoolkit.plugins.commands.api.utils.readOrtResult
import org.ossreviewtoolkit.plugins.commands.api.utils.writeOrtResult
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.CompositePackageConfigurationProvider
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProviderFactory
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.SimplePackageConfigurationProvider
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.dir.DirPackageConfigurationProvider
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.SimplePackageCurationProvider
import org.ossreviewtoolkit.plugins.packagecurationproviders.file.FilePackageCurationProvider
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.config.setPackageConfigurations
import org.ossreviewtoolkit.utils.config.setPackageCurations
import org.ossreviewtoolkit.utils.config.setResolutions
import org.ossreviewtoolkit.utils.ort.ORT_COPYRIGHT_GARBAGE_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_EVALUATOR_RULES_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_LICENSE_CLASSIFICATIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory

@OrtPlugin(
    displayName = "Evaluate",
    description = "Evaluate ORT result files against policy rules.",
    factory = OrtCommandFactory::class
)
class EvaluateCommand(descriptor: PluginDescriptor = EvaluateCommandFactory.descriptor) : OrtCommand(descriptor) {
    private val ortFile by option(
        "--ort-file", "-i",
        help = "The ORT result file to read as input."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .inputGroup()

    private val outputDir by option(
        "--output-dir", "-o",
        help = "The directory to write the ORT result file with evaluation results to.  If no output directory is " +
            "specified, no ORT result file is written and only the exit code signals a success or failure."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .outputGroup()

    private val outputFormats by option(
        "--output-formats", "-f",
        help = "The list of output formats to be used for the ORT result file(s)."
    ).enum<FileFormat>().split(",").default(listOf(FileFormat.YAML)).outputGroup()

    private val rulesFile by option(
        "--rules-file", "-r",
        help = "The name of a script file containing rules."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .multiple()

    private val rulesResource by option(
        "--rules-resource",
        help = "The name of a script resource on the classpath that contains rules."
    ).multiple()

    private val copyrightGarbageFile by option(
        "--copyright-garbage-file",
        help = "A file containing copyright statements which are marked as garbage."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .default(ortConfigDirectory / ORT_COPYRIGHT_GARBAGE_FILENAME)
        .configurationGroup()

    private val licenseClassificationsFile by option(
        "--license-classifications-file",
        help = "A file containing the license classifications which are passed as parameter to the rules script."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .default(ortConfigDirectory / ORT_LICENSE_CLASSIFICATIONS_FILENAME)
        .configurationGroup()

    private val packageConfigurationsDir by option(
        "--package-configurations-dir",
        help = "A directory that is searched recursively for package configuration files. Each file must only " +
            "contain a single package configuration."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .configurationGroup()

    private val packageCurationsFile by option(
        "--package-curations-file",
        help = "A file containing package curations. This replaces all package curations contained in the given ORT " +
            "result file with the ones present in the given file and, if enabled, those from the repository " +
            "configuration."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .configurationGroup()

    private val packageCurationsDir by option(
        "--package-curations-dir",
        help = "A directory containing package curation files. This replaces all package curations contained in the " +
            "given ORT result file with the ones present in the given directory and, if enabled, those from the " +
            "repository configuration."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .configurationGroup()

    private val repositoryConfigurationFile by option(
        "--repository-configuration-file",
        help = "A file containing the repository configuration. If set, overrides the repository configuration " +
            "contained in the ORT result input file."
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
        .default(ortConfigDirectory / ORT_RESOLUTIONS_FILENAME)
        .configurationGroup()

    private val labels by option(
        "--label", "-l",
        help = "Set a label in the ORT result, overwriting any existing label of the same name. Can be used multiple " +
            "times. For example: --label distribution=external"
    ).associate()

    private val checkSyntax by option(
        "--check-syntax",
        help = "Do not evaluate the script but only check its syntax. No output is written in this case."
    ).flag()

    override fun run() {
        val scriptUris = mutableSetOf<URI>()

        rulesFile.mapTo(scriptUris) { it.toURI() }
        rulesResource.mapTo(scriptUris) { javaClass.getResource(it).toURI() }

        if (scriptUris.isEmpty()) {
            val defaultRulesFile = ortConfigDirectory / ORT_EVALUATOR_RULES_FILENAME

            if (defaultRulesFile.isFile) {
                scriptUris += defaultRulesFile.toURI()
            } else {
                echo(Theme.Default.danger("No rules specified."))
                throw ProgramResult(1)
            }
        }

        val configurationFiles = listOfNotNull(
            copyrightGarbageFile,
            licenseClassificationsFile,
            packageConfigurationsDir,
            packageCurationsFile,
            packageCurationsDir,
            repositoryConfigurationFile,
            resolutionsFile
        )

        val configurationInfo = configurationFiles.joinToString("\n\t", postfix = "\n\t") { file ->
            file.absolutePath + " (does not exist)".takeIf { !file.exists() }.orEmpty()
        } + scriptUris.joinToString("\n\t") {
            runCatching { File(it).absolutePath }.getOrDefault(it.toString())
        }

        echo("Looking for evaluator-specific configuration in the following files, directories and resources:")
        echo("\t" + configurationInfo)

        // Fail early if output files exist and must not be overwritten.
        val outputFiles = mutableSetOf<File>()

        outputDir?.let { absoluteOutputDir ->
            outputFormats.mapTo(outputFiles) { format ->
                absoluteOutputDir / "evaluation-result.${format.fileExtension}"
            }

            validateOutputFiles(outputFiles)
        }

        if (checkSyntax) {
            val evaluator = Evaluator()

            var allChecksSucceeded = true

            scriptUris.forEach {
                if (evaluator.checkSyntax(it.toURL().readText())) {
                    echo("Syntax check for $it succeeded.")
                } else {
                    echo("Syntax check for $it failed.")
                    allChecksSucceeded = false
                }
            }

            if (allChecksSucceeded) return else throw ProgramResult(2)
        }

        val existingOrtFile = requireNotNull(ortFile) {
            "The '--ort-file' option is required unless the '--check-syntax' option is used."
        }

        var ortResultInput = readOrtResult(existingOrtFile)

        repositoryConfigurationFile?.let {
            val config = it.readValueOrDefault(RepositoryConfiguration())
            ortResultInput = ortResultInput.replaceConfig(config)
        }

        if (packageCurationsDir != null || packageCurationsFile != null) {
            val packageCurationProviders = buildList {
                if (ortConfig.enableRepositoryPackageCurations) {
                    val packageCurations = ortResultInput.repository.config.curations.packages
                    add(REPOSITORY_CONFIGURATION_PROVIDER_ID to SimplePackageCurationProvider(packageCurations))
                }

                val providerFromOption = FilePackageCurationProvider(packageCurationsFile, packageCurationsDir)
                add("EvaluatorCommandOption" to providerFromOption)
            }

            ortResultInput = ortResultInput.setPackageCurations(packageCurationProviders)
        }

        val enabledPackageConfigurationProviders = buildList {
            val repositoryPackageConfigurations = ortResultInput.repository.config.packageConfigurations

            if (ortConfig.enableRepositoryPackageConfigurations) {
                add(SimplePackageConfigurationProvider(configurations = repositoryPackageConfigurations))
            } else {
                if (repositoryPackageConfigurations.isNotEmpty()) {
                    logger.info { "Local package configurations were not applied because the feature is not enabled." }
                }
            }

            if (packageConfigurationsDir != null) {
                add(DirPackageConfigurationProvider(packageConfigurationsDir))
            } else {
                val packageConfigurationProviders =
                    PackageConfigurationProviderFactory.create(ortConfig.packageConfigurationProviders)
                addAll(packageConfigurationProviders.map { it.second })
            }
        }

        val packageConfigurationProvider =
            CompositePackageConfigurationProvider(*enabledPackageConfigurationProviders.toTypedArray())

        ortResultInput = ortResultInput.setPackageConfigurations(packageConfigurationProvider)

        val copyrightGarbage = copyrightGarbageFile.takeIf { it.isFile }?.readValue<CopyrightGarbage>().orEmpty()

        val licenseInfoResolver = LicenseInfoResolver(
            provider = DefaultLicenseInfoProvider(ortResultInput),
            copyrightGarbage = copyrightGarbage,
            addAuthorsToCopyrights = ortConfig.addAuthorsToCopyrights,
            archiver = ortConfig.scanner.archive.createFileArchiver(),
            licenseFilePatterns = LicenseFilePatterns.getInstance()
        )

        val resolutionProvider = DefaultResolutionProvider.create(ortResultInput, resolutionsFile)
        val licenseClassifications =
            licenseClassificationsFile.takeIf { it.isFile }?.readValue<LicenseClassifications>().orEmpty()
        val evaluator = Evaluator(ortResultInput, licenseInfoResolver, resolutionProvider, licenseClassifications)

        val scripts = scriptUris.map { it.toURL().readText() }
        val evaluatorRun = evaluator.run(*scripts.toTypedArray())

        if (evaluatorRun.violations.isNotEmpty()) {
            echo("The following ${evaluatorRun.violations.size} rule violations have been found:")

            evaluatorRun.violations.forEach { violation ->
                echo(violation.format())
            }
        } else {
            echo("No rule violations have been found.")
        }

        val duration = with(evaluatorRun) { Duration.between(startTime, endTime).toKotlinDuration() }
        echo("The evaluation of ${scriptUris.size} script(s) took $duration.")

        // Note: This overwrites any existing EvaluatorRun from the input file.
        val ortResultOutput = ortResultInput.copy(evaluator = evaluatorRun)
            .mergeLabels(labels)
            .setResolutions(resolutionProvider)

        outputDir?.let { absoluteOutputDir ->
            absoluteOutputDir.safeMkdirs()
            writeOrtResult(ortResultOutput, outputFiles, terminal)
        }

        SeverityStatsPrinter(terminal, resolutionProvider).stats(evaluatorRun.violations)
            .print().conclude(ortConfig.severeRuleViolationThreshold, 2)
    }
}

private fun RuleViolation.format() =
    buildString {
        append(severity)
        append(": ")
        append(rule)
        append(" - ")
        pkg?.let { id ->
            append(id.toCoordinates())
            append(" - ")
        }

        license?.let { license ->
            append(license)
            licenseSource?.let { source ->
                append(" (")
                append(source)
                append(")")
            }

            append(" - ")
        }

        append(message)
    }
