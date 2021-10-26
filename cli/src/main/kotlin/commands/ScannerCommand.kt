/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
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
import com.github.ajalt.clikt.core.CliktCommand
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

import java.io.File

import kotlinx.coroutines.runBlocking

import org.ossreviewtoolkit.cli.GlobalOptions
import org.ossreviewtoolkit.cli.utils.OPTION_GROUP_INPUT
import org.ossreviewtoolkit.cli.utils.SeverityStats
import org.ossreviewtoolkit.cli.utils.configurationGroup
import org.ossreviewtoolkit.cli.utils.outputGroup
import org.ossreviewtoolkit.cli.utils.readOrtResult
import org.ossreviewtoolkit.cli.utils.writeOrtResult
import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.ClearlyDefinedStorageConfiguration
import org.ossreviewtoolkit.model.config.FileBasedStorageConfiguration
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.config.PostgresStorageConfiguration
import org.ossreviewtoolkit.model.config.ScanStorageConfiguration
import org.ossreviewtoolkit.model.config.StorageType
import org.ossreviewtoolkit.model.config.Sw360StorageConfiguration
import org.ossreviewtoolkit.model.utils.DatabaseUtils
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.model.utils.mergeLabels
import org.ossreviewtoolkit.scanner.LocalScanner
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.scanner.Scanner
import org.ossreviewtoolkit.scanner.TOOL_NAME
import org.ossreviewtoolkit.scanner.experimental.DefaultNestedProvenanceResolver
import org.ossreviewtoolkit.scanner.experimental.DefaultPackageProvenanceResolver
import org.ossreviewtoolkit.scanner.experimental.DefaultProvenanceDownloader
import org.ossreviewtoolkit.scanner.experimental.ExperimentalScanner
import org.ossreviewtoolkit.scanner.experimental.ProvenanceBasedFileStorage
import org.ossreviewtoolkit.scanner.experimental.ProvenanceBasedPostgresStorage
import org.ossreviewtoolkit.scanner.experimental.ScanStorage
import org.ossreviewtoolkit.scanner.scanOrtResult
import org.ossreviewtoolkit.scanner.scanners.Askalono
import org.ossreviewtoolkit.scanner.scanners.BoyterLc
import org.ossreviewtoolkit.scanner.scanners.Licensee
import org.ossreviewtoolkit.scanner.scanners.scancode.ScanCode
import org.ossreviewtoolkit.scanner.storages.ClearlyDefinedStorage
import org.ossreviewtoolkit.scanner.storages.FileBasedStorage
import org.ossreviewtoolkit.scanner.storages.PostgresStorage
import org.ossreviewtoolkit.scanner.storages.SCAN_RESULTS_FILE_NAME
import org.ossreviewtoolkit.scanner.storages.Sw360Storage
import org.ossreviewtoolkit.utils.core.ORT_RESOLUTIONS_FILENAME
import org.ossreviewtoolkit.utils.core.expandTilde
import org.ossreviewtoolkit.utils.core.ortConfigDirectory
import org.ossreviewtoolkit.utils.core.ortDataDirectory
import org.ossreviewtoolkit.utils.core.safeMkdirs
import org.ossreviewtoolkit.utils.core.storage.LocalFileStorage
import org.ossreviewtoolkit.utils.core.storage.XZCompressedLocalFileStorage

private fun RawOption.convertToScanner() =
    convert { scannerName ->
        // TODO: Consider allowing to enable multiple scanners (and potentially running them in parallel).
        Scanner.ALL.find { it.scannerName.equals(scannerName, ignoreCase = true) }
            ?: throw BadParameterValue("Scanner '$scannerName' is not one of ${Scanner.ALL}.")
    }

class ScannerCommand : CliktCommand(name = "scan", help = "Run external license / copyright scanners.") {
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

    private val scannerFactory by option(
        "--scanner", "-s",
        help = "The scanner to use, one of ${Scanner.ALL}."
    ).convertToScanner().default(ScanCode.Factory())

    private val projectScannerFactory by option(
        "--project-scanner",
        help = "The scanner to use for scanning the source code of projects. By default, projects and packages are " +
                "scanned with the same scanner as specified by '--scanner'."
    ).convertToScanner()

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

    private val experimental by option(
        "--experimental",
        help = "Use a new experimental implementation of the scanner which scans by provenance instead of by " +
                "package. This improves reuse of stored scan results and increases performance if multiple packages " +
                "are coming from the same source code repository. The experimental scanner is work in progress and " +
                "it is therefore not recommended to use it in production."
    ).flag()

    private val globalOptionsForSubcommands by requireObject<GlobalOptions>()

    override fun run() {
        val nativeOutputDir = outputDir.resolve("native-scan-results")

        val outputFiles = outputFormats.mapTo(mutableSetOf()) { format ->
            outputDir.resolve("scan-result.${format.fileExtension}")
        }

        if (!globalOptionsForSubcommands.forceOverwrite) {
            val existingOutputFiles = outputFiles.filter { it.exists() }
            if (existingOutputFiles.isNotEmpty()) {
                throw UsageError("None of the output files $existingOutputFiles must exist yet.", statusCode = 2)
            }

            if (nativeOutputDir.exists() && nativeOutputDir.list().isNotEmpty()) {
                throw UsageError("The directory '$nativeOutputDir' must not contain any files yet.", statusCode = 2)
            }
        }

        val config = globalOptionsForSubcommands.config

        val ortResult = if (experimental) {
            runExperimental(config)
        } else {
            run(nativeOutputDir, config)
        }.mergeLabels(labels)

        // Write the result.
        outputDir.safeMkdirs()
        writeOrtResult(ortResult, outputFiles, "scan")

        val scanResults = ortResult.scanner?.results

        if (scanResults == null) {
            println("There was an error creating the scan results.")
            throw ProgramResult(1)
        }

        val resolutionProvider = DefaultResolutionProvider.create(ortResult, resolutionsFile)
        val (resolvedIssues, unresolvedIssues) =
            scanResults.collectIssues().flatMap { it.value }.partition { resolutionProvider.isResolved(it) }
        val severityStats = SeverityStats.createFromIssues(resolvedIssues, unresolvedIssues)

        severityStats.printAndConclude(config.severeIssueThreshold, 2)
    }

