/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

import org.ossreviewtoolkit.GlobalOptions
import org.ossreviewtoolkit.advisor.Advisor
import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.mapper
import org.ossreviewtoolkit.model.utils.mergeLabels
import org.ossreviewtoolkit.utils.expandTilde
import org.ossreviewtoolkit.utils.safeMkdirs

class AdvisorCommand : CliktCommand(name = "advise", help = "Check dependencies for security vulnerabilities.") {
    private val allVulnerabilityProvidersByName = Advisor.ALL.associateBy { it.providerName }
        .toSortedMap(String.CASE_INSENSITIVE_ORDER)

    private val ortFile by option(
        "--ort-file", "-i",
        help = "An ORT result file with an analyzer result to use."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val outputDir by option(
        "--output-dir", "-o",
        help = "The directory to write the advisor results as ORT result file(s) to."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()
        .outputGroup()

    private val outputFormats by option(
        "--output-formats", "-f",
        help = "The list of output formats to be used for the ORT result file(s)."
    ).enum<FileFormat>().split(",").default(listOf(FileFormat.YAML)).outputGroup()

    private val labels by option(
        "--label", "-l",
        help = "Add a label to the ORT result. Can be used multiple times. If an ORT result is used as input for the" +
                "advisor, any existing label with the same key is overwritten. For example: " +
                "--label distribution=external"
    ).associate()

    private val globalOptionsForSubcommands by requireObject<GlobalOptions>()

    private val providerFactories by option(
        "--advisors", "-a",
        help = "The comma-separated advisors to use, any of ${allVulnerabilityProvidersByName.keys}."
    ).convert { name ->
        allVulnerabilityProvidersByName[name]
            ?: throw BadParameterValue(
                "Advisor '$name' is not one of ${allVulnerabilityProvidersByName.keys}."
            )
    }.split(",").required()

    private val skipExcluded by option(
        "--skip-excluded",
        help = "Do not check excluded projects or packages."
    ).flag()

    override fun run() {
        val outputFiles = outputFormats.mapTo(mutableSetOf()) { format ->
            outputDir.resolve("advisor-result.${format.fileExtension}")
        }

        if (!globalOptionsForSubcommands.forceOverwrite) {
            val existingOutputFiles = outputFiles.filter { it.exists() }
            if (existingOutputFiles.isNotEmpty()) {
                throw UsageError("None of the output files $existingOutputFiles must exist yet.", statusCode = 2)
            }
        }

        val distinctProviders = providerFactories.distinct()
        println("The following advisors are activated:")
        println("\t" + distinctProviders.joinToString())

        val advisor = Advisor(distinctProviders, globalOptionsForSubcommands.config.advisor)

        val ortResult = advisor.retrieveVulnerabilityInformation(ortFile, skipExcluded).mergeLabels(labels)

        outputDir.safeMkdirs()

        outputFiles.forEach { file ->
            println("Writing advisor result to '$file'.")
            file.mapper().writerWithDefaultPrettyPrinter().writeValue(file, ortResult)
        }

        val advisorResults = ortResult.advisor?.results

        if (advisorResults == null) {
            println("There was an error creating the advisor results.")
            throw ProgramResult(1)
        }

        if (advisorResults.hasIssues) {
            println("The advisor result contains issues.")
            throw ProgramResult(2)
        }
    }
}
