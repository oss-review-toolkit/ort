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

import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.Identifier
import com.here.ort.model.OutputFormat
import com.here.ort.model.Package
import com.here.ort.model.Project
import com.here.ort.model.ScanResultContainer
import com.here.ort.model.Scope
import com.here.ort.model.jsonMapper
import com.here.ort.model.mapper
import com.here.ort.model.yamlMapper
import com.here.ort.scanner.scanners.ScanCode
import com.here.ort.utils.PARAMETER_ORDER_HELP
import com.here.ort.utils.PARAMETER_ORDER_LOGGING
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.PARAMETER_ORDER_OPTIONAL
import com.here.ort.utils.collectMessages
import com.here.ort.utils.log
import com.here.ort.utils.printStackTrace
import com.here.ort.utils.showStackTrace

import java.io.File
import java.util.SortedMap
import java.util.SortedSet

import kotlin.system.exitProcess

@Suppress("unused") // The class is only used to serialize data.
class ScanSummary(
        val pkgSummary: PackageSummary,
        val cacheStats: CacheStatistics,
        val scannedScopes: SortedSet<String>,
        val ignoredScopes: SortedSet<String>,
        val analyzerErrors: SortedMap<Identifier, List<String>>
)

typealias PackageSummary = MutableMap<String, SummaryEntry>

class SummaryEntry(
        val scopes: SortedSet<String> = sortedSetOf(),
        val declaredLicenses: SortedSet<String> = sortedSetOf(),
        val detectedLicenses: SortedSet<String> = sortedSetOf(),
        val errors: MutableList<String> = mutableListOf()
)

/**
 * The main entry point of the application.
 */
object Main {
    const val TOOL_NAME = "scanner"
    const val HTTP_CACHE_PATH = "$TOOL_NAME/cache/http"

    private class OutputFormatConverter : IStringConverter<OutputFormat> {
        override fun convert(name: String): OutputFormat {
            try {
                return OutputFormat.valueOf(name.toUpperCase())
            } catch (e: IllegalArgumentException) {
                e.showStackTrace()

                throw ParameterException("Summary formats must be contained in ${OutputFormat.ALL}.")
            }
        }
    }

    private class ScannerConverter : IStringConverter<Scanner> {
        override fun convert(scannerName: String): Scanner {
            // TODO: Consider allowing to enable multiple scanners (and potentially running them in parallel).
            return Scanner.ALL.find { it.toString().equals(scannerName, true) }
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

        if (configFile != null) {
            ScanResultsCache.configure(yamlMapper.readTree(configFile))
        }

        println("Using scanner '$scanner'.")

        val scanSummary = dependenciesFile?.let { scanDependenciesFile(it) } ?: scanInputPath(inputPath!!)

        writeSummary(outputDir, scanSummary)
    }

    private fun scanDependenciesFile(dependenciesFile: File): ScanSummary {
        require(dependenciesFile.isFile) {
            "Provided path is not a file: ${dependenciesFile.absolutePath}"
        }

        val pkgSummary: PackageSummary = mutableMapOf()

        val includedScopes = sortedSetOf<Scope>()
        val excludedScopes = sortedSetOf<Scope>()
        val analyzerErrors = sortedMapOf<Identifier, List<String>>()

        val mapper = dependenciesFile.mapper()

        val analyzerResult = mapper.readValue(dependenciesFile, ProjectAnalyzerResult::class.java)
        analyzerErrors.putAll(analyzerResult.collectErrors())

        // Add the project itself also as a "package" to scan.
        val packages = mutableListOf(analyzerResult.project.toPackage())

        if (scopesToScan.isNotEmpty()) {
            println("Limiting scan to scopes $scopesToScan")

            analyzerResult.project.scopes.partition { scopesToScan.contains(it.name) }.let {
                includedScopes.addAll(it.first)
                excludedScopes.addAll(it.second)
            }

            if (includedScopes.isNotEmpty()) {
                packages.addAll(
                        analyzerResult.packages.filter { curatedPackage ->
                            includedScopes.any { scope -> scope.contains(curatedPackage.pkg) }
                        }.map { it.pkg }
                )
            } else {
                println("No scopes found for given scopes $scopesToScan.")
            }
        } else {
            includedScopes.addAll(analyzerResult.project.scopes)
            packages.addAll(analyzerResult.packages.map { it.pkg })
        }

        val results = scanner.scan(packages, outputDir, downloadDir)
        results.forEach { pkg, result ->
            // TODO: Make output format configurable.
            File(outputDir, "scanResults/${pkg.id.toPath()}/scan-results.yml").also {
                yamlMapper.writeValue(it, ScanResultContainer(pkg.id, result))
            }

            val entry = SummaryEntry(
                    scopes = findScopesForPackage(pkg, analyzerResult.project).toSortedSet(),
                    declaredLicenses = pkg.declaredLicenses,
                    detectedLicenses = result.flatMap { it.summary.licenses }.toSortedSet(),
                    errors = result.flatMap { it.summary.errors }.toMutableList()
            )

            pkgSummary[pkg.id.toString()] = entry

            println("Declared licenses for '${pkg.id}': ${entry.declaredLicenses.joinToString()}")
            println("Detected licenses for '${pkg.id}': ${entry.detectedLicenses.joinToString()}")
        }

        val scannedScopes = includedScopes.map { it.name }.toSortedSet()
        val ignoredScopes = excludedScopes.map { it.name }.toSortedSet()

        return ScanSummary(pkgSummary, ScanResultsCache.stats, scannedScopes, ignoredScopes, analyzerErrors)
    }

    private fun scanInputPath(inputPath: File): ScanSummary {
        require(inputPath.exists()) {
            "Provided path does not exist: ${inputPath.absolutePath}"
        }

        require(scanner is LocalScanner) {
            "To scan local files the chosen scanner must be a local scanner."
        }

        println("Scanning path '${inputPath.absolutePath}'...")

        val entry = try {
            val result = (scanner as LocalScanner).scanPath(inputPath, outputDir)

            println("Detected licenses for path '${inputPath.absolutePath}': " +
                    result.summary.licenses.joinToString())

            SummaryEntry(
                    detectedLicenses = result.summary.licenses,
                    errors = result.summary.errors.toMutableList()
            )
        } catch (e: ScanException) {
            e.showStackTrace()

            log.error { "Could not scan path '${inputPath.absolutePath}': ${e.message}" }

            SummaryEntry(errors = e.collectMessages().toMutableList())
        }

        return ScanSummary(
                pkgSummary = mutableMapOf(inputPath.absolutePath to entry),
                cacheStats = ScanResultsCache.stats,
                scannedScopes = sortedSetOf(),
                ignoredScopes = sortedSetOf(),
                analyzerErrors = sortedMapOf()
        )
    }

    private fun findScopesForPackage(pkg: Package, project: Project) =
            project.scopes.filter { it.contains(pkg) }.map { it.name }

    private fun writeSummary(outputDirectory: File, scanSummary: ScanSummary) {
        summaryFormats.forEach { format ->
            val summaryFile = File(outputDirectory, "scan-summary.${format.fileExtension}")
            val mapper = when (format) {
                OutputFormat.JSON -> jsonMapper
                OutputFormat.YAML -> yamlMapper
            }
            println("Writing scan summary to ${summaryFile.absolutePath}.")
            mapper.writerWithDefaultPrettyPrinter().writeValue(summaryFile, scanSummary)
        }
    }
}
