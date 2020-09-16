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
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file

import java.io.File

import org.ossreviewtoolkit.GroupTypes.FileType
import org.ossreviewtoolkit.GroupTypes.StringType
import org.ossreviewtoolkit.evaluator.Evaluator
import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.licenses.LicenseConfiguration
import org.ossreviewtoolkit.model.licenses.orEmpty
import org.ossreviewtoolkit.model.mapper
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.utils.mergeLabels
import org.ossreviewtoolkit.utils.ORT_REPO_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.PackageConfigurationOption
import org.ossreviewtoolkit.utils.createProvider
import org.ossreviewtoolkit.utils.expandTilde
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.ortConfigDirectory
import org.ossreviewtoolkit.utils.safeMkdirs

class EvaluatorCommand : CliktCommand(name = "evaluate", help = "Evaluate rules on ORT result files.") {
    private val ortFile by option(
        "--ort-file", "-i",
        help = "The ORT result file to read as input."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()
        .inputGroup()

    private val outputDir by option(
        "--output-dir", "-o",
        help = "The directory to write the evaluation results as ORT result file(s) to, in the specified output " +
                "format(s). If no output directory is specified, no output formats are written and only the exit " +
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
            help = "The name of a script file containing rules."
        ).convert { it.expandTilde() }
            .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
            .convert { FileType(it.absoluteFile.normalize()) },
        option(
            "--rules-resource",
            help = "The name of a script resource on the classpath that contains rules."
        ).convert { StringType(it) },
        name = "Rule Options"
    ).single()

    private val licenseConfigurationFile by option(
        "--license-configuration-file",
        help = "A file containing the license configuration. That license configuration is passed as parameter to " +
                "the rules script."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .configurationGroup()

    private val packageConfigurationOption by mutuallyExclusiveOptions(
        option(
            "--package-configuration-dir",
            help = "A directory that is searched recursively for package configuration files. Each file must only " +
                    "contain a single package configuration."
        ).convert { it.expandTilde() }
            .file(mustExist = true, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = true)
            .convert { PackageConfigurationOption.Dir(it.absoluteFile.normalize()) },
        option(
            "--package-configuration-file",
            help = "A file containing a list of package configurations."
        ).convert { it.expandTilde() }
            .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
            .convert { PackageConfigurationOption.File(it.absoluteFile.normalize()) },
        name = "Configuration Options"
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
                "the repository configuration contained in the ort result from the input file."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .configurationGroup()

    private val labels by option(
        "--label", "-l",
        help = "Add a label to the ORT result. Can be used multiple times. Any existing label with the same key in " +
                "the input ORT result is overwritten. For example: --label distribution=external"
    ).associate()

    private val syntaxCheck by option(
        "--syntax-check",
        help = "Do not evaluate the script but only check its syntax. No output is written in this case."
    ).flag()

    override fun run() {
        val outputFiles = mutableListOf<File>()

        outputDir?.let { absoluteOutputDir ->
            outputFiles += outputFormats.distinct().map { format ->
                absoluteOutputDir.resolve("evaluation-result.${format.fileExtension}")
            }

            val existingOutputFiles = outputFiles.filter { it.exists() }
            if (existingOutputFiles.isNotEmpty()) {
                throw UsageError("None of the output files $existingOutputFiles must exist yet.")
            }
        }

        var ortResultInput = ortFile.readValue<OrtResult>()
        repositoryConfigurationFile?.let {
            ortResultInput = ortResultInput.replaceConfig(it.readValue())
        }

        val packageConfigurationProvider = packageConfigurationOption.createProvider()

        val licenseConfiguration = licenseConfigurationFile?.readValue<LicenseConfiguration>().orEmpty()

        packageCurationsFile?.let {
            ortResultInput = ortResultInput.replacePackageCurations(it.readValue())
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

        val evaluator = Evaluator(ortResultInput, packageConfigurationProvider, licenseConfiguration)

        if (syntaxCheck) {
            if (evaluator.checkSyntax(script)) {
                return
            } else {
                println("Syntax check failed.")
                throw ProgramResult(2)
            }
        }

        val evaluatorRun by lazy { evaluator.run(script) }

        if (log.delegate.isErrorEnabled) {
            evaluatorRun.violations.forEach { violation ->
                log.error(violation.toString())
            }
        }

        printSummary(evaluatorRun.violations)

        outputDir?.let { absoluteOutputDir ->
            // Note: This overwrites any existing EvaluatorRun from the input file.
            val ortResultOutput = ortResultInput.copy(evaluator = evaluatorRun).mergeLabels(labels)

            absoluteOutputDir.safeMkdirs()

            outputFiles.forEach { file ->
                println("Writing evaluation result to '$file'.")
                file.mapper().writerWithDefaultPrettyPrinter().writeValue(file, ortResultOutput)
            }
        }

        if (evaluatorRun.violations.isNotEmpty()) {
            println("Rule violations found.")
            throw ProgramResult(2)
        }
    }

    private fun printSummary(errors: List<RuleViolation>) {
        val counts = errors.groupingBy { it.severity }.eachCount()

        val errorCount = counts[Severity.ERROR] ?: 0
        val warningCount = counts[Severity.WARNING] ?: 0
        val hintCount = counts[Severity.HINT] ?: 0

        println("Found $errorCount errors, $warningCount warnings, $hintCount hints.")
    }
}
