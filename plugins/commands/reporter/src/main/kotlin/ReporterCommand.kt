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

package org.ossreviewtoolkit.plugins.commands.reporter

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.options.splitPair
import com.github.ajalt.clikt.parameters.types.file

import kotlin.time.measureTimedValue

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.LicenseFilePatterns
import org.ossreviewtoolkit.model.config.PluginConfiguration
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
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.plugins.commands.api.OrtCommand
import org.ossreviewtoolkit.plugins.commands.api.utils.configurationGroup
import org.ossreviewtoolkit.plugins.commands.api.utils.inputGroup
import org.ossreviewtoolkit.plugins.commands.api.utils.outputGroup
import org.ossreviewtoolkit.plugins.commands.api.utils.readOrtResult
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.CompositePackageConfigurationProvider
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.SimplePackageConfigurationProvider
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.dir.DirPackageConfigurationProvider
import org.ossreviewtoolkit.reporter.DefaultLicenseTextProvider
import org.ossreviewtoolkit.reporter.HowToFixTextProvider
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.config.setPackageConfigurations
import org.ossreviewtoolkit.utils.config.setResolutions
import org.ossreviewtoolkit.utils.ort.ORT_COPYRIGHT_GARBAGE_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_CUSTOM_LICENSE_TEXTS_DIRNAME
import org.ossreviewtoolkit.utils.ort.ORT_HOW_TO_FIX_TEXT_PROVIDER_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_LICENSE_CLASSIFICATIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory
import org.ossreviewtoolkit.utils.ort.showStackTrace
import org.ossreviewtoolkit.utils.spdx.SpdxConstants.LICENSE_REF_PREFIX

