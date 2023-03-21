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

package org.ossreviewtoolkit.cli.commands

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file

import java.io.File
import java.time.Duration

import kotlin.time.toKotlinDuration

import org.ossreviewtoolkit.cli.OrtCommand
import org.ossreviewtoolkit.cli.utils.OPTION_GROUP_CONFIGURATION
import org.ossreviewtoolkit.cli.utils.PackageConfigurationOption
import org.ossreviewtoolkit.cli.utils.SeverityStats
import org.ossreviewtoolkit.cli.utils.configurationGroup
import org.ossreviewtoolkit.cli.utils.createProvider
import org.ossreviewtoolkit.cli.utils.inputGroup
import org.ossreviewtoolkit.cli.utils.logger
import org.ossreviewtoolkit.cli.utils.outputGroup
import org.ossreviewtoolkit.cli.utils.readOrtResult
import org.ossreviewtoolkit.cli.utils.writeOrtResult
import org.ossreviewtoolkit.evaluator.Evaluator
import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.LicenseFilePatterns
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.createFileArchiver
import org.ossreviewtoolkit.model.config.orEmpty
import org.ossreviewtoolkit.model.licenses.DefaultLicenseInfoProvider
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.licenses.orEmpty
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.readValueOrDefault
import org.ossreviewtoolkit.model.utils.CompositePackageConfigurationProvider
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.model.utils.SimplePackageConfigurationProvider
import org.ossreviewtoolkit.model.utils.addResolutions
import org.ossreviewtoolkit.model.utils.mergeLabels
import org.ossreviewtoolkit.plugins.packagecurationproviders.file.FilePackageCurationProvider
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.ort.ORT_COPYRIGHT_GARBAGE_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_EVALUATOR_RULES_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_LICENSE_CLASSIFICATIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory

