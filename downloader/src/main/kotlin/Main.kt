package com.here.provenanceanalyzer.downloader

import ch.frankel.slf4k.*

import com.beust.jcommander.IStringConverter
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException

import com.here.provenanceanalyzer.model.OutputFormat
import com.here.provenanceanalyzer.model.Package
import com.here.provenanceanalyzer.model.ScanResult
import com.here.provenanceanalyzer.util.jsonMapper
import com.here.provenanceanalyzer.util.log
import com.here.provenanceanalyzer.util.safeMkdirs
import com.here.provenanceanalyzer.util.yamlMapper

import java.io.File
import java.io.IOException

import kotlin.system.exitProcess

/**
 * The main entry point of the application.
 */
object Main {

    enum class DataEntity {
        PACKAGES,
        PROJECT
    }

    val ALL_DATA_ENTITIES = DataEntity.values().asList()

    private class DataEntityConverter : IStringConverter<DataEntity> {
        override fun convert(name: String): DataEntity {
            try {
                return DataEntity.valueOf(name.toUpperCase())
            } catch (e: IllegalArgumentException) {
                throw ParameterException("Data entities must be contained in $ALL_DATA_ENTITIES.")
            }
        }
    }

    @Parameter(description = "The provenance data file to use.",
            names = arrayOf("--input-file", "-i"),
            required = true,
            order = 0)
    @Suppress("LateinitUsage")
    private lateinit var provenanceFilePath: String

    @Parameter(description = "The output directory to download the source code to.",
            names = arrayOf("--output", "-o"),
            required = true,
            order = 0)
    @Suppress("LateinitUsage")
    private lateinit var outputPath: String

    @Parameter(description = "The data entities from the provenance data file to download.",
            names = arrayOf("--entities", "-e"),
            converter = DataEntityConverter::class,
            order = 0)
    private var entities: List<DataEntity> = ALL_DATA_ENTITIES

    @Parameter(description = "Enable info logging.",
            names = arrayOf("--info"),
            order = 0)
    private var info = false

    @Parameter(description = "Enable debug logging and keep temporary files.",
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
        jc.programName = "downloader"

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

        val outputDirectory = File(outputPath).apply { safeMkdirs() }

        val scanResult = mapper.readValue(provenanceFile, ScanResult::class.java)

        val packages = mutableListOf<Package>()

        if (entities.contains(DataEntity.PROJECT)) {
            packages.add(scanResult.project.asPackage())
        }

        if (entities.contains(DataEntity.PACKAGES)) {
            packages.addAll(scanResult.packages)
        }

        packages.forEach { pkg ->
            try {
                download(pkg, outputDirectory)
            } catch (e: DownloadException) {
                log.error { "Could not download '${pkg.identifier}': ${e.message}" }
            }
        }

    }

    /**
     * Download the source code of the [target] package to a folder inside [outputDirectory]. The folder name is created
     * from the [name][Package.name] and [version][Package.version] of the [target] package.
     *
     * @param target The description of the package to download.
     * @param outputDirectory The parent directory to download the source code to.
     *
     * @return The directory containing the source code, or null if the source code could not be downloaded.
     */
    fun download(target: Package, outputDirectory: File): File {
        // TODO: return also SHA1 which was finally cloned
        val p = fun(string: String) = println("${target.identifier}: $string")

        // TODO: add namespace to path
        val targetDir = File(outputDirectory, "${target.name}/${target.version}").apply { safeMkdirs() }
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
                    // TODO: Decide if we want to do a trial-and-error with all available VCS here.
                    throw DownloadException("Could not find applicable VCS.")
                applicableVcs.size > 1 ->
                    throw DownloadException("Found multiple applicable VCS: ${applicableVcs.joinToString()}")
                else -> {
                    val vcs = applicableVcs.first()
                    p("Use ${vcs.javaClass.simpleName}")
                    try {
                        @Suppress("UnsafeCallOnNullableType")
                        vcs.download(target.normalizedVcsUrl!!, target.vcsRevision, target.vcsPath, targetDir)
                        p("Downloaded source code to ${targetDir.absolutePath}")
                        return targetDir
                    } catch (e: IOException) {
                        throw DownloadException("Could not download source code.", e)
                    }
                }
            }
        } else {
            p("No VCS URL provided")
            // TODO: This should also be tried if the VCS checkout does not work.
            p("Try to download source package: ...")
            // TODO: Implement downloading of source package.
            throw DownloadException("No source package URL provided.")
        }
    }

}