class ReporterCommand : OrtCommand(
    name = "report",
    help = "Present Analyzer, Scanner and Evaluator results in various formats."
) {
    private val ortFile by option(
        "--ort-file", "-i",
        help = "The ORT result file to use."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()
        .inputGroup()

    private val outputDir by option(
        "--output-dir", "-o",
        help = "The output directory to store the generated reports in."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()
        .outputGroup()

    private val reportFormats by option(
        "--report-formats", "-f",
        help = "A comma-separated list of report formats to generate, any of ${ReporterFactory.ALL.keys}."
    ).convert { name ->
        ReporterFactory.ALL[name]
            ?: throw BadParameterValue("Report formats must be one or more of ${ReporterFactory.ALL.keys}.")
    }.split(",").required().outputGroup()

    private val copyrightGarbageFile by option(
        "--copyright-garbage-file",
        help = "A file containing copyright statements which are marked as garbage. This can make the output " +
            "inconsistent with the evaluator output but is useful when testing copyright garbage."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .default(ortConfigDirectory.resolve(ORT_COPYRIGHT_GARBAGE_FILENAME))
        .configurationGroup()

    private val customLicenseTextsDir by option(
        "--custom-license-texts-dir",
        help = "A directory which maps custom license IDs to license texts. It should contain one text file per " +
            "license with the license ID as the filename. A custom license text is used only if its ID has a " +
            "'$LICENSE_REF_PREFIX' prefix and if the respective license text is not known by ORT."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .default(ortConfigDirectory.resolve(ORT_CUSTOM_LICENSE_TEXTS_DIRNAME))
        .configurationGroup()

    private val howToFixTextProviderScript by option(
        "--how-to-fix-text-provider-script",
        help = "The path to a Kotlin script which returns an instance of a 'HowToFixTextProvider'. That provider " +
            "injects how-to-fix texts in Markdown format for ORT issues."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .default(ortConfigDirectory.resolve(ORT_HOW_TO_FIX_TEXT_PROVIDER_FILENAME))
        .configurationGroup()

    private val licenseClassificationsFile by option(
        "--license-classifications-file",
        help = "A file containing the license classifications. This can make the output inconsistent with the " +
            "evaluator output but is useful when testing license classifications."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .default(ortConfigDirectory.resolve(ORT_LICENSE_CLASSIFICATIONS_FILENAME))
        .configurationGroup()

    private val packageConfigurationsDir by option(
        "--package-configurations-dir",
        help = "A directory that is searched recursively for package configuration files. Each file must only " +
            "contain a single package configuration. This can make the output inconsistent with the evaluator " +
            "output but is useful when testing package configurations."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .configurationGroup()

    private val refreshResolutions by option(
        "--refresh-resolutions",
        help = "Use the resolutions from the global and repository configuration instead of the resolved " +
            "configuration. This can make the output inconsistent with the evaluator output but is useful when " +
            "testing resolutions."
    ).flag().configurationGroup()

    private val repositoryConfigurationFile by option(
        "--repository-configuration-file",
        help = "A file containing the repository configuration. If set, overrides the repository configuration " +
            "contained in the ORT result input file. This can make the output inconsistent with the output of " +
            "previous commands but is useful when testing changes in the repository configuration."
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

    private val reportOptions by option(
        "--report-option", "-O",
        help = "Specify a report-format-specific option. The key is the (case-insensitive) name of the report " +
            "format, and the value is an arbitrary key-value pair. For example: " +
            "-O PlainTextTemplate=template.id=NOTICE_SUMMARY"
    ).splitPair().convert { (format, option) ->
        require(format in ReporterFactory.ALL.keys) {
            "Report formats must be one or more of ${ReporterFactory.ALL.keys}."
        }

        format to Pair(option.substringBefore("="), option.substringAfter("=", ""))
    }.multiple()

    override fun run() {
        var ortResult = readOrtResult(ortFile)

        repositoryConfigurationFile?.let {
            val config = it.readValueOrDefault(RepositoryConfiguration())
            ortResult = ortResult.replaceConfig(config)
        }

        if (refreshResolutions || ortResult.resolvedConfiguration.resolutions == null) {
            val resolutionProvider = DefaultResolutionProvider.create(ortResult, resolutionsFile)
            ortResult = ortResult.setResolutions(resolutionProvider)
        }

        val licenseTextDirectories = listOfNotNull(customLicenseTextsDir.takeIf { it.isDirectory })

        val resolvedPackageConfigurations = ortResult.resolvedConfiguration.packageConfigurations
        val packageConfigurationProvider = when {
            resolvedPackageConfigurations != null && packageConfigurationsDir == null -> {
                SimplePackageConfigurationProvider(configurations = resolvedPackageConfigurations)
            }

            ortConfig.enableRepositoryPackageConfigurations -> {
                CompositePackageConfigurationProvider(
                    SimplePackageConfigurationProvider(
                        configurations = ortResult.repository.config.packageConfigurations
                    ),
                    DirPackageConfigurationProvider(packageConfigurationsDir)
                )
            }

            else -> {
                if (ortResult.repository.config.packageConfigurations.isNotEmpty()) {
                    logger.info { "Local package configurations were not applied because the feature is not enabled." }
                }

                DirPackageConfigurationProvider(packageConfigurationsDir)
            }
        }

        ortResult = ortResult.setPackageConfigurations(packageConfigurationProvider)

        val copyrightGarbage = copyrightGarbageFile.takeIf { it.isFile }?.readValue<CopyrightGarbage>().orEmpty()

        val licenseInfoResolver = LicenseInfoResolver(
            provider = DefaultLicenseInfoProvider(ortResult),
            copyrightGarbage = copyrightGarbage,
            addAuthorsToCopyrights = ortConfig.addAuthorsToCopyrights,
            archiver = ortConfig.scanner.archive.createFileArchiver(),
            licenseFilePatterns = LicenseFilePatterns.getInstance()
        )

        val licenseClassifications =
            licenseClassificationsFile.takeIf { it.isFile }?.readValue<LicenseClassifications>().orEmpty()

        val howToFixTextProvider = howToFixTextProviderScript.takeIf { it.isFile }?.let {
            HowToFixTextProvider.fromKotlinScript(it.readText(), ortResult)
        } ?: HowToFixTextProvider.NONE

        outputDir.safeMkdirs()

        val input = ReporterInput(
            ortResult,
            ortConfig,
            DefaultLicenseTextProvider(licenseTextDirectories),
            copyrightGarbage,
            licenseInfoResolver,
            licenseClassifications,
            howToFixTextProvider
        )

        val reportConfigMap = sortedMapOf<String, PluginConfiguration>(String.CASE_INSENSITIVE_ORDER)

        // Obtain reporter-specific options defined in ORT's configuration.
        ortConfig.reporter.config?.forEach { (reporterName, config) ->
            reportConfigMap[reporterName] = config
        }

        // Allow overwriting reporter-specific options via the command line.
        reportOptions.forEach { (reporterName, option) ->
            val reportSpecificConfig = reportConfigMap.getOrPut(reporterName) { PluginConfiguration.EMPTY }
            val updatedConfig = reportSpecificConfig.copy(options = reportSpecificConfig.options + option)
            reportConfigMap[reporterName] = updatedConfig
        }

        val reportDurationMap = measureTimedValue {
            @Suppress("ForbiddenMethodCall")
            runBlocking(Dispatchers.Default) {
                reportFormats.map { reporter ->
                    async {
                        val threadName = Thread.currentThread().name
                        echo("Generating the '${reporter.descriptor.id}' report in thread '$threadName'...")

                        reporter to measureTimedValue {
                            val options = reportConfigMap[reporter.descriptor.id] ?: PluginConfiguration.EMPTY
                            reporter.create(PluginConfig(options.options, options.secrets))
                                .generateReport(input, outputDir)
                        }
                    }
                }.awaitAll()
            }
        }

        var failureCount = 0

        reportDurationMap.value.forEach { (reporter, timedValue) ->
            val name = reporter.descriptor.id
            val fileResults = timedValue.value

            fileResults.forEach { fileResult ->
                fileResult.onSuccess { file ->
                    echo("Successfully created '$name' report at $file in ${timedValue.duration}.")
                }.onFailure { e ->
                    e.showStackTrace()

                    logger.error {
                        "Could not create '$name' report in ${timedValue.duration}: ${e.collectMessages()}"
                    }

                    ++failureCount
                }
            }
        }

        val successCount = reportFormats.size - failureCount
        echo("Created $successCount of ${reportFormats.size} report(s) in ${reportDurationMap.duration}.")

        if (failureCount > 0) throw ProgramResult(2)
    }
}
