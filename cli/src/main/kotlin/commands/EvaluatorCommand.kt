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

package org.ossreviewtoolkit.commands

import com.github.ajalt.clikt.core.CliktCommand
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

import kotlin.time.measureTime
import kotlin.time.measureTimedValue

import org.ossreviewtoolkit.GlobalOptions
import org.ossreviewtoolkit.GroupTypes.FileType
import org.ossreviewtoolkit.GroupTypes.StringType
import org.ossreviewtoolkit.evaluator.Evaluator
import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.LicenseFilenamePatterns
import org.ossreviewtoolkit.model.config.createFileArchiver
import org.ossreviewtoolkit.model.config.orEmpty
import org.ossreviewtoolkit.model.licenses.DefaultLicenseInfoProvider
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.licenses.orEmpty
import org.ossreviewtoolkit.model.mapper
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.utils.mergeLabels
import org.ossreviewtoolkit.utils.ORT_COPYRIGHT_GARBAGE_FILENAME
import org.ossreviewtoolkit.utils.ORT_LICENSE_CLASSIFICATIONS_FILENAME
import org.ossreviewtoolkit.utils.ORT_REPO_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.PackageConfigurationOption
import org.ossreviewtoolkit.utils.createProvider
import org.ossreviewtoolkit.utils.expandTilde
import org.ossreviewtoolkit.utils.formatSizeInMib
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.ortConfigDirectory
import org.ossreviewtoolkit.utils.perf
import org.ossreviewtoolkit.utils.safeMkdirs

