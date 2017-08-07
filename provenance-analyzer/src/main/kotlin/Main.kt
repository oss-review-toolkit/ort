package com.here.provenanceanalyzer

import com.beust.jcommander.IStringConverter
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter

import com.here.provenanceanalyzer.model.OutputFormat
import com.here.provenanceanalyzer.model.jsonMapper
import com.here.provenanceanalyzer.model.yamlMapper

import java.io.File
import java.nio.file.Files
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

import kotlin.system.exitProcess

@Suppress("UnsafeCast")
internal val log = org.slf4j.LoggerFactory.getLogger({}.javaClass) as ch.qos.logback.classic.Logger

/**
 * The main entry point of the application.
 */
object Main {
    private class PackageManagerListConverter : IStringConverter<List<PackageManager>> {
        override fun convert(managers: String): List<PackageManager> {
            // Map lower-cased package manager class names to their instances.
            val packageManagerNames = packageManagers.associateBy { it.javaClass.simpleName.toLowerCase() }

            // Determine active package managers.
            val names = managers.toLowerCase().split(",")
            return names.mapNotNull { packageManagerNames[it] }
        }
    }

    @Parameter(names = arrayOf("--package-managers", "-m"),
            description = "A list of package managers to activate.",
            listConverter = PackageManagerListConverter::class,
            order = 0)
    private var packageManagers: List<PackageManager> = PACKAGE_MANAGERS

    @Parameter(names = arrayOf("--debug"),
            description = "Enable debug logging and keep temporary files.",
            order = 0)
    private var debug = false

    @Parameter(names = arrayOf("--help", "-h"),
            description = "Display the command line help.",
            help = true,
            order = 100)
    private var help = false

    @Parameter(description = "project path(s)")
    private var projectPaths: List<String>? = null

    @Parameter(names = arrayOf("--output-format", "-f"),
            description = "The data format used for dependency information.")
    private var outputFormat: OutputFormat = OutputFormat.YAML

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
        jc.programName = "pran"

        if (debug) {
            log.level = ch.qos.logback.classic.Level.DEBUG
        }

        if (help) {
            jc.usage()
            exitProcess(1)
        }

        if (projectPaths == null) {
            log.error("Please specify at least one project path.")
            exitProcess(2)
        }

        println("The following package managers are activated:")
        println("\t" + packageManagers.map { it.javaClass.simpleName }.joinToString(", "))

        // Map of paths managed by the respective package manager.
        val managedProjectPaths = mutableMapOf<PackageManager, MutableList<File>>()

        @Suppress("UnsafeCallOnNullableType")
        projectPaths!!.forEach { projectPath ->
            val absolutePath = File(projectPath).absoluteFile
            println("Scanning project path '$absolutePath'.")

            if (packageManagers.size == 1 && absolutePath.isFile) {
                // If only one package manager is activated, treat given paths to files as definition files for that
                // package manager despite their name.
                managedProjectPaths.getOrPut(packageManagers.first()) { mutableListOf() }.add(absolutePath)
            } else {
                Files.walkFileTree(absolutePath.toPath(), object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attributes: BasicFileAttributes): FileVisitResult {
                        dir.toFile().listFiles().forEach { file ->
                            packageManagers.forEach { manager ->
                                if (manager.globForDefinitionFiles.matches(file.toPath())) {
                                    managedProjectPaths.getOrPut(manager) { mutableListOf() }.add(file)
                                }
                            }
                        }
                        return FileVisitResult.CONTINUE
                    }
                })
            }
        }

        managedProjectPaths.forEach { manager, paths ->
            println("$manager projects found in:")
            println(paths.map { "\t$it" }.joinToString("\n"))

            val mapper = when (outputFormat) {
                OutputFormat.JSON -> jsonMapper
                OutputFormat.YAML -> yamlMapper
            }

            val userDir = File(System.getProperty("user.dir"))

            // Print the list of dependencies.
            val results = manager.resolveDependencies(paths)
            results.forEach { definitionFile, scanResult ->
                val outputFile = File(definitionFile.parent, "provenance${outputFormat.fileEnding}")
                println("Writing results for ${definitionFile.toRelativeString(userDir)} to " +
                        "${outputFile.toRelativeString(userDir)}.")
                mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, scanResult)
            }
        }
    }
}
