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
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.config.orEmpty
import org.ossreviewtoolkit.model.licenses.LicenseConfiguration
import org.ossreviewtoolkit.model.licenses.orEmpty
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.reporter.DefaultLicenseTextProvider
import org.ossreviewtoolkit.reporter.DefaultResolutionProvider
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.PackageConfigurationOption
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.createProvider
import org.ossreviewtoolkit.utils.expandTilde
import org.ossreviewtoolkit.utils.safeMkdirs
import org.ossreviewtoolkit.utils.showStackTrace

import java.io.File

class ReporterCommand : CliktCommand(
    name = "report",
    help = "Present Analyzer and Scanner results in various formats."
) {
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

        val reports = reporters.associateWith { reporter ->
            File(absoluteOutputDir, reporter.defaultFilename)
        }

        val existingReportFiles = reports.values.filter { it.exists() }
        if (existingReportFiles.isNotEmpty()) {
            throw UsageError("None of the report files $existingReportFiles must exist yet.", statusCode = 2)
        }

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

                throw UsageError(
                    "Could not create '${reporter.reporterName}' report: ${e.collectMessagesAsString()}",
                    statusCode = 1
                )
            }
        }
    }
}