class EvaluatorCommand : OrtCommand(
    name = "evaluate",
    help = "Evaluate ORT result files against policy rules."
) {
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
        .default(ortConfigDirectory.resolve(ORT_EVALUATOR_RULES_FILENAME))

    private val rulesResource by option(
        "--rules-resource",
        help = "The name of a script resource on the classpath that contains rules."
    )

    private val copyrightGarbageFile by option(
        "--copyright-garbage-file",
        help = "A file containing copyright statements which are marked as garbage."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .default(ortConfigDirectory.resolve(ORT_COPYRIGHT_GARBAGE_FILENAME))
        .configurationGroup()

    private val licenseClassificationsFile by option(
        "--license-classifications-file",
        help = "A file containing the license classifications which are passed as parameter to the rules script."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .default(ortConfigDirectory.resolve(ORT_LICENSE_CLASSIFICATIONS_FILENAME))
        .configurationGroup()

    private val packageConfigurationOption by mutuallyExclusiveOptions(
        option(
            "--package-configuration-dir",
            help = "A directory that is searched recursively for package configuration files. Each file must only " +
                    "contain a single package configuration. Must not be used together with " +
                    "'--package-configuration-file'."
        ).convert { it.expandTilde() }
            .file(mustExist = true, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = true)
            .convert { PackageConfigurationOption.Dir(it.absoluteFile.normalize()) },
        option(
            "--package-configuration-file",
            help = "A file containing a list of package configurations. Must not be used together with " +
                    "'--package-configuration-dir'."
        ).convert { it.expandTilde() }
            .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
            .convert { PackageConfigurationOption.File(it.absoluteFile.normalize()) },
        name = OPTION_GROUP_CONFIGURATION
    ).single()

    private val packageCurationsFile by option(
        "--package-curations-file",
        help = "A file containing package curation data. This replaces all package curations contained in the given " +
                "ORT result file with the ones present in the given file."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .configurationGroup()

    private val packageCurationsDir by option(
        "--package-curations-dir",
        help = "A directory containing package curation data. This replaces all package curations contained in the " +
                "given ORT result file with the ones present in the given directory."
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
        .default(ortConfigDirectory.resolve(ORT_RESOLUTIONS_FILENAME))
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

    private val ortConfig by requireObject<OrtConfiguration>()

    override fun run() {
        val scriptUrls = listOfNotNull(
            rulesFile.takeIf { it.isFile }?.toURI()?.toURL(),
            rulesResource?.let { javaClass.getResource(it) }
        )

        if (scriptUrls.isEmpty()) {
            throw BadParameterValue("Neither a rules file nor a rules resource was specified.")
        }

        val configurationFiles = listOfNotNull(
                copyrightGarbageFile,
                licenseClassificationsFile,
                packageCurationsFile,
                repositoryConfigurationFile
        )

        val configurationInfo = configurationFiles.joinToString("\n\t") { file ->
            file.absolutePath + " (does not exist)".takeIf { !file.exists() }.orEmpty()
        }

        println("Looking for evaluator-specific configuration in the following files and directories:")
        println("\t" + configurationInfo)

        // Fail early if output files exist and must not be overwritten.
        val outputFiles = mutableSetOf<File>()

        outputDir?.let { absoluteOutputDir ->
            outputFormats.mapTo(outputFiles) { format ->
                absoluteOutputDir.resolve("evaluation-result.${format.fileExtension}")
            }

            if (!ortConfig.forceOverwrite) {
                val existingOutputFiles = outputFiles.filter { it.exists() }
                if (existingOutputFiles.isNotEmpty()) {
                    throw UsageError("None of the output files $existingOutputFiles must exist yet.", statusCode = 2)
                }
            }
        }

        if (checkSyntax) {
            val evaluator = Evaluator()

            var allChecksSucceeded = true

            scriptUrls.forEach {
                if (evaluator.checkSyntax(it.readText())) {
                    println("Syntax check for $it succeeded.")
                } else {
                    println("Syntax check for $it failed.")
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
            val provider = FilePackageCurationProvider(packageCurationsFile, packageCurationsDir)
            ortResultInput = ortResultInput.replacePackageCurations(provider, providerId = "EvaluatorCommandOption")
        }

        val packageConfigurationProvider = if (ortConfig.enableRepositoryPackageConfigurations) {
            CompositePackageConfigurationProvider(
                SimplePackageConfigurationProvider(ortResultInput.repository.config.packageConfigurations),
                packageConfigurationOption.createProvider()
            )
        } else {
            if (ortResultInput.repository.config.packageConfigurations.isNotEmpty()) {
                logger.info { "Local package configurations were not applied because the feature is not enabled." }
            }

            packageConfigurationOption.createProvider()
        }

        val copyrightGarbage = copyrightGarbageFile.takeIf { it.isFile }?.readValue<CopyrightGarbage>().orEmpty()

        val licenseInfoResolver = LicenseInfoResolver(
            provider = DefaultLicenseInfoProvider(ortResultInput, packageConfigurationProvider),
            copyrightGarbage = copyrightGarbage,
            addAuthorsToCopyrights = ortConfig.addAuthorsToCopyrights,
            archiver = ortConfig.scanner.archive.createFileArchiver(),
            licenseFilePatterns = LicenseFilePatterns.getInstance()
        )

        val resolutionProvider = DefaultResolutionProvider.create(ortResultInput, resolutionsFile)
        val licenseClassifications =
            licenseClassificationsFile.takeIf { it.isFile }?.readValue<LicenseClassifications>().orEmpty()
        val evaluator = Evaluator(ortResultInput, licenseInfoResolver, resolutionProvider, licenseClassifications)

        val scripts = scriptUrls.map { it.readText() }
        val evaluatorRun = evaluator.run(*scripts.toTypedArray())

        val duration = with(evaluatorRun) { Duration.between(startTime, endTime).toKotlinDuration() }
        println("The evaluation of ${scriptUrls.size} script(s) took $duration.")

        evaluatorRun.violations.forEach { violation ->
            println(violation.format())
        }

        // Note: This overwrites any existing EvaluatorRun from the input file.
        val ortResultOutput = ortResultInput.copy(evaluator = evaluatorRun)
            .mergeLabels(labels)
            .addResolutions(resolutionProvider)

        outputDir?.let { absoluteOutputDir ->
            absoluteOutputDir.safeMkdirs()
            writeOrtResult(ortResultOutput, outputFiles, "evaluation")
        }

        val (resolvedViolations, unresolvedViolations) =
            evaluatorRun.violations.partition { resolutionProvider.isResolved(it) }
        val severityStats = SeverityStats.createFromRuleViolations(resolvedViolations, unresolvedViolations)

        severityStats.print().conclude(ortConfig.severeRuleViolationThreshold, 2)
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
