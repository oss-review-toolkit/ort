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

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.options.splitPair
import com.github.ajalt.clikt.parameters.types.file

import java.io.File

import kotlin.time.TimedValue
import kotlin.time.measureTimedValue

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.config.orEmpty
import org.ossreviewtoolkit.model.licenses.LicenseConfiguration
import org.ossreviewtoolkit.model.licenses.orEmpty
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.reporter.DefaultLicenseTextProvider
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.PackageConfigurationOption
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.createProvider
import org.ossreviewtoolkit.utils.expandTilde
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.safeMkdirs
import org.ossreviewtoolkit.utils.showStackTrace

class ReporterCommand : CliktCommand(
    name = "report",
    help = "Present Analyzer and Scanner results in various formats."
) {
    private val allReportersByName = Reporter.ALL.associateBy { it.reporterName.toUpperCase() }

    private val ortFile by option(
        "--ort-file", "-i",
        help = "The ORT result file to use."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .required()

    private val packageConfigurationOption by mutuallyExclusiveOptions<PackageConfigurationOption>(
        option(
            "--package-configuration-dir",
            help = "The directory containing the package configuration files to read as input. It is searched " +
                    "recursively."
        ).convert { it.expandTilde() }
            .file(mustExist = true, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = true)
            .convert { PackageConfigurationOption.Dir(it) },
        option(
            "--package-configuration-file",
            help = "The file containing the package configurations to read as input."
        ).convert { it.expandTilde() }
            .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
            .convert { PackageConfigurationOption.File(it) }
    ).single()

    private val outputDir by option(
        "--output-dir", "-o",
        help = "The output directory to store the generated reports in."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)
        .required()

    private val reportFormats by option(
        "--report-formats", "-f",
        help = "The list of report formats that will be generated."
    ).convert { name ->
        allReportersByName[name.toUpperCase()]
            ?: throw BadParameterValue("Report formats must be one or more of ${allReportersByName.keys}.")
    }.split(",").required()

    private val reportOptions by option(
        "--report-option", "-O",
        help = "Specify a report-format-specific option. The key is the (case-insensitive) name of the report " +
                "format, and the value is an arbitrary key-value pair. For example: " +
                "-O AntennaAttributionDocument=template.id=basic-pdf-template"
    ).splitPair().convert { (format, option) ->
        val upperCaseFormat = format.toUpperCase()

        require(upperCaseFormat in allReportersByName.keys) {
            "Report formats must be one or more of ${allReportersByName.keys}."
        }

        upperCaseFormat to Pair(option.substringBefore("="), option.substringAfter("=", ""))
    }.multiple()

    private val resolutionsFile by option(
        "--resolutions-file",
        help = "A file containing error resolutions."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)

    private val preProcessingScript by option(
        "--pre-processing-script",
        help = "The path to a Kotlin script to pre-process the notice report before writing it to disk."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)

    private val copyrightGarbageFile by option(
        "--copyright-garbage-file",
        help = "A file containing garbage copyright statements entries which are to be ignored."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)

    private val repositoryConfigurationFile by option(
        "--repository-configuration-file",
        help = "A file containing the repository configuration. If set the .ort.yml overrides the repository " +
                "configuration contained in the ort result from the input file."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)

    private val customLicenseTextsDir by option(
        "--custom-license-texts-dir",
        help = "A directory which maps custom license IDs to license texts. It should contain one text file per " +
                "license with the license ID as the filename. A custom license text is used only if its ID has a " +
                "'LicenseRef-' prefix and if the respective license text is not known by ORT."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)

    private val licenseConfigurationFile by option(
        "--license-configuration-file",
        help = "A file containing the license configuration."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)

    private val config by requireObject<OrtConfiguration>()

    override fun run() {
        val absoluteOutputDir = outputDir.normalize()

        var ortResult = ortFile.readValue<OrtResult>()
        repositoryConfigurationFile?.let {
            ortResult = ortResult.replaceConfig(it.readValue())
        }

        val resolutionProvider = DefaultResolutionProvider()
        resolutionProvider.add(ortResult.getResolutions())
        resolutionsFile?.readValue<Resolutions>()?.let { resolutionProvider.add(it) }

        val copyrightGarbage = copyrightGarbageFile?.readValue<CopyrightGarbage>().orEmpty()

        val packageConfigurationProvider = packageConfigurationOption.createProvider()

        val licenseConfiguration = licenseConfigurationFile?.readValue<LicenseConfiguration>().orEmpty()

        absoluteOutputDir.safeMkdirs()

        val input = ReporterInput(
            ortResult,
            config,
            packageConfigurationProvider,
            resolutionProvider,
            DefaultLicenseTextProvider(customLicenseTextsDir),
            copyrightGarbage,
            licenseConfiguration,
            preProcessingScript?.readText()
        )

        val reportOptionsMap = mutableMapOf<String, MutableMap<String, String>>()

        reportOptions.forEach { (format, option) ->
            val reportSpecificOptionsMap = reportOptionsMap.getOrPut(format) { mutableMapOf() }
            reportSpecificOptionsMap[option.first] = option.second
        }

        var failure = false
        val reportDurationMap = mutableMapOf<Reporter, TimedValue<List<File>>>()

        reportFormats.forEach { reporter ->
            val options = reportOptionsMap[reporter.reporterName.toUpperCase()].orEmpty()

            println("Creating the '${reporter.reporterName}' report...")

            @Suppress("TooGenericExceptionCaught")
            try {
                reportDurationMap[reporter] = measureTimedValue {
                    reporter.generateReport(input, absoluteOutputDir, options)
                }
            } catch (e: Exception) {
                e.showStackTrace()

                log.error { "Could not create '${reporter.reporterName}' report: ${e.collectMessagesAsString()}" }

                failure = true
            }
        }

        reportDurationMap.forEach { (reporter, files) ->
            val name = reporter.reporterName
            println("Successfully created the '$name' report at ${files.value} in ${files.duration.inSeconds}s.")
        }

        println("Created ${reportDurationMap.size} of ${reportFormats.size} report(s).")

        if (failure) {
            if (reportDurationMap.isEmpty()) {
                println("Failed to create any report.")
            } else {
                println("At least one report was not created successfully.")
            }

            throw ProgramResult(2)
        }
    }
}
