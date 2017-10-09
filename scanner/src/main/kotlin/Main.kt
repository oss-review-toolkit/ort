package com.here.provenanceanalyzer.scanner

import ch.frankel.slf4k.*

import com.beust.jcommander.IStringConverter
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException

import com.here.provenanceanalyzer.model.ALL_OUTPUT_FORMATS
import com.here.provenanceanalyzer.model.OutputFormat
import com.here.provenanceanalyzer.model.Package
import com.here.provenanceanalyzer.model.Project
import com.here.provenanceanalyzer.model.ScanResult
import com.here.provenanceanalyzer.util.jsonMapper
import com.here.provenanceanalyzer.util.log
import com.here.provenanceanalyzer.util.yamlMapper

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
                throw ParameterException("Summary formats must be contained in $ALL_OUTPUT_FORMATS.")
            }
        }
    }

    private class ScannerConverter : IStringConverter<Scanner> {
        override fun convert(scannerName: String): Scanner {
            // TODO: Consider allowing to enable multiple scanners (and potentially running them in parallel).
            return ALL_SCANNERS.find { it.javaClass.simpleName.toUpperCase() == scannerName.toUpperCase() } ?:
                    throw ParameterException("The scanner must be one of $ALL_SCANNERS.")
        }
    }

    @Parameter(description = "The provenance data file to use.",
            names = arrayOf("--input-file", "-i"),
            required = true,
            order = 0)
    @Suppress("LateinitUsage")
    private lateinit var provenanceFilePath: String

    @Parameter(description = "The output directory to store the scan results and source code of packages.",
            names = arrayOf("--output-dir", "-o"),
            required = true,
            order = 0)
    @Suppress("LateinitUsage")
    private lateinit var outputPath: String

    @Parameter(description = "The scanner to use.",
            names = arrayOf("--scanner", "-s"),
            converter = ScannerConverter::class,
            order = 0)
    private var scanner: Scanner = ScanCode

    @Parameter(description = "The path to the cache configuration file.",
            names = arrayOf("--cache", "-c"),
            order = 0)
    @Suppress("LateinitUsage")
    private var cacheConfigurationFile: File? = null

    @Parameter(description = "The list of file formats for the summary files.",
            names = arrayOf("--summary-format", "-f"),
            converter = OutputFormatConverter::class,
            order = 0)
    private var summaryFormats: List<OutputFormat> = listOf(OutputFormat.YAML)

    @Parameter(description = "Enable info logging.",
            names = arrayOf("--info"),
            order = 0)
    private var info = false

    @Parameter(description = "Enable debug logging.",
            names = arrayOf("--debug"),
            order = 0)
    private var debug = false

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

        val provenanceFile = File(provenanceFilePath)
        require(provenanceFile.isFile) {
            "Provided path is not a file: ${provenanceFile.absolutePath}"
        }

        val mapper = when (provenanceFile.extension) {
            OutputFormat.JSON.fileEnding -> jsonMapper
            OutputFormat.YAML.fileEnding -> yamlMapper
            else -> throw IllegalArgumentException("Provided input file is neither JSON nor YAML.")
        }

        if (cacheConfigurationFile != null) {
            val mapType = yamlMapper.typeFactory.constructMapType(HashMap::class.java, String::class.java,
                    String::class.java)
            val cacheConfiguration: HashMap<String, String> = yamlMapper.readValue(cacheConfigurationFile, mapType)
            ScanResultsCache.configure(cacheConfiguration)
        }

        val outputDirectory = File(outputPath)
        require(!outputDirectory.exists()) {
            "The output directory '${outputDirectory.absolutePath}' must not exist yet."
        }

        val scanResult = mapper.readValue(provenanceFile, ScanResult::class.java)

        println("Using scanner '$scanner'.")

        val packages = mutableListOf(scanResult.project.toPackage())
        packages.addAll(scanResult.packages)

        val summary = mutableMapOf<String, SummaryEntry>()

        packages.forEach { pkg ->
            val entry = summary.getOrPut(pkg.identifier) { SummaryEntry() }
            entry.scopes.addAll(findScopesForPackage(pkg, scanResult.project))
            try {
                println("Scanning ${pkg.identifier}")
                entry.licenses.addAll(scanner.scan(pkg, outputDirectory).sorted())
                println("Found licenses for ${pkg.identifier}: ${entry.licenses.joinToString()}")
            } catch (e: ScanException) {
                log.error { "Could not scan ${pkg.identifier}: ${e.message}" }
                var cause: Throwable? = e
                while (cause != null) {
                    entry.errors.add("${cause.javaClass.simpleName}: ${cause.message}")
                    cause = cause.cause
                }
            }
        }

        writeSummary(outputDirectory, summary)
    }

    private fun findScopesForPackage(pkg: Package, project: Project): List<String> {
        return project.scopes.filter { it.contains(pkg) }.map { it.name }
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