class EvaluatorCommand : CliktCommand(name = "evaluate", help = "Evaluate ORT result files against policy rules.") {
    private val ortFile by option(
        "--ort-file", "-i",
        help = "The ORT result file to read as input."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .inputGroup()

    private val outputDir by option(
        "--output-dir", "-o",
        help = "The directory to write the evaluation results as ORT result file(s) to, in the specified output " +
                "format(s). If no output directory is specified, no ORT result file(s) are written and only the exit " +
                "code signals a success or failure."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .outputGroup()

    private val outputFormats by option(
        "--output-formats", "-f",
        help = "The list of output formats to be used for the ORT result file(s)."
    ).enum<FileFormat>().split(",").default(listOf(FileFormat.YAML)).outputGroup()

    private val rules by mutuallyExclusiveOptions(
        option(
            "--rules-file", "-r",
            help = "The name of a script file containing rules. Must not be used together with '--rules-resource'."
        ).convert { it.expandTilde() }
            .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
            .convert { FileType(it.absoluteFile.normalize()) },
        option(
            "--rules-resource",
            help = "The name of a script resource on the classpath that contains rules. Must not be used together " +
                    "with '--rules-file'."
        ).convert { StringType(it) },
        name = OPTION_GROUP_RULE
    ).single()

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
        help = "A file containing the license classificationsm which are passed as parameter to the rules script."
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

    private val repositoryConfigurationFile by option(
        "--repository-configuration-file",
        help = "A file containing the repository configuration. If set, the '$ORT_REPO_CONFIG_FILENAME' overrides " +
                "the repository configuration contained in the ORT result from the input file."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .configurationGroup()

    private val labels by option(
        "--label", "-l",
        help = "Add a label to the ORT result. Can be used multiple times. Any existing label with the same key in " +
                "the input ORT result is overwritten. For example: --label distribution=external"
    ).associate()

    private val checkSyntax by option(
        "--check-syntax",
        help = "Do not evaluate the script but only check its syntax. No output is written in this case."
    ).flag()

    private val globalOptionsForSubcommands by requireObject<GlobalOptions>()

    override fun run() {
        val configurationFiles = listOfNotNull(
                copyrightGarbageFile,
                licenseClassificationsFile,
                packageCurationsFile,
                repositoryConfigurationFile
        ).map { it.absolutePath }
        println("The following configuration files are used:")
        println("\t" + configurationFiles.joinToString("\n\t"))

        // Fail early if output files exist and must not be overwritten.
        val outputFiles = mutableSetOf<File>()

        outputDir?.let { absoluteOutputDir ->
            outputFormats.mapTo(outputFiles) { format ->
                absoluteOutputDir.resolve("evaluation-result.${format.fileExtension}")
            }

            if (!globalOptionsForSubcommands.forceOverwrite) {
                val existingOutputFiles = outputFiles.filter { it.exists() }
                if (existingOutputFiles.isNotEmpty()) {
                    throw UsageError("None of the output files $existingOutputFiles must exist yet.", statusCode = 2)
                }
            }
        }

        val script = when (rules) {
            is FileType -> (rules as FileType).file.readText()

            is StringType -> {
                val rulesResource = (rules as StringType).string
                javaClass.classLoader.getResource(rulesResource)?.readText()
                    ?: throw UsageError("Invalid rules resource '$rulesResource'.")
            }

            null -> {
                val rulesFile = ortConfigDirectory.resolve("rules.kts")

                if (!rulesFile.isFile) {
                    throw UsageError("No rule option specified and no default rule found at '$rulesFile'.")
                }

                rulesFile.readText()
            }
        }

        if (checkSyntax) {
            if (Evaluator().checkSyntax(script)) {
                return
            } else {
                println("Syntax check failed.")
                throw ProgramResult(2)
            }
        }

        var (ortResultInput, readDuration) = measureTimedValue { ortFile?.readValue<OrtResult>() }

        ortFile?.let { file ->
            log.perf {
                "Read ORT result from '${file.name}' (${file.formatSizeInMib}) in ${readDuration.inMilliseconds}ms."
            }
        }

        repositoryConfigurationFile?.let {
            ortResultInput = ortResultInput?.replaceConfig(it.readValue())
        }

        packageCurationsFile?.let {
            ortResultInput = ortResultInput?.replacePackageCurations(it.readValue())
        }

        val finalOrtResult = requireNotNull(ortResultInput) {
            "The '--ort-file' option is required unless the '--check-syntax' option is used."
        }

        val packageConfigurationProvider = packageConfigurationOption.createProvider()
        val copyrightGarbage = copyrightGarbageFile.takeIf { it.isFile }?.readValue<CopyrightGarbage>().orEmpty()

        val licenseInfoResolver = LicenseInfoResolver(
            provider = DefaultLicenseInfoProvider(finalOrtResult, packageConfigurationProvider),
            copyrightGarbage = copyrightGarbage,
            archiver = globalOptionsForSubcommands.config.scanner.archive.createFileArchiver(),
            licenseFilenamePatterns = LicenseFilenamePatterns.getInstance()
        )

        val licenseClassifications =
            licenseClassificationsFile.takeIf { it.isFile }?.readValue<LicenseClassifications>().orEmpty()
        val evaluator = Evaluator(finalOrtResult, licenseInfoResolver, licenseClassifications)

        val (evaluatorRun, duration) = measureTimedValue { evaluator.run(script) }

        log.perf { "Executed the evaluator in ${duration.inMilliseconds}ms." }

        if (log.delegate.isErrorEnabled) {
            evaluatorRun.violations.forEach { violation ->
                log.error(violation.toString())
            }
        }

        outputDir?.let { absoluteOutputDir ->
            // Note: This overwrites any existing EvaluatorRun from the input file.
            val ortResultOutput = finalOrtResult.copy(evaluator = evaluatorRun).mergeLabels(labels)

            absoluteOutputDir.safeMkdirs()

            outputFiles.forEach { file ->
                println("Writing evaluation result to '$file'.")
                val writeDuration = measureTime {
                    file.mapper().writerWithDefaultPrettyPrinter().writeValue(file, ortResultOutput)
                }

                log.perf {
                    "Wrote ORT result to '${file.name}' (${file.formatSizeInMib}) in ${writeDuration.inMilliseconds}ms."
                }
            }
        }

        val counts = evaluatorRun.violations.groupingBy { it.severity }.eachCount()

        val errorCount = counts[Severity.ERROR] ?: 0
        val warningCount = counts[Severity.WARNING] ?: 0
        val hintCount = counts[Severity.HINT] ?: 0

        if (errorCount > 0 || warningCount > 0) {
            println("Found $errorCount errors, $warningCount warnings, $hintCount hints.")
            throw ProgramResult(2)
        }

        println("Found $hintCount hints only.")
    }
}
