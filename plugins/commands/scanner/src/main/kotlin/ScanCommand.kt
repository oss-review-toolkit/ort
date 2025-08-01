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

package org.ossreviewtoolkit.plugins.commands.scanner

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.RawOption
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

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.downloader.DefaultWorkingTreeCache
import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.model.utils.mergeLabels
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.api.orEmpty
import org.ossreviewtoolkit.plugins.commands.api.OrtCommand
import org.ossreviewtoolkit.plugins.commands.api.OrtCommandFactory
import org.ossreviewtoolkit.plugins.commands.api.utils.SeverityStatsPrinter
import org.ossreviewtoolkit.plugins.commands.api.utils.configurationGroup
import org.ossreviewtoolkit.plugins.commands.api.utils.outputGroup
import org.ossreviewtoolkit.plugins.commands.api.utils.readOrtResult
import org.ossreviewtoolkit.plugins.commands.api.utils.writeOrtResult
import org.ossreviewtoolkit.scanner.ScanStorages
import org.ossreviewtoolkit.scanner.Scanner
import org.ossreviewtoolkit.scanner.ScannerWrapperFactory
import org.ossreviewtoolkit.scanner.provenance.DefaultNestedProvenanceResolver
import org.ossreviewtoolkit.scanner.provenance.DefaultPackageProvenanceResolver
import org.ossreviewtoolkit.scanner.provenance.DefaultProvenanceDownloader
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.ort.ORT_FAILURE_STATUS_CODE
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory

