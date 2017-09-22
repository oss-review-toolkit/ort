package com.here.provenanceanalyzer.scanner

import com.beust.jcommander.IStringConverter
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter

import com.here.provenanceanalyzer.model.OutputFormat
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

    private class ScannerConverter : IStringConverter<Scanner> {
        override fun convert(scannerName: String): Scanner {
            return SCANNERS.find { it.javaClass.simpleName.toLowerCase() == scannerName.toLowerCase() } ?:
                    throw IllegalArgumentException("No scanner matching '$scannerName' found.")
        }
    }

    @Parameter(description = "The provenance data file to use.",
            names = arrayOf("--input-file", "-i"),
            required = true,
            order = 0)
    @Suppress("LateinitUsage")
    private lateinit var provenanceFilePath: String

    @Parameter(description = "The output directory to store the scan results and source code of packages.",
            names = arrayOf("--output", "-o"),
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

        scanner.scan(scanResult.project.asPackage(), outputDirectory)
        scanResult.packages.forEach { scanner.scan(it, outputDirectory) }
    }

}
