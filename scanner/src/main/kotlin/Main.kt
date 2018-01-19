/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

import ch.frankel.slf4k.*

import com.beust.jcommander.IStringConverter
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException

import com.here.ort.model.OutputFormat
import com.here.ort.model.Package
import com.here.ort.model.Project
import com.here.ort.model.AnalyzerResult
import com.here.ort.scanner.scanners.ScanCode
import com.here.ort.utils.PARAMETER_ORDER_HELP
import com.here.ort.utils.PARAMETER_ORDER_LOGGING
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.PARAMETER_ORDER_OPTIONAL
import com.here.ort.utils.collectMessages
import com.here.ort.utils.jsonMapper
import com.here.ort.utils.log
import com.here.ort.utils.yamlMapper

import java.io.File
import java.util.SortedSet

import kotlin.system.exitProcess

class ScanSummary(
        val pkgSummary: PackageSummary,
        val cacheStats: CacheStatistics
)

typealias PackageSummary = MutableMap<String, SummaryEntry>

class SummaryEntry(
        val scopes: SortedSet<String> = sortedSetOf(),
        val licenses: SortedSet<String> = sortedSetOf(),
        val errors: MutableList<String> = mutableListOf()
)

/**
 * The main entry point of the application.
 */
object Main {
    const val TOOL_NAME = "scanner"

    private class OutputFormatConverter : IStringConverter<OutputFormat> {
        override fun convert(name: String): OutputFormat {
            try {
                return OutputFormat.valueOf(name.toUpperCase())
            } catch (e: IllegalArgumentException) {
                if (stacktrace) {
                    e.printStackTrace()
                }

                throw ParameterException("Summary formats must be contained in ${OutputFormat.ALL}.")
            }
        }
    }

    private class ScannerConverter : IStringConverter<Scanner> {
        override fun convert(scannerName: String): Scanner {
            // TODO: Consider allowing to enable multiple scanners (and potentially running them in parallel).
            return Scanner.ALL.find { it.javaClass.simpleName.toUpperCase() == scannerName.toUpperCase() }
                    ?: throw ParameterException("The scanner must be one of ${Scanner.ALL}.")
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
    private var scanner: Scanner = ScanCode

    @Parameter(description = "The path to the configuration file.",
            names = ["--config", "-c"],
            order = PARAMETER_ORDER_OPTIONAL)
    @Suppress("LateinitUsage")
    private var configFile: File? = null

    @Parameter(description = "The list of file formats for the summary files.",
            names = ["--summary-format", "-f"],
            converter = OutputFormatConverter::class,
            order = PARAMETER_ORDER_OPTIONAL)
    private var summaryFormats = listOf(OutputFormat.YAML)

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
    var stacktrace = false

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

        if ((dependenciesFile != null) == (inputPath != null)) {
            throw IllegalArgumentException("Either --dependencies-file or --input-path must be specified.")
        }

        require(!outputDir.exists()) {
            "The output directory '${outputDir.absolutePath}' must not exist yet."
        }

        downloadDir?.let {
            require(!it.exists()) {
                "The download directory '${it.absolutePath}' must not exist yet."
            }
        }

        if (configFile != null) {
            ScanResultsCache.configure(yamlMapper.readTree(configFile))
        }

        println("Using scanner '$scanner'.")

        val pkgSummary: PackageSummary = mutableMapOf()

        dependenciesFile?.let { dependenciesFile ->
            require(dependenciesFile.isFile) {
                "Provided path is not a file: ${dependenciesFile.absolutePath}"
            }

            val mapper = when (dependenciesFile.extension) {
                OutputFormat.JSON.fileEnding -> jsonMapper
                OutputFormat.YAML.fileEnding -> yamlMapper
                else -> throw IllegalArgumentException("Provided input file is neither JSON nor YAML.")
            }

            val analyzerResult = mapper.readValue(dependenciesFile, AnalyzerResult::class.java)

            val packages = mutableListOf(analyzerResult.project.toPackage())
            packages.addAll(analyzerResult.packages)

            packages.forEach { pkg ->
                val entry = pkgSummary.getOrPut(pkg.id.toString()) { SummaryEntry() }
                entry.scopes.addAll(findScopesForPackage(pkg, analyzerResult.project))
                scanEntry(entry, pkg.id.toString(), pkg)
            }
        }

        inputPath?.let { inputPath ->
            require(inputPath.exists()) {
                "Provided path does not exist: ${inputPath.absolutePath}"
            }

            val entry = pkgSummary.getOrPut(inputPath.absolutePath) { SummaryEntry() }
            scanEntry(entry, inputPath.absolutePath, inputPath)
        }

        writeSummary(outputDir, ScanSummary(pkgSummary, ScanResultsCache.stats))
    }

    private fun findScopesForPackage(pkg: Package, project: Project) =
            project.scopes.filter { it.contains(pkg) }.map { it.name }

    private fun scanEntry(entry: SummaryEntry, identifier: String, input: Any) {
        try {
            println("Scanning package '$identifier'...")

            val result = when (input) {
                is Package -> {
                    entry.licenses.addAll(input.declaredLicenses)
                    scanner.scan(input, outputDir, downloadDir)
                }
                is File -> scanner.scan(input, outputDir)
                else -> throw IllegalArgumentException("Unsupported scan input.")
            }
            entry.licenses.addAll(result.licenses)

            println("Found licenses for '$identifier': ${entry.licenses.joinToString()}")
        } catch (e: ScanException) {
            if (stacktrace) {
                e.printStackTrace()
            }

            log.error { "Could not scan '$identifier': ${e.message}" }

            entry.errors.addAll(e.collectMessages())
        }
    }

    private fun writeSummary(outputDirectory: File, scanSummary: ScanSummary) {
        summaryFormats.forEach { format ->
            val summaryFile = File(outputDirectory, "scan-summary.${format.fileEnding}")
            val mapper = when (format) {
                OutputFormat.JSON -> jsonMapper
                OutputFormat.YAML -> yamlMapper
            }
            println("Writing scan summary to ${summaryFile.absolutePath}.")
            mapper.writerWithDefaultPrettyPrinter().writeValue(summaryFile, scanSummary)
        }
    }
}
