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
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file

import com.here.ort.model.OutputFormat
import com.here.ort.model.config.OrtConfiguration
import com.here.ort.model.config.ScannerConfiguration
import com.here.ort.model.mapper
import com.here.ort.scanner.LocalScanner
import com.here.ort.scanner.ScanResultsStorage
import com.here.ort.scanner.Scanner
import com.here.ort.scanner.TOOL_NAME
import com.here.ort.scanner.scanners.ScanCode
import com.here.ort.scanner.scanOrtResult
import com.here.ort.scanner.storages.FileBasedStorage
import com.here.ort.scanner.storages.SCAN_RESULTS_FILE_NAME
import com.here.ort.utils.expandTilde
import com.here.ort.utils.getUserOrtDirectory
import com.here.ort.utils.log
import com.here.ort.utils.storage.XZCompressedLocalFileStorage

import java.io.File

class ScannerCommand : CliktCommand(name = "scan", help = "Run existing copyright / license scanners.") {
    private val input by mutuallyExclusiveOptions(
        option(
            "--ort-file", "-i",
            help = "An ORT result file with an analyzer result to use. Source code will be downloaded automatically " +
                    "if needed. This parameter and '--input-path' are mutually exclusive."
        ).file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true),
        option(
            "--input-path", "-p",
            help = "An input directory or file to scan. This parameter and '--ort-file' are mutually exclusive."
        ).file(mustExist = true, canBeFile = true, canBeDir = true, mustBeWritable = false, mustBeReadable = true)
    ).single().required()

    private val skipExcluded by option(
        "--skip-excluded",
        help = "Do not scan excluded projects or packages. Works only with the '--ort-file' parameter."
    ).flag()

    private val outputDir by option(
        "--output-dir", "-o",
        help = "The directory to write the scan results as ORT result file(s) to, in the specified output format(s)."
    ).file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)
        .required()

    private val downloadDir by option(
        "--download-dir",
        help = "The output directory for downloaded source code. (default: <output-dir>/downloads)"
    ).file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)

    private val scannerFactory by option(
        "--scanner", "-s",
        help = "The scanner to use, one of ${Scanner.ALL}."
    ).convert { scannerName ->
        // TODO: Consider allowing to enable multiple scanners (and potentially running them in parallel).
        Scanner.ALL.find { it.scannerName.equals(scannerName, ignoreCase = true) }
            ?: throw BadParameterValue("Scanner '$scannerName' is not one of ${Scanner.ALL}.")
    }.default(ScanCode.Factory())

    private val outputFormats by option(
        "--output-formats", "-f",
        help = "The list of output formats to be used for the ORT result file(s)."
    ).enum<OutputFormat>().split(",").default(listOf(OutputFormat.YAML))

    private val config by requireObject<OrtConfiguration>()

    private fun configureScanner(scannerConfiguration: ScannerConfiguration?): Scanner {
        val config = scannerConfiguration ?: ScannerConfiguration()

        // By default use a file based scan results storage.
        val localFileStorage = XZCompressedLocalFileStorage(getUserOrtDirectory().resolve("$TOOL_NAME/results"))
        val fileBasedStorage = FileBasedStorage(localFileStorage)
        ScanResultsStorage.storage = fileBasedStorage

        // Allow to override the default scan results storage.
        val configuredStorages = listOfNotNull(
            config.fileBasedStorage,
            config.postgresStorage
        )

        require(configuredStorages.size <= 1) {
            "Only one scan results storage may be configured."
        }

        configuredStorages.forEach { ScanResultsStorage.configure(it) }

        val scanner = scannerFactory.create(config)

        println("Using scanner '${scanner.scannerName}' with storage '${ScanResultsStorage.storage.name}'.")

        val localFileStorageLogFunction: ((String) -> Unit)? = when {
            // If the local file storage is in use, log about it already at info level.
            log.delegate.isInfoEnabled && ScanResultsStorage.storage == fileBasedStorage -> log::info

            // Otherwise log about the local file storage only at debug level.
            log.delegate.isDebugEnabled -> log::debug

            else -> null
        }

        if (localFileStorageLogFunction != null) {
            val fileCount = localFileStorage.directory.walk().filter {
                it.isFile && it.name == SCAN_RESULTS_FILE_NAME
            }.count()

            localFileStorageLogFunction("Local file storage has $fileCount scan results files.")
        }

        return scanner
    }

    override fun run() {
        val absoluteOutputDir = outputDir.expandTilde().normalize()
        val absoluteNativeOutputDir = absoluteOutputDir.resolve("native-scan-results")

        val outputFiles = outputFormats.distinct().map { format ->
            File(absoluteOutputDir, "scan-result.${format.fileExtension}")
        }

        val existingOutputFiles = outputFiles.filter { it.exists() }
        if (existingOutputFiles.isNotEmpty()) {
            throw UsageError("None of the output files $existingOutputFiles must exist yet.", statusCode = 2)
        }

        if (absoluteNativeOutputDir.exists() && absoluteNativeOutputDir.list().isNotEmpty()) {
            throw UsageError("The directory '$absoluteNativeOutputDir' must not contain any files yet.", statusCode = 2)
        }

        val absoluteDownloadDir = downloadDir?.expandTilde()
        require(absoluteDownloadDir?.exists() != true) {
            "The download directory '$absoluteDownloadDir' must not exist yet."
        }

        val scanner = configureScanner(config.scanner)

        val ortResult = if (input.isFile) {
            val ortFile = input.expandTilde()
            scanOrtResult(
                scanner = scanner,
                ortResultFile = ortFile,
                outputDirectory = absoluteNativeOutputDir,
                downloadDirectory = absoluteDownloadDir ?: absoluteOutputDir.resolve("downloads"),
                skipExcluded = skipExcluded
            )
        } else {
            require(scanner is LocalScanner) {
                "To scan local files the chosen scanner must be a local scanner."
            }

            val inputPath = input.expandTilde()
            val absoluteInputPath = inputPath.normalize()
            scanner.scanPath(absoluteInputPath, absoluteNativeOutputDir)
        }

        outputFiles.forEach { file ->
            println("Writing scan result to '$file'.")
            file.mapper().writerWithDefaultPrettyPrinter().writeValue(file, ortResult)
        }
    }
}
