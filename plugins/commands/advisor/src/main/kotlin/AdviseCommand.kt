/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.commands.advisor

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.terminal
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
import com.github.ajalt.mordant.rendering.Theme

import java.time.Duration

import kotlin.time.toKotlinDuration

import kotlinx.coroutines.runBlocking

import org.ossreviewtoolkit.advisor.AdviceProviderFactory
import org.ossreviewtoolkit.advisor.Advisor
import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.model.utils.mergeLabels
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.commands.api.OrtCommand
import org.ossreviewtoolkit.plugins.commands.api.OrtCommandFactory
import org.ossreviewtoolkit.plugins.commands.api.utils.SeverityStatsPrinter
import org.ossreviewtoolkit.plugins.commands.api.utils.configurationGroup
import org.ossreviewtoolkit.plugins.commands.api.utils.outputGroup
import org.ossreviewtoolkit.plugins.commands.api.utils.readOrtResult
import org.ossreviewtoolkit.plugins.commands.api.utils.writeOrtResult
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.ort.ORT_FAILURE_STATUS_CODE
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory

@OrtPlugin(
    displayName = "Advise",
    description = "Check dependencies for security vulnerabilities.",
    factory = OrtCommandFactory::class
)
class AdviseCommand(descriptor: PluginDescriptor = AdviseCommandFactory.descriptor) : OrtCommand(descriptor) {
    private val ortFile by option(
        "--ort-file", "-i",
        help = "An ORT result file with an analyzer result to use."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val outputDir by option(
        "--output-dir", "-o",
        help = "The directory to write the ORT result file with advisor results to."
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
        help = "Set a label in the ORT result, overwriting any existing label of the same name. Can be used multiple " +
            "times. For example: --label distribution=external"
    ).associate()

    private val resolutionsFile by option(
        "--resolutions-file",
        help = "A file containing issue and rule violation resolutions."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .default(ortConfigDirectory / ORT_RESOLUTIONS_FILENAME)
        .configurationGroup()

    private val providerFactories by option(
        "--advisors", "-a",
        help = "The comma-separated advisors to use, any of ${AdviceProviderFactory.ALL.keys}."
    ).convert { name ->
        AdviceProviderFactory.ALL[name]
            ?: throw BadParameterValue("Advisor '$name' is not one of ${AdviceProviderFactory.ALL.keys}.")
    }.split(",").required()

    private val skipExcluded by option(
        "--skip-excluded",
        help = "Do not check excluded projects or packages."
    ).flag().deprecated("Use the global option 'ort -P ort.advisor.skipExcluded=... advise' instead.")

    override fun run() {
        val outputFiles = outputFormats.mapTo(mutableSetOf()) { format ->
            outputDir.resolve("advisor-result.${format.fileExtension}")
        }

        validateOutputFiles(outputFiles)

        val distinctProviders = providerFactories.distinct()
        echo("The following ${distinctProviders.size} advisor(s) are enabled:")
        echo("\t" + distinctProviders.joinToString { it.descriptor.id }.ifEmpty { "<None>" })

        val advisor = Advisor(distinctProviders, ortConfig.advisor)

        val ortResultInput = readOrtResult(ortFile)

        @Suppress("ForbiddenMethodCall")
        val ortResultOutput = runBlocking {
            advisor.advise(ortResultInput, skipExcluded || ortConfig.advisor.skipExcluded).mergeLabels(labels)
        }

        outputDir.safeMkdirs()
        writeOrtResult(ortResultOutput, outputFiles, terminal)

        val advisorRun = ortResultOutput.advisor
        if (advisorRun == null) {
            echo(Theme.Default.danger("No advisor run was created."))
            throw ProgramResult(1)
        }

        val duration = with(advisorRun) { Duration.between(startTime, endTime).toKotlinDuration() }
        echo("The advice took $duration.")

        with(advisorRun.getVulnerabilities()) {
            val includedPackages = ortResultOutput.getPackages(omitExcluded = true).map { it.metadata.id }
            val totalPackageCount = includedPackages.size
            val vulnerablePackageCount = count { (id, vulnerabilities) ->
                id in includedPackages && vulnerabilities.isNotEmpty()
            }

            val vulnerabilityCount = filterKeys { it in includedPackages }.values.sumOf { it.size }

            echo(
                "$vulnerablePackageCount of $totalPackageCount package(s) (not counting excluded ones) are " +
                    "vulnerable, with $vulnerabilityCount vulnerabilities in total."
            )
        }

        val resolutionProvider = DefaultResolutionProvider.create(ortResultOutput, resolutionsFile)
        val issues = advisorRun.getIssues().flatMap { it.value }
        SeverityStatsPrinter(terminal, resolutionProvider).stats(issues)
            .print().conclude(ortConfig.severeIssueThreshold, ORT_FAILURE_STATUS_CODE)
    }
}
