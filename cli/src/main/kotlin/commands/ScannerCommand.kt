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
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.RawOption
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file

import java.time.Duration

import kotlin.time.toKotlinDuration

import kotlinx.coroutines.runBlocking

import org.ossreviewtoolkit.cli.OrtCommand
import org.ossreviewtoolkit.cli.utils.OPTION_GROUP_INPUT
import org.ossreviewtoolkit.cli.utils.SeverityStats
import org.ossreviewtoolkit.cli.utils.configurationGroup
import org.ossreviewtoolkit.cli.utils.outputGroup
import org.ossreviewtoolkit.cli.utils.readOrtResult
import org.ossreviewtoolkit.cli.utils.writeOrtResult
import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.model.utils.mergeLabels
import org.ossreviewtoolkit.scanner.ScanStorages
import org.ossreviewtoolkit.scanner.Scanner
import org.ossreviewtoolkit.scanner.ScannerWrapper
import org.ossreviewtoolkit.scanner.ScannerWrapperFactory
import org.ossreviewtoolkit.scanner.provenance.DefaultNestedProvenanceResolver
import org.ossreviewtoolkit.scanner.provenance.DefaultPackageProvenanceResolver
import org.ossreviewtoolkit.scanner.provenance.DefaultProvenanceDownloader
import org.ossreviewtoolkit.scanner.scanners.scancode.ScanCode
import org.ossreviewtoolkit.scanner.utils.DefaultWorkingTreeCache
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory

class ScannerCommand : OrtCommand(
    name = "scan",
    help = "Run external license / copyright scanners."
) {
    private val input by mutuallyExclusiveOptions(
        option(
            "--ort-file", "-i",
            help = "An ORT result file with an analyzer result to use. Source code is downloaded automatically if " +
                    "needed. Must not be used together with '--input-path'."
        ).convert { it.expandTilde() }
            .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
            .convert { it.absoluteFile.normalize() },
        option(
            "--input-path", "-p",
            help = "An input directory or file to scan. Must not be used together with '--ort-file'."
        ).convert { it.expandTilde() }
            .file(mustExist = true, canBeFile = true, canBeDir = true, mustBeWritable = false, mustBeReadable = true)
            .convert { it.absoluteFile.normalize() },
        name = OPTION_GROUP_INPUT
    ).single().required()

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
        help = "A comma-separated list of scanners to use.\nPossible values are: ${ScannerWrapper.ALL.keys}"
    ).convertToScannerWrapperFactories().default(listOf(ScanCode.Factory()))

    private val projectScanners by option(
        "--project-scanners",
        help = "A comma-separated list of scanners to use for scanning the source code of projects. By default, " +
                "projects and packages are scanned with the same scanners as specified by '--scanners'.\n" +
                "Possible values are: ${ScannerWrapper.ALL.keys}"
    ).convertToScannerWrapperFactories()

    private val packageTypes by option(
        "--package-types",
        help = "A comma-separated list of the package types from the ORT file's analyzer result to limit scans to."
    ).enum<PackageType>().split(",").default(enumValues<PackageType>().asList())

    private val skipExcluded by option(
        "--skip-excluded",
        help = "Do not scan excluded projects or packages. Works only with the '--ort-file' parameter."
    ).flag()

    private val resolutionsFile by option(
        "--resolutions-file",
        help = "A file containing issue and rule violation resolutions."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .default(ortConfigDirectory.resolve(ORT_RESOLUTIONS_FILENAME))
        .configurationGroup()

    private val ortConfig by requireObject<OrtConfiguration>()

    override fun run() {
        val outputFiles = outputFormats.mapTo(mutableSetOf()) { format ->
            outputDir.resolve("scan-result.${format.fileExtension}")
        }

        if (!ortConfig.forceOverwrite) {
            val existingOutputFiles = outputFiles.filter { it.exists() }
            if (existingOutputFiles.isNotEmpty()) {
                throw UsageError("None of the output files $existingOutputFiles must exist yet.", statusCode = 2)
            }
        }

        val ortResult = runScanners(scanners, projectScanners ?: scanners, ortConfig).mergeLabels(labels)

        outputDir.safeMkdirs()
        writeOrtResult(ortResult, outputFiles, "scan")

        val scannerRun = ortResult.scanner
        if (scannerRun == null) {
            println("No scanner run was created.")
            throw ProgramResult(1)
        }

        val duration = with(scannerRun) { Duration.between(startTime, endTime).toKotlinDuration() }
        println("The scan took $duration.")

        val resolutionProvider = DefaultResolutionProvider.create(ortResult, resolutionsFile)
        val (resolvedIssues, unresolvedIssues) = scannerRun.collectIssues().flatMap { it.value }
            .partition { resolutionProvider.isResolved(it) }
        val severityStats = SeverityStats.createFromIssues(resolvedIssues, unresolvedIssues)

        severityStats.print().conclude(ortConfig.severeIssueThreshold, 2)
    }

    private fun runScanners(
        scannerWrapperFactories: List<ScannerWrapperFactory>,
        projectScannerWrapperFactories: List<ScannerWrapperFactory>,
        ortConfig: OrtConfiguration
    ): OrtResult {
        val packageScannerWrappers = scannerWrapperFactories
            .takeIf { PackageType.PACKAGE in packageTypes }.orEmpty()
            .map { it.create(ortConfig.scanner, ortConfig.downloader) }
        val projectScannerWrappers = projectScannerWrapperFactories
            .takeIf { PackageType.PROJECT in packageTypes }.orEmpty()
            .map { it.create(ortConfig.scanner, ortConfig.downloader) }

        if (projectScannerWrappers.isNotEmpty()) {
            println("Scanning projects with:")
            println(projectScannerWrappers.joinToString { "\t${it.details.name} (version ${it.details.version})" })
        } else {
            println("Projects will not be scanned.")
        }

        if (packageScannerWrappers.isNotEmpty()) {
            println("Scanning packages with:")
            println(packageScannerWrappers.joinToString { "\t${it.details.name} (version ${it.details.version})" })
        } else {
            println("Packages will not be scanned.")
        }

        val scanStorages = ScanStorages.createFromConfig(ortConfig.scanner)
        val workingTreeCache = DefaultWorkingTreeCache()

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
                scanner.scan(ortResult, skipExcluded, labels)
            }
        } finally {
            runBlocking { workingTreeCache.shutdown() }
        }
    }
}

private fun RawOption.convertToScannerWrapperFactories() =
    convert { scannerNames ->
        scannerNames.split(",").map { name ->
            ScannerWrapper.ALL[name]
                ?: throw BadParameterValue("Scanner '$name' is not one of ${ScannerWrapper.ALL.keys}.")
        }
    }