    private fun run(nativeOutputDir: File, config: OrtConfiguration): OrtResult {
        // Configure the scan storage, which is common to all scanners.
        ScanResultsStorage.configure(config.scanner)

        val storage = ScanResultsStorage.storage
        println("Using scan storage '${storage.name}'.")

        if (storage is FileBasedStorage) {
            val backend = storage.backend
            if (backend is LocalFileStorage) {
                val transformedScanResultsFileName = backend.transformPath(SCAN_RESULTS_FILE_NAME)
                val fileCount = backend.directory.walk().filter {
                    it.isFile && it.name == transformedScanResultsFileName
                }.count()

                println("Local file storage has $fileCount scan results file(s).")
            }
        }

        // Configure the package and project scanners.
        val scanner = scannerFactory.create(config.scanner, config.downloader).also {
            println("Using scanner '${it.scannerName}'.")
        }

        val projectScanner = projectScannerFactory?.create(config.scanner, config.downloader)?.also {
            println("Using project scanner '${it.scannerName}'.")
        } ?: scanner

        // Perform the scan.
        return if (input.isFile) {
            val ortResult = readOrtResult(input)
            scanOrtResult(scanner, projectScanner, ortResult, nativeOutputDir, skipExcluded)
        } else {
            require(projectScanner is LocalScanner) {
                "To scan local files the chosen project scanner must be a local scanner."
            }

            projectScanner.scanPath(
                inputPath = input,
                outputDirectory = nativeOutputDir
            )
        }
    }

    private fun runExperimental(config: OrtConfiguration): OrtResult {
        // TODO: The experimental scanner supports using multiple scanner wrappers at once, for now use only one to stay
        //       compatible with the existing scanner command. Once the experimental flag is removed this command can
        //       support multiple scanners and use the proper ScannerWrapperFactories.
        val scannerWrapper = when (scannerFactory.scannerName) {
            "Askalono" -> Askalono.Factory().create(config.scanner, config.downloader)
            "BoyterLc" -> BoyterLc.Factory().create(config.scanner, config.downloader)
            "Licensee" -> Licensee.Factory().create(config.scanner, config.downloader)
            "ScanCode" -> ScanCode.Factory().create(config.scanner, config.downloader)
            else -> {
                throw IllegalArgumentException(
                    "The scanner ${scannerFactory.scannerName} is not supported by the experimental scanner."
                )
            }
        }

        val storages = config.scanner.storages.orEmpty().mapValues { createStorage(it.value) }

        fun resolve(name: String): ScanStorage = requireNotNull(storages[name]) { "Could not resolve storage '$name'." }

        val defaultStorage = createDefaultStorage()

        val readers = config.scanner.storageReaders.orEmpty().map { resolve(it) }
            .takeIf { it.isNotEmpty() } ?: listOf(defaultStorage)
        val writers = config.scanner.storageWriters.orEmpty().map { resolve(it) }
            .takeIf { it.isNotEmpty() } ?: listOf(defaultStorage)

        val scanner = ExperimentalScanner(
            scannerConfig = config.scanner,
            downloaderConfig = config.downloader,
            provenanceDownloader = DefaultProvenanceDownloader(config.downloader),
            storageReaders = readers,
            storageWriters = writers,
            packageProvenanceResolver = DefaultPackageProvenanceResolver(),
            nestedProvenanceResolver = DefaultNestedProvenanceResolver(),
            scannerWrappers = listOf(scannerWrapper)
        )

        val ortResult = readOrtResult(input)
        return runBlocking {
            scanner.scan(ortResult)
        }
    }
}

private fun createDefaultStorage(): ScanStorage {
    val localFileStorage = XZCompressedLocalFileStorage(ortDataDirectory.resolve("$TOOL_NAME/results"))
    return ProvenanceBasedFileStorage(localFileStorage)
}

private fun createStorage(config: ScanStorageConfiguration): ScanStorage =
    when (config) {
        is FileBasedStorageConfiguration -> createFileBasedStorage(config)
        is PostgresStorageConfiguration -> createPostgresStorage(config)
        is ClearlyDefinedStorageConfiguration -> createClearlyDefinedStorage(config)
        is Sw360StorageConfiguration -> createSw360Storage(config)
    }

private fun createFileBasedStorage(config: FileBasedStorageConfiguration) =
    when (config.type) {
        StorageType.PACKAGE_BASED -> FileBasedStorage(config.backend.createFileStorage())
        StorageType.PROVENANCE_BASED -> ProvenanceBasedFileStorage(config.backend.createFileStorage())
    }

private fun createPostgresStorage(config: PostgresStorageConfiguration) =
    when (config.type) {
        StorageType.PACKAGE_BASED -> PostgresStorage(
            DatabaseUtils.createHikariDataSource(config = config, applicationNameSuffix = TOOL_NAME)
        )
        StorageType.PROVENANCE_BASED -> ProvenanceBasedPostgresStorage(
            DatabaseUtils.createHikariDataSource(config = config, applicationNameSuffix = TOOL_NAME)
        )
    }

private fun createClearlyDefinedStorage(config: ClearlyDefinedStorageConfiguration) = ClearlyDefinedStorage(config)

private fun createSw360Storage(config: Sw360StorageConfiguration) = Sw360Storage(config)
