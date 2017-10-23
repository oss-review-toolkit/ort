package com.here.ort.analyzer

import ch.frankel.slf4k.*

import com.beust.jcommander.IStringConverter
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException

import com.here.ort.model.OutputFormat
import com.here.ort.util.jsonMapper
import com.here.ort.util.log
import com.here.ort.util.yamlMapper

import java.io.File
import java.nio.file.Files
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

import kotlin.system.exitProcess

/**
 * The main entry point of the application.
 */
object Main {
    private class PackageManagerConverter : IStringConverter<PackageManager> {
        companion object {
            // Map upper-cased package manager class names to their instances.
            val PACKAGE_MANAGER_NAMES = PackageManager.ALL.associateBy {
                it.javaClass.simpleName.toUpperCase()
            }
        }

        override fun convert(name: String): PackageManager {
            return PACKAGE_MANAGER_NAMES[name.toUpperCase()] ?:
                    throw ParameterException("Package managers must be contained in ${PackageManager.ALL}.")
        }
    }

    @Parameter(description = "A list of package managers to activate.",
            names = arrayOf("--package-managers", "-m"),
            converter = PackageManagerConverter::class,
            order = 0)
    private var packageManagers = PackageManager.ALL

    @Parameter(description = "The project directory to scan.",
            names = arrayOf("--input-dir", "-i"),
            required = true,
            order = 0)
    @Suppress("LateinitUsage")
    private lateinit var inputDir: File

    @Parameter(description = "The directory to write dependency information to.",
            names = arrayOf("--output-dir", "-o"),
            required = true,
            order = 0)
    @Suppress("LateinitUsage")
    private lateinit var outputDir: File

    @Parameter(description = "The data format used for dependency information.",
            names = arrayOf("--output-format", "-f"),
            order = 0)
    private var outputFormat: OutputFormat = OutputFormat.YAML

    @Parameter(description = "Ignore versions of required tools. NOTE: This may lead to erroneous results.",
            names = arrayOf("--ignore-versions"),
            order = 0)
    var ignoreVersions = false

    @Parameter(description = "Allow dynamic versions of dependencies. This can result in unstable results when " +
            "dependencies use version ranges. This option only affects package managers that support lock files, " +
            "like NPM.",
            names = arrayOf("--allow-dynamic-versions"),
            order = 0)
    var allowDynamicVersions = false

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
    @Suppress("ComplexMethod")
    @JvmStatic
    fun main(args: Array<String>) {
        val jc = JCommander(this)
        jc.parse(*args)
        jc.programName = "analyzer"

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

        val absoluteOutputPath = outputDir.absoluteFile
        if (absoluteOutputPath.exists()) {
            log.error { "The output directory '$absoluteOutputPath' must not exist yet." }
            exitProcess(2)
        }

        println("The following package managers are activated:")
        println("\t" + packageManagers.joinToString(", ") { it.javaClass.simpleName })

        // Map of paths managed by the respective package manager.
        val managedProjectPaths = mutableMapOf<PackageManager, MutableList<File>>()

        val absoluteProjectPath = inputDir.absoluteFile
        println("Scanning project path:\n\t$absoluteProjectPath")

        if (packageManagers.size == 1 && absoluteProjectPath.isFile) {
            // If only one package manager is activated, treat given paths to files as definition files for that
            // package manager despite their name.
            managedProjectPaths.getOrPut(packageManagers.first()) { mutableListOf() }.add(absoluteProjectPath)
        } else {
            Files.walkFileTree(absoluteProjectPath.toPath(), object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attributes: BasicFileAttributes): FileVisitResult {
                    val filesInDir = dir.toFile().listFiles()

                    packageManagers.forEach { manager ->
                        val matches = manager.matchersForDefinitionFiles.mapNotNull { glob ->
                            filesInDir.find { file ->
                                glob.matches(file.toPath())
                            }
                        }

                        if (matches.isNotEmpty()) {
                            managedProjectPaths.getOrPut(manager) { mutableListOf() }.add(matches.first())
                        }
                    }

                    return FileVisitResult.CONTINUE
                }
            })
        }

        // Print a summary of all projects found per package manager.
        managedProjectPaths.forEach { manager, paths ->
            println("$manager projects found in:")
            println(paths.joinToString("\n") {
                "\t${it.toRelativeString(absoluteProjectPath)}"
            })
        }

        // Resolve dependencies per package manager.
        managedProjectPaths.forEach { manager, paths ->
            val mapper = when (outputFormat) {
                OutputFormat.JSON -> jsonMapper
                OutputFormat.YAML -> yamlMapper
            }

            // Print the list of dependencies.
            val results = manager.resolveDependencies(absoluteProjectPath, paths)
            results.forEach { definitionFile, analyzerResult ->
                // Mirror the directory structure from the project in the output.
                val outputDir = File(absoluteOutputPath,
                        definitionFile.parentFile.toRelativeString(absoluteProjectPath))
                if (outputDir.mkdirs()) {
                    val outputFile = File(outputDir, definitionFile.name.replace('.', '-') +
                            "-dependencies." + outputFormat.fileEnding)
                    println("Writing results for\n\t$definitionFile\nto\n\t$outputFile")
                    mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, analyzerResult)
                    println("done.")
                } else {
                    log.error { "Unable to create output directory '$outputDir'." }
                }
            }
        }
    }
}
