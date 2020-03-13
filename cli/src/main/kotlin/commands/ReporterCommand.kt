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

package com.here.ort.commands

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.file

import com.here.ort.model.OrtResult
import com.here.ort.model.config.CopyrightGarbage
import com.here.ort.model.config.OrtConfiguration
import com.here.ort.model.config.Resolutions
import com.here.ort.model.config.orEmpty
import com.here.ort.model.licenses.LicenseConfiguration
import com.here.ort.model.licenses.orEmpty
import com.here.ort.model.readValue
import com.here.ort.model.utils.SimplePackageConfigurationProvider
import com.here.ort.reporter.DefaultLicenseTextProvider
import com.here.ort.reporter.DefaultResolutionProvider
import com.here.ort.reporter.Reporter
import com.here.ort.reporter.ReporterInput
import com.here.ort.utils.collectMessagesAsString
import com.here.ort.utils.expandTilde
import com.here.ort.utils.safeMkdirs
import com.here.ort.utils.showStackTrace

import java.io.File

class ReporterCommand : CliktCommand(
    name = "report",
    help = "Present Analyzer and Scanner results in various formats."
) {
    private val ortFile by option(
        "--ort-file", "-i",
        help = "The ORT result file to use."
    ).file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .required()

    private val packageConfigurationDir by option(
        "--package-configuration-dir",
        help = "The directory containing the package configuration files to read as input. It is searched recursively."
    ).file(mustExist = true, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = true)

    private val outputDir by option(
        "--output-dir", "-o",
        help = "The output directory to store the generated reports in."
    ).file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)
        .required()

    private val reporters by option(
        "--report-formats", "-f",
        help = "The list of report formats that will be generated."
    ).convert { reporterName ->
        // Map upper-cased reporter names to their instances.
        val reporters = Reporter.ALL.associateBy { it.reporterName.toUpperCase() }
        reporters[reporterName.toUpperCase()]
            ?: throw BadParameterValue("Reporters must be contained in ${reporters.keys}.")
    }.split(",").required()

    private val resolutionsFile by option(
        "--resolutions-file",
        help = "A file containing error resolutions."
    ).file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)

    private val preProcessingScript by option(
        "--pre-processing-script",
        help = "The path to a Kotlin script to pre-process the notice report before writing it to disk."
    ).file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)

    private val copyrightGarbageFile by option(
        "--copyright-garbage-file",
        help = "A file containing garbage copyright statements entries which are to be ignored."
    ).file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)

    private val repositoryConfigurationFile by option(
        "--repository-configuration-file",
        help = "A file containing the repository configuration. If set the .ort.yml overrides the repository " +
                "configuration contained in the ort result from the input file."
    ).file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)

    private val customLicenseTextsDir by option(
        "--custom-license-texts-dir",
        help = "A directory which maps custom license IDs to license texts. It should contain one text file per " +
                "license with the license ID as the filename. A custom license text is used only if its ID has a " +
                "'LicenseRef-' prefix and if the respective license text is not known by ORT."
    ).file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)

    private val licenseConfigurationFile by option(
        "--license-configuration-file",
        help = "A file containing the license configuration."
    ).file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)

    private val config by requireObject<OrtConfiguration>()

    override fun run() {
        val absoluteOutputDir = outputDir.expandTilde().normalize()

        val reports = reporters.associateWith { reporter ->
            File(absoluteOutputDir, reporter.defaultFilename)
        }

        val existingReportFiles = reports.values.filter { it.exists() }
        if (existingReportFiles.isNotEmpty()) {
            throw UsageError("None of the report files $existingReportFiles must exist yet.", statusCode = 2)
        }

        var ortResult = ortFile.expandTilde().readValue<OrtResult>()
        repositoryConfigurationFile?.expandTilde()?.let {
            ortResult = ortResult.replaceConfig(it.readValue())
        }

        val resolutionProvider = DefaultResolutionProvider()
        resolutionProvider.add(ortResult.getResolutions())
        resolutionsFile?.expandTilde()?.readValue<Resolutions>()?.let { resolutionProvider.add(it) }

        val copyrightGarbage = copyrightGarbageFile?.expandTilde()?.readValue<CopyrightGarbage>().orEmpty()

        val packageConfigurationProvider =
            packageConfigurationDir?.let { SimplePackageConfigurationProvider.forDirectory(it) }
                ?: SimplePackageConfigurationProvider()

        val licenseConfiguration = licenseConfigurationFile?.expandTilde()?.readValue<LicenseConfiguration>().orEmpty()

        absoluteOutputDir.safeMkdirs()

        val input = ReporterInput(
            ortResult,
            config,
            packageConfigurationProvider,
            resolutionProvider,
            DefaultLicenseTextProvider(customLicenseTextsDir),
            copyrightGarbage,
            licenseConfiguration,
            preProcessingScript?.expandTilde()?.readText()
        )

        reports.forEach { (reporter, file) ->
            @Suppress("TooGenericExceptionCaught")
            try {
                reporter.generateReport(file.outputStream(), input)

                println("Created '${reporter.reporterName}' report:\n\t$file")
            } catch (e: Exception) {
                e.showStackTrace()

                // The "file.outputStream()" above already creates the file, so delete it here if the exception occurred
                // before any content was written.
                if (file.length() == 0L) file.delete()

                throw UsageError("Could not create '${reporter.reporterName}' report: ${e.collectMessagesAsString()}",
                    statusCode = 1)
            }
        }
    }
}
