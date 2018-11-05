/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

import com.beust.jcommander.IStringConverter
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException
import com.beust.jcommander.Parameters

import com.here.ort.CommandWithHelp
import com.here.ort.model.OutputFormat
import com.here.ort.model.config.ScannerConfiguration
import com.here.ort.model.readValue
import com.here.ort.scanner.LocalScanner
import com.here.ort.scanner.ScanResultsCache
import com.here.ort.scanner.Scanner
import com.here.ort.scanner.ScannerFactory
import com.here.ort.scanner.scanners.ScanCode
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.PARAMETER_ORDER_OPTIONAL

import java.io.File

@Parameters(commandNames = ["scan"], commandDescription = "Run existing copyright / license scanners.")
object ScannerCommand : CommandWithHelp() {
    private class ScannerConverter : IStringConverter<ScannerFactory> {
        override fun convert(scannerName: String): ScannerFactory {
            // TODO: Consider allowing to enable multiple scanners (and potentially running them in parallel).
            return Scanner.ALL.find { it.toString().equals(scannerName, true) }
                    ?: throw ParameterException("Scanner '$scannerName' is not one of ${Scanner.ALL}.")
        }
    }

    @Parameter(description = "An ORT result file with an analyzer result to use. Source code will be downloaded " +
            "automatically if needed. This parameter and '--input-path' are mutually exclusive.",
            names = ["--ort-file", "-a"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var dependenciesFile: File? = null

    @Parameter(description = "An input directory or file to scan. This parameter and '--ort-file' are mutually " +
            "exclusive.",
            names = ["--input-path", "-i"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var inputPath: File? = null

    @Parameter(description = "The list of scopes whose packages shall be scanned. Works only with the '--ort-file' " +
            "parameter. If empty, all scopes are scanned.",
            names = ["--scopes"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var scopesToScan = listOf<String>()

    @Parameter(description = "The directory to write the scan results as ORT result file(s) to, in the specified " +
            "output format(s).",
            names = ["--output-dir", "-o"],
            required = true,
            order = PARAMETER_ORDER_MANDATORY)
    @Suppress("LateinitUsage")
    private lateinit var outputDir: File

    @Parameter(description = "The output directory for downloaded source code. Defaults to <output-dir>/downloads.",
            names = ["--download-dir"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var downloadDir: File? = null

    @Parameter(description = "The scanner to use.",
            names = ["--scanner", "-s"],
            converter = ScannerConverter::class,
            order = PARAMETER_ORDER_OPTIONAL)
    private var scannerFactory: ScannerFactory = ScanCode.Factory()

    @Parameter(description = "The path to a configuration file.",
            names = ["--config", "-c"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var configFile: File? = null

    @Parameter(description = "The list of output formats to be used for the ORT result file(s).",
            names = ["--output-formats", "-f"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var outputFormats = listOf(OutputFormat.YAML)

    override fun runCommand(jc: JCommander): Int {
        require((dependenciesFile == null) != (inputPath == null)) {
            "Either '--ort-file' or '--input-path' must be specified."
        }

        require(!outputDir.exists()) {
            "The output directory '${outputDir.absolutePath}' must not exist yet."
        }

        downloadDir?.let {
            require(!it.exists()) {
                "The download directory '${it.absolutePath}' must not exist yet."
            }
        }

        val config = configFile?.let {
            require(it.isFile) {
                "Provided configuration file is not a file: ${it.invariantSeparatorsPath}"
            }

            it.readValue<ScannerConfiguration>()
        } ?: ScannerConfiguration()

        config.artifactoryCache?.let {
            ScanResultsCache.configure(it)
        }

        val scanner = scannerFactory.create(config)

        println("Using scanner '$scanner'.")

        val ortResult = dependenciesFile?.let {
            scanner.scanDependenciesFile(it, outputDir, downloadDir, scopesToScan.toSet())
        } ?: run {
            require(scanner is LocalScanner) {
                "To scan local files the chosen scanner must be a local scanner."
            }

            scanner.scanInputPath(inputPath!!, outputDir)
        }

        outputFormats.distinct().forEach { format ->
            val scanResultFile = File(outputDir, "scan-result.${format.fileExtension}")
            println("Writing scan result to '${scanResultFile.absolutePath}'.")
            format.mapper.writerWithDefaultPrettyPrinter().writeValue(scanResultFile, ortResult)
        }

        return 0
    }
}
