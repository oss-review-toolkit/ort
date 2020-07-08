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
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.config.OrtConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.mapper
import org.ossreviewtoolkit.scanner.LocalScanner
import org.ossreviewtoolkit.scanner.ScanResultsStorage
import org.ossreviewtoolkit.scanner.Scanner
import org.ossreviewtoolkit.scanner.scanners.ScanCode
import org.ossreviewtoolkit.scanner.storages.FileBasedStorage
import org.ossreviewtoolkit.scanner.storages.SCAN_RESULTS_FILE_NAME
import org.ossreviewtoolkit.utils.expandTilde
import org.ossreviewtoolkit.utils.storage.LocalFileStorage

import java.io.File

class ScannerCommand : CliktCommand(name = "scan", help = "Run existing copyright / license scanners.") {
    private val input by mutuallyExclusiveOptions(
        option(
            "--ort-file", "-i",
            help = "An ORT result file with an analyzer result to use. Source code will be downloaded automatically " +
                    "if needed. This parameter and '--input-path' are mutually exclusive."
        ).convert { it.expandTilde() }
            .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true),
        option(
            "--input-path", "-p",
            help = "An input directory or file to scan. This parameter and '--ort-file' are mutually exclusive."
        ).convert { it.expandTilde() }
            .file(mustExist = true, canBeFile = true, canBeDir = true, mustBeWritable = false, mustBeReadable = true)
    ).single().required()

    private val skipExcluded by option(
        "--skip-excluded",
        help = "Do not scan excluded projects or packages. Works only with the '--ort-file' parameter."
    ).flag()

    private val outputDir by option(
        "--output-dir", "-o",
        help = "The directory to write the scan results as ORT result file(s) to, in the specified output format(s)."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)
        .required()

    private val downloadDir by option(
        "--download-dir",
        help = "The output directory for downloaded source code. (default: <output-dir>/downloads)"
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)

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
    ).enum<FileFormat>().split(",").default(listOf(FileFormat.YAML))

    private val config by requireObject<OrtConfiguration>()

    private fun configureScanner(scannerConfiguration: ScannerConfiguration?): Scanner {
        val config = scannerConfiguration ?: ScannerConfiguration()

        ScanResultsStorage.configure(config)

        val scanner = scannerFactory.create(config)

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
        val absoluteOutputDir = outputDir.normalize()
        val absoluteNativeOutputDir = absoluteOutputDir.resolve("native-scan-results")

        val outputFiles = outputFormats.distinct().map { format ->
            File(absoluteOutputDir, "scan-result.${format.fileExtension}")
        }

        val existingOutputFiles = outputFiles.filter { it.exists() }
        if (existingOutputFiles.isNotEmpty()) {
            throw UsageError("None of the output files $existingOutputFiles must exist yet.")
        }

        if (absoluteNativeOutputDir.exists() && absoluteNativeOutputDir.list().isNotEmpty()) {
            throw UsageError("The directory '$absoluteNativeOutputDir' must not contain any files yet.")
        }

        require(downloadDir?.exists() != true) {
            "The download directory '$downloadDir' must not exist yet."
        }

        val scanner = configureScanner(config.scanner)

        val ortResult = if (input.isFile) {
            scanner.scanOrtResult(
                ortResultFile = input,
                outputDirectory = absoluteNativeOutputDir,
                downloadDirectory = downloadDir ?: absoluteOutputDir.resolve("downloads"),
                skipExcluded = skipExcluded
            )
        } else {
            require(scanner is LocalScanner) {
                "To scan local files the chosen scanner must be a local scanner."
            }

            val absoluteInputPath = input.normalize()
            scanner.scanPath(absoluteInputPath, absoluteNativeOutputDir)
        }

        outputFiles.forEach { file ->
            println("Writing scan result to '$file'.")
            file.mapper().writerWithDefaultPrettyPrinter().writeValue(file, ortResult)
        }

        val scanResults = ortResult.scanner?.results

        if (scanResults == null) {
            println("There was an error creating the scan results.")
            throw ProgramResult(1)
        }

        if (scanResults.hasIssues) {
            println("The scan result contains issues.")
            throw ProgramResult(2)
        }
    }
}
