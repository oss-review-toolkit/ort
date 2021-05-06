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
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.required
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

import org.ossreviewtoolkit.GlobalOptions
import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.utils.mergeLabels
import org.ossreviewtoolkit.scanner.LocalScanner
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.scanner.Scanner
import org.ossreviewtoolkit.scanner.scanners.scancode.ScanCode
import org.ossreviewtoolkit.scanner.storages.FileBasedStorage
import org.ossreviewtoolkit.scanner.storages.SCAN_RESULTS_FILE_NAME
import org.ossreviewtoolkit.utils.expandTilde
import org.ossreviewtoolkit.utils.safeMkdirs
import org.ossreviewtoolkit.utils.storage.LocalFileStorage
import org.ossreviewtoolkit.writeOrtResult

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
    ).convert { scannerName ->
        // TODO: Consider allowing to enable multiple scanners (and potentially running them in parallel).
        Scanner.ALL.find { it.scannerName.equals(scannerName, ignoreCase = true) }
            ?: throw BadParameterValue("Scanner '$scannerName' is not one of ${Scanner.ALL}.")
    }.default(ScanCode.Factory())

    private val skipExcluded by option(
        "--skip-excluded",
        help = "Do not scan excluded projects or packages. Works only with the '--ort-file' parameter."
    ).flag()

    private val globalOptionsForSubcommands by requireObject<GlobalOptions>()

    private fun configureScanner(
        scannerConfig: ScannerConfiguration,
        downloaderConfig: DownloaderConfiguration
    ): Scanner {
        ScanResultsStorage.configure(scannerConfig)

        val scanner = scannerFactory.create(scannerConfig, downloaderConfig)

        println("Using scanner '${scanner.scannerName}' with storage '${ScanResultsStorage.storage.name}'.")

        val storage = ScanResultsStorage.storage
        if (storage is FileBasedStorage) {
            val backend = storage.backend
            if (backend is LocalFileStorage) {
                val transformedScanResultsFileName = backend.transformPath(SCAN_RESULTS_FILE_NAME)
                val fileCount = backend.directory.walk().filter {
                    it.isFile && it.name == transformedScanResultsFileName
                }.count()

                println("Local file storage has $fileCount scan results files.")
            }
        }

        return scanner
    }

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
        val scanner = configureScanner(config.scanner, config.downloader)

        val ortResult = if (input.isFile) {
            scanner.scanOrtResult(
                ortFile = input,
                outputDirectory = nativeOutputDir,
                skipExcluded = skipExcluded
            )
        } else {
            require(scanner is LocalScanner) {
                "To scan local files the chosen scanner must be a local scanner."
            }

            scanner.scanPath(
                inputPath = input,
                outputDirectory = nativeOutputDir
            )
        }.mergeLabels(labels)

        outputDir.safeMkdirs()
        writeOrtResult(ortResult, outputFiles, "scan")

        val scanResults = ortResult.scanner?.results

        if (scanResults == null) {
            println("There was an error creating the scan results.")
            throw ProgramResult(1)
        }

        val hasSevereIssues = scanResults.scanResults.any { (_, results) ->
            results.any { it.summary.issues.any { issue -> issue.severity > Severity.HINT } }
        }

        if (hasSevereIssues) {
            println("The scan result contains errors or warnings.")
            throw ProgramResult(2)
        }

        println("The scan result contains hints only.")
    }
}
