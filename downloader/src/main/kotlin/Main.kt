package com.here.provenanceanalyzer.downloader

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter

import com.here.provenanceanalyzer.model.OutputFormat
import com.here.provenanceanalyzer.model.ScanResult
import com.here.provenanceanalyzer.model.jsonMapper
import com.here.provenanceanalyzer.model.yamlMapper

import java.io.File

import kotlin.system.exitProcess

/**
 * The main entry point of the application.
 */
object Main {

    @Parameter(description = "provenance data file path")
    private var provenanceFilePath: String? = null

    @Parameter(names = arrayOf("--help", "-h"),
            description = "Display the command line help.",
            help = true,
            order = 100)
    private var help = false

    @Parameter(names = arrayOf("--output", "-o"),
            description = "",
            required = true)
    private var outputPath: String? = null

    /**
     * The entry point for the application.
     *
     * @param args The list of application arguments.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val jc = JCommander(this)
        jc.parse(*args)
        jc.programName = "downloader"

        if (help || provenanceFilePath == null) {
            jc.usage()
            exitProcess(1)
        }

        val provenanceFile = File(provenanceFilePath)
        require(provenanceFile.isFile) {
            "Provided path is not a file: ${provenanceFile.absolutePath}"
        }

        val mapper = when {
            provenanceFile.name.endsWith(OutputFormat.JSON.fileEnding) -> jsonMapper
            provenanceFile.name.endsWith(OutputFormat.YAML.fileEnding) -> yamlMapper
            else -> throw IllegalArgumentException("Provided input file is neither JSON or YAML.")
        }

        val outputDirectory = File(outputPath)
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        } else require(outputDirectory.isDirectory) {
            "Output directory is not a directory: ${outputDirectory.absolutePath}"
        }

        val scanResult = mapper.readValue(provenanceFile, ScanResult::class.java)

        scanResult.packages.forEach {
            val print = fun(string: String) = println("${it.identifier}: $string")

            val targetDir = File(outputDirectory, "${it.name}/${it.version}") // TODO: add namespace to path
            targetDir.mkdirs()
            print("Download source code to ${targetDir.absolutePath}")

            if (!it.normalizedVcsUrl.isNullOrBlank()) {
                print("Try to download from VCS: ${it.normalizedVcsUrl}")
                if (it.vcsUrl != it.normalizedVcsUrl) {
                    print("URL was normalized, original URL was ${it.vcsUrl}")
                }
                if (it.vcsRevision.isNullOrEmpty()) {
                    print("WARNING: No VCS revision provided, downloaded source code does likely not match version " +
                            it.version)
                } else {
                    print("Download revision ${it.vcsRevision}")
                }
                val applicableVcs = mutableListOf<VersionControlSystem>()
                if (!it.vcsProvider.isNullOrEmpty()) {
                    print("Detect VCS from provider name ${it.vcsProvider}")
                    applicableVcs.addAll(VERSION_CONTROL_SYSTEMS.filter { vcs ->
                        vcs.isApplicableProvider(it.vcsProvider!!)
                    })
                }
                if (applicableVcs.isEmpty()) {
                    print("Could not find VCS provider or no provider defined, try to detect provider from URL " +
                            "${it.normalizedVcsUrl}")
                    applicableVcs.addAll(VERSION_CONTROL_SYSTEMS.filter { vcs ->
                        vcs.isApplicableUrl(it.normalizedVcsUrl!!)
                    })
                }
                when {
                    applicableVcs.isEmpty() ->
                        print("ERROR: Could not find applicable VCS")
                        // TODO: Decide if we want to do a trial-and-error with all available VCS here.
                    applicableVcs.size > 1 ->
                        print("ERROR: Found multiple applicable VCS: ${applicableVcs.joinToString()}")
                    else -> {
                        val vcs = applicableVcs.first()
                        print("Use ${vcs.javaClass.simpleName}")
                        try {
                            vcs.download(it.normalizedVcsUrl!!, it.vcsRevision, targetDir)
                            print("Downloaded source code to ${targetDir.absolutePath}")
                        } catch (e: IllegalArgumentException) {
                            print("ERROR: Could not download source code: ${e.message}")
                        }
                    }
                }
            } else {
                print("No VCS URL provided")
                // TODO: This should also be tried if the VCS checkout does not work.
                print("Try to download source package: ...")
                // TODO: Implement downloading of source package.
                print("ERROR: No source package URL provided")
            }
        }
    }

}
