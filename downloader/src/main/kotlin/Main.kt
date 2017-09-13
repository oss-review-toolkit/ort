package com.here.provenanceanalyzer.downloader

import com.beust.jcommander.IStringConverter
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter

import com.here.provenanceanalyzer.model.OutputFormat
import com.here.provenanceanalyzer.model.Package
import com.here.provenanceanalyzer.model.ScanResult
import com.here.provenanceanalyzer.util.jsonMapper
import com.here.provenanceanalyzer.util.yamlMapper

import java.io.File

import kotlin.system.exitProcess

/**
 * The main entry point of the application.
 */
object Main {

    enum class DataEntity {
        PACKAGES,
        PROJECT
    }

    private class DataEntityListConverter : IStringConverter<List<DataEntity>> {
        override fun convert(sources: String): List<DataEntity> {
            return sources.toUpperCase().split(",").map { DataEntity.valueOf(it) }
        }
    }

    @Parameter(description = "provenance data file path")
    private var provenanceFilePath: String? = null

    @Parameter(names = arrayOf("--help", "-h"),
            description = "Display the command line help.",
            help = true,
            order = 100)
    private var help = false

    @Parameter(names = arrayOf("--output", "-o"),
            description = "The output directory to download the source code to.",
            required = true,
            order = 0)
    private var outputPath: String? = null

    @Parameter(names = arrayOf("--entities", "-e"),
            description = "The data entities from the provenance data file to download.",
            listConverter = DataEntityListConverter::class,
            order = 0)
    private var entities: List<DataEntity> = DataEntity.values().asList()

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
            else -> throw IllegalArgumentException("Provided input file is neither JSON nor YAML.")
        }

        val outputDirectory = File(outputPath)
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        } else require(outputDirectory.isDirectory) {
            "Output directory is not a directory: ${outputDirectory.absolutePath}"
        }

        val scanResult = mapper.readValue(provenanceFile, ScanResult::class.java)

        if (entities.contains(DataEntity.PROJECT)) {
            val project = scanResult.project
            val projectPackage = Package("", "", project.name, "", project.version, project.homepageUrl, "",
                    "", "", project.vcsPath, project.vcsProvider, project.vcsUrl, project.revision)
            download(projectPackage, outputDirectory)
        }

        if (entities.contains(DataEntity.PACKAGES)) {
            scanResult.packages.forEach {
                download(it, outputDirectory)
            }
        }
    }

    private fun download(target: Package, outputDirectory: File) {
        val p = fun(string: String) = println("${target.identifier}: $string")

        val targetDir = File(outputDirectory, "${target.name}/${target.version}") // TODO: add namespace to path
        targetDir.mkdirs()
        p("Download source code to ${targetDir.absolutePath}")

        if (!target.normalizedVcsUrl.isNullOrBlank()) {
            p("Try to download from VCS: ${target.normalizedVcsUrl}")
            if (target.vcsUrl != target.normalizedVcsUrl) {
                p("URL was normalized, original URL was ${target.vcsUrl}")
            }
            if (target.vcsRevision.isNullOrEmpty()) {
                p("WARNING: No VCS revision provided, downloaded source code does likely not match version " +
                        target.version)
            } else {
                p("Download revision ${target.vcsRevision}")
            }
            val applicableVcs = mutableListOf<VersionControlSystem>()
            if (!target.vcsProvider.isNullOrEmpty()) {
                p("Detect VCS from provider name ${target.vcsProvider}")
                applicableVcs.addAll(VERSION_CONTROL_SYSTEMS.filter { vcs ->
                    @Suppress("UnsafeCallOnNullableType")
                    vcs.isApplicableProvider(target.vcsProvider!!)
                })
            }
            if (applicableVcs.isEmpty()) {
                p("Could not find VCS provider or no provider defined, try to detect provider from URL " +
                        "${target.normalizedVcsUrl}")
                applicableVcs.addAll(VERSION_CONTROL_SYSTEMS.filter { vcs ->
                    @Suppress("UnsafeCallOnNullableType")
                    vcs.isApplicableUrl(target.normalizedVcsUrl!!)
                })
            }
            when {
                applicableVcs.isEmpty() ->
                    p("ERROR: Could not find applicable VCS")
            // TODO: Decide if we want to do a trial-and-error with all available VCS here.
                applicableVcs.size > 1 ->
                    p("ERROR: Found multiple applicable VCS: ${applicableVcs.joinToString()}")
                else -> {
                    val vcs = applicableVcs.first()
                    p("Use ${vcs.javaClass.simpleName}")
                    try {
                        @Suppress("UnsafeCallOnNullableType")
                        vcs.download(target.normalizedVcsUrl!!, target.vcsRevision, target.vcsPath, targetDir)
                        p("Downloaded source code to ${targetDir.absolutePath}")
                    } catch (e: IllegalArgumentException) {
                        p("ERROR: Could not download source code: ${e.message}")
                    }
                }
            }
        } else {
            p("No VCS URL provided")
            // TODO: This should also be tried if the VCS checkout does not work.
            p("Try to download source package: ...")
            // TODO: Implement downloading of source package.
            p("ERROR: No source package URL provided")
        }
    }

}
