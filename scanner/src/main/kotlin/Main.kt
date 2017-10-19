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
import com.here.ort.util.jsonMapper
import com.here.ort.util.log
import com.here.ort.util.yamlMapper

import java.io.File

import kotlin.system.exitProcess

/**
 * The main entry point of the application.
 */
object Main {
    private class SummaryEntry(
            val scopes: MutableList<String> = mutableListOf(),
            val licenses: MutableList<String> = mutableListOf(),
            val errors: MutableList<String> = mutableListOf()
    )

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
            return Scanner.ALL.find { it.javaClass.simpleName.toUpperCase() == scannerName.toUpperCase() } ?:
                    throw ParameterException("The scanner must be one of ${Scanner.ALL}.")
        }
    }

    @Parameter(description = "The dependencies analysis file to use. Source code will be downloaded automatically if " +
            "needed. This parameter and --input-path are mutually exclusive.",
            names = arrayOf("--dependencies-file", "-d"),
            order = 0)
    private var dependenciesFile: File? = null

    @Parameter(description = "The input directory or file to scan. This parameter and --dependencies-file are " +
            "mutually exclusive.",
            names = arrayOf("--input-path", "-i"),
            order = 0)
    private var inputPath: File? = null

    @Parameter(description = "The output directory to store the scan results and source code of packages.",
            names = arrayOf("--output-dir", "-o"),
            required = true,
            order = 0)
    @Suppress("LateinitUsage")
    private lateinit var outputDir: File

    @Parameter(description = "The scanner to use.",
            names = arrayOf("--scanner", "-s"),
            converter = ScannerConverter::class,
            order = 0)
    private var scanner: Scanner = ScanCode

    @Parameter(description = "The path to the configuration file.",
            names = arrayOf("--config", "-c"),
            order = 0)
    @Suppress("LateinitUsage")
    private var configFile: File? = null

    @Parameter(description = "The list of file formats for the summary files.",
            names = arrayOf("--summary-format", "-f"),
            converter = OutputFormatConverter::class,
            order = 0)
    private var summaryFormats: List<OutputFormat> = listOf(OutputFormat.YAML)

    @Parameter(description = "Enable info logging.",
            names = arrayOf("--info"),
            order = 0)
    private var info = false

    @Parameter(description = "Enable debug logging and keep any temporary files.",
            names = arrayOf("--debug"),
            order = 0)
    private var debug = false

    @Parameter(description = "Print out the stacktrace for all exceptions.",
            names = arrayOf("--stacktrace"),
            order = 0)
    var stacktrace = false

    @Parameter(description = "Display the command line help.",
            names = arrayOf("--help", "-h"),
            help = true,
            order = 100)
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
        jc.programName = "scanner"

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

        if (configFile != null) {
            ScanResultsCache.configure(yamlMapper.readTree(configFile))
        }

        println("Using scanner '$scanner'.")

        val summary = mutableMapOf<String, SummaryEntry>()

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
                val entry = summary.getOrPut(pkg.identifier) { SummaryEntry() }
                entry.scopes.addAll(findScopesForPackage(pkg, analyzerResult.project))
                scanEntry(entry, pkg.identifier, pkg)
            }
        }

        inputPath?.let { inputPath ->
            require(inputPath.exists()) {
                "Provided path does not exist: ${inputPath.absolutePath}"
            }

            val entry = summary.getOrPut(inputPath.absolutePath) { SummaryEntry() }
            scanEntry(entry, inputPath.absolutePath, inputPath)
        }

        writeSummary(outputDir, summary)
    }

    private fun findScopesForPackage(pkg: Package, project: Project): List<String> {
        return project.scopes.filter { it.contains(pkg) }.map { it.name }
    }

    private fun scanEntry(entry: SummaryEntry, identifier: String, input: Any) {
        try {
            println("Scanning '$identifier'...")

            val result = when (input) {
                is Package -> scanner.scan(input, outputDir)
                is File -> scanner.scan(input, outputDir)
                else -> throw IllegalArgumentException("Unsupported scan input.")
            }
            entry.licenses.addAll(result.sorted())

            println("Found licenses for '$identifier: ${entry.licenses.joinToString()}")
        } catch (e: ScanException) {
            if (stacktrace) {
                e.printStackTrace()
            }

            log.error { "Could not scan '$identifier': ${e.message}" }

            var cause: Throwable? = e
            while (cause != null) {
                entry.errors.add("${cause.javaClass.simpleName}: ${cause.message}")
                cause = cause.cause
            }
        }
    }

    private fun writeSummary(outputDirectory: File, summary: MutableMap<String, SummaryEntry>) {
        summaryFormats.forEach { format ->
            val summaryFile = File(outputDirectory, "scan-summary.${format.fileEnding}")
            val mapper = when (format) {
                OutputFormat.JSON -> jsonMapper
                OutputFormat.YAML -> yamlMapper
            }
            println("Writing scan summary to ${summaryFile.absolutePath}.")
            mapper.writerWithDefaultPrettyPrinter().writeValue(summaryFile, summary)
        }
    }
}
