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

package com.here.ort.scanner

import com.beust.jcommander.IStringConverter
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException

import com.here.ort.model.OutputFormat
import com.here.ort.model.config.ScannerConfiguration
import com.here.ort.model.readValue
import com.here.ort.scanner.scanners.ScanCode
import com.here.ort.utils.PARAMETER_ORDER_HELP
import com.here.ort.utils.PARAMETER_ORDER_LOGGING
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.PARAMETER_ORDER_OPTIONAL
import com.here.ort.utils.log
import com.here.ort.utils.printStackTrace

import java.io.File

import kotlin.system.exitProcess

/**
 * The main entry point of the application.
 */
object Main {
    const val TOOL_NAME = "scanner"
    const val HTTP_CACHE_PATH = "$TOOL_NAME/cache/http"

    private class ScannerConverter : IStringConverter<ScannerFactory> {
        override fun convert(scannerName: String): ScannerFactory {
            // TODO: Consider allowing to enable multiple scanners (and potentially running them in parallel).
            return Scanner.ALL.find { it.toString().equals(scannerName, true) }
                    ?: throw ParameterException("Scanner '$scannerName' is not one of ${Scanner.ALL}.")
        }
    }

    @Parameter(description = "The dependencies analysis file to use. Source code will be downloaded automatically if " +
            "needed. This parameter and --input-path are mutually exclusive.",
            names = ["--dependencies-file", "-d"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var dependenciesFile: File? = null

    @Parameter(description = "The input directory or file to scan. This parameter and --dependencies-file are " +
            "mutually exclusive.",
            names = ["--input-path", "-i"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var inputPath: File? = null

    @Parameter(description = "The list of scopes that shall be scanned. Works only with the " +
            "--dependencies-file parameter. If empty, all scopes are scanned.",
            names = ["--scopes"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var scopesToScan = listOf<String>()

    @Parameter(description = "The output directory to store the scan results in.",
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
    private var scannerFactory: ScannerFactory? = null

    @Parameter(description = "The path to the configuration file.",
            names = ["--config", "-c"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var configFile: File? = null

    @Parameter(description = "The list of output formats used for the result file(s).",
            names = ["--output-formats", "-f"],
            order = PARAMETER_ORDER_OPTIONAL)
    private var outputFormats = listOf(OutputFormat.YAML)

    @Parameter(description = "Enable info logging.",
            names = ["--info"],
            order = PARAMETER_ORDER_LOGGING)
    private var info = false

    @Parameter(description = "Enable debug logging and keep any temporary files.",
            names = ["--debug"],
            order = PARAMETER_ORDER_LOGGING)
    private var debug = false

    @Parameter(description = "Print out the stacktrace for all exceptions.",
            names = ["--stacktrace"],
            order = PARAMETER_ORDER_LOGGING)
    private var stacktrace = false

    @Parameter(description = "Display the command line help.",
            names = ["--help", "-h"],
            help = true,
            order = PARAMETER_ORDER_HELP)
    private var help = false

    /**
     * The entry point for the application.
     *
     * @param args The list of application arguments.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val jc = JCommander(this)
        jc.parse(*args)
        jc.programName = TOOL_NAME

        if (info) {
            log.level = ch.qos.logback.classic.Level.INFO
        }

        if (debug) {
            log.level = ch.qos.logback.classic.Level.DEBUG
        }

        if (help) {
            jc.usage()
            exitProcess(1)
        }

        // Make the parameter globally available.
        printStackTrace = stacktrace

        require((dependenciesFile == null) != (inputPath == null)) {
            "Either --dependencies-file or --input-path must be specified."
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

            it.readValue(ScannerConfiguration::class.java)
        } ?: ScannerConfiguration()

        config.artifactoryCache?.let {
            ScanResultsCache.configure(it)
        }

        val scanner = scannerFactory?.let {
            it.create(config)
        } ?: ScanCode(config)

        println("Using scanner '$scanner'.")

        val ortResult = dependenciesFile?.let {
            scanner.scanDependenciesFile(it, outputDir, downloadDir, scopesToScan)
        } ?: run {
            require(scanner is LocalScanner) {
                "To scan local files the chosen scanner must be a local scanner."
            }

            val localScanner = scanner as LocalScanner
            localScanner.scanInputPath(inputPath!!, outputDir)
        }

        outputFormats.forEach { format ->
            val scanRecordFile = File(outputDir, "scan-result.${format.fileExtension}")
            println("Writing scan record to '${scanRecordFile.absolutePath}'.")
            format.mapper.writerWithDefaultPrettyPrinter().writeValue(scanRecordFile, ortResult)
        }
    }
}