@OrtPlugin(
    displayName = "Scan",
    description = "Run external license / copyright scanners.",
    factory = OrtCommandFactory::class
)
class ScanCommand(descriptor: PluginDescriptor = ScanCommandFactory.descriptor) : OrtCommand(descriptor) {
    private val input by option(
        "--ort-file", "-i",
        help = "An ORT result file with an analyzer result to use. Source code is downloaded automatically if needed."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val outputDir by option(
        "--output-dir", "-o",
        help = "The directory to write the ORT result file with scan results to."
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

    private val scanners by option(
        "--scanners", "-s",
        help = "A comma-separated list of scanners to use.\nPossible values are: ${ScannerWrapperFactory.ALL.keys}"
    ).convertToScannerWrapperFactories()
        .default(listOfNotNull(ScannerWrapperFactory.ALL.let { it.values.singleOrNull() ?: it["ScanCode"] }))

    private val projectScanners by option(
        "--project-scanners",
        help = "A comma-separated list of scanners to use for scanning the source code of projects. By default, " +
            "projects and packages are scanned with the same scanners as specified by '--scanners'.\n" +
            "Possible values are: ${ScannerWrapperFactory.ALL.keys}"
    ).convertToScannerWrapperFactories()

    private val packageTypes by option(
        "--package-types",
        help = "A comma-separated list of the package types from the ORT file's analyzer result to limit scans to."
    ).enum<PackageType>().split(",").default(PackageType.entries)

    private val skipExcluded by option(
        "--skip-excluded",
        help = "Do not scan excluded projects or packages. Works only with the '--ort-file' parameter."
    ).flag().deprecated("Use the global option 'ort -P ort.scanner.skipExcluded=... scan' instead.")

    private val resolutionsFile by option(
        "--resolutions-file",
        help = "A file containing issue and rule violation resolutions."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .default(ortConfigDirectory / ORT_RESOLUTIONS_FILENAME)
        .configurationGroup()

    override fun run() {
        val outputFiles = outputFormats.mapTo(mutableSetOf()) { format ->
            outputDir / "scan-result.${format.fileExtension}"
        }

        validateOutputFiles(outputFiles)

        val ortResult = runCatching {
            runScanners(scanners, projectScanners ?: scanners, ortConfig).mergeLabels(labels)
        }.onFailure {
            echo(Theme.Default.danger(it.collectMessages()))
        }.getOrNull()

        val scannerRun = ortResult?.scanner
        if (scannerRun == null) {
            echo(Theme.Default.danger("No scanner run was created."))
            throw ProgramResult(1)
        }

        outputDir.safeMkdirs()
        writeOrtResult(ortResult, outputFiles, terminal)

        val duration = with(scannerRun) { Duration.between(startTime, endTime).toKotlinDuration() }
        echo("The scan took $duration.")

        val resolutionProvider = DefaultResolutionProvider.create(ortResult, resolutionsFile)
        val issues = scannerRun.getAllIssues().flatMap { it.value }
        SeverityStatsPrinter(terminal, resolutionProvider).stats(issues)
            .print().conclude(ortConfig.severeIssueThreshold, ORT_FAILURE_STATUS_CODE)
    }

    @Suppress("ForbiddenMethodCall")
    private fun runScanners(
        scannerWrapperFactories: List<ScannerWrapperFactory>,
        projectScannerWrapperFactories: List<ScannerWrapperFactory>,
        ortConfig: OrtConfiguration
    ): OrtResult {
        val packageScannerWrappers = scannerWrapperFactories
            .takeIf { PackageType.PACKAGE in packageTypes }.orEmpty()
            .map {
                val config = ortConfig.scanner.scanners?.get(it.descriptor.id)
                it.create(config.orEmpty())
            }

        val projectScannerWrappers = projectScannerWrapperFactories
            .takeIf { PackageType.PROJECT in packageTypes }.orEmpty()
            .map {
                val config = ortConfig.scanner.scanners?.get(it.descriptor.id)
                it.create(PluginConfig(config?.options.orEmpty(), config?.secrets.orEmpty()))
            }

        if (projectScannerWrappers.isNotEmpty()) {
            echo("Scanning projects with:")
            projectScannerWrappers.forEach { echo("\t${it.descriptor.displayName} (version ${it.version})") }
        } else {
            echo("Projects will not be scanned.")
        }

        if (packageScannerWrappers.isNotEmpty()) {
            echo("Scanning packages with:")
            packageScannerWrappers.forEach { echo("\t${it.descriptor.displayName} (version ${it.version})") }
        } else {
            echo("Packages will not be scanned.")
        }

        val scanStorages = ScanStorages.createFromConfig(ortConfig.scanner)
        val workingTreeCache = DefaultWorkingTreeCache()

        with(scanStorages) {
            logger.info {
                val storages = listOf(packageProvenanceStorage, nestedProvenanceStorage).map { it.javaClass.simpleName }
                "Using the following provenance storages: $storages"
            }

            logger.info {
                "Using the following scan storages for reading results: " + readers.map { it.javaClass.simpleName }
            }

            logger.info {
                "Using the following scan storages for writing results: " + writers.map { it.javaClass.simpleName }
            }
        }

        try {
            val scanner = Scanner(
                scannerConfig = ortConfig.scanner,
                downloaderConfig = ortConfig.downloader,
                provenanceDownloader = DefaultProvenanceDownloader(ortConfig.downloader, workingTreeCache),
                storageReaders = scanStorages.readers,
                storageWriters = scanStorages.writers,
                packageProvenanceResolver = DefaultPackageProvenanceResolver(
                    scanStorages.packageProvenanceStorage,
                    workingTreeCache
                ),
                nestedProvenanceResolver = DefaultNestedProvenanceResolver(
                    scanStorages.nestedProvenanceStorage,
                    workingTreeCache
                ),
                scannerWrappers = mapOf(
                    PackageType.PACKAGE to packageScannerWrappers,
                    PackageType.PROJECT to projectScannerWrappers
                )
            )

            val ortResult = readOrtResult(input)
            return runBlocking {
                scanner.scan(ortResult, skipExcluded || ortConfig.scanner.skipExcluded, labels)
            }
        } finally {
            runBlocking { workingTreeCache.shutdown() }
        }
    }
}

private fun RawOption.convertToScannerWrapperFactories() =
    convert { scannerNames ->
        scannerNames.split(',').map { name ->
            ScannerWrapperFactory.ALL[name]
                ?: throw BadParameterValue("Scanner '$name' is not one of ${ScannerWrapperFactory.ALL.keys}.")
        }
    }
