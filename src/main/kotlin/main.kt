import com.beust.jcommander.IStringConverter
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter

import java.nio.file.Files
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

import kotlin.system.exitProcess

import mu.KotlinLogging

/**
 * A class representing a package manager that handles software dependencies.
 *
 * @property homepageUrl The URL to the package manager's homepage.
 * @property primaryLanguage The name of the programming language this package manager is primarily used with.
 * @property pathsToDefinitionFiles A prioritized list of glob patterns of definition files supported by this package manager.
 *
 */
abstract class PackageManager(
    val homepageUrl: String,
    val primaryLanguage: String,
    val pathsToDefinitionFiles: List<String>
) {
    // Create a recursive glob matcher for all definition files.
    val globForDefinitionFiles = FileSystems.getDefault().getPathMatcher("glob:**/{" + pathsToDefinitionFiles.joinToString(",") + "}")

    /**
     * Return the Java class name to make JCommander display a proper name in list parameters of this custom type.
     */
    override fun toString(): String {
        return javaClass.name
    }
}

object Bower: PackageManager(
    "https://bower.io/",
    "JavaScript",
    listOf("bower.json")
)

object Bundler: PackageManager(
    "http://bundler.io/",
    "Ruby",
    listOf("Gemfile.lock", "Gemfile")
)

object CocoaPods: PackageManager(
    "https://cocoapods.org/",
    "Objective-C",
    listOf("Podfile.lock", "Podfile")
)

object Godep: PackageManager(
    "https://godoc.org/github.com/tools/godep",
    "Go",
    listOf("Godeps/Godeps.json")
)

object Gradle: PackageManager(
    "https://gradle.org/",
    "Java",
    listOf("build.gradle")
)

object Maven: PackageManager(
    "https://maven.apache.org/",
    "Java",
    listOf("pom.xml")
)

object NPM: PackageManager(
    "https://www.npmjs.com/",
    "JavaScript",
    listOf("package.json")
)

object PIP: PackageManager(
    "https://pip.pypa.io/",
    "Python",
    listOf("setup.py", "requirements*.txt")
)

object SBT: PackageManager(
    "http://www.scala-sbt.org/",
    "Scala",
    listOf("build.sbt", "build.scala")
)

object ProvenanceAnalyzer {
    private val logger = KotlinLogging.logger {}

    // Prioritized list of package managers.
    val PACKAGE_MANAGERS = listOf(
        Gradle,
        Maven,
        SBT,
        NPM,
        CocoaPods,
        Godep,
        Bower,
        PIP,
        Bundler
    )

    class PackageManagerListConverter: IStringConverter<List<PackageManager>> {
        override fun convert(managers: String): List<PackageManager> {
            // Map lower-cased package manager class names to their instances.
            val packageManagerNames = packageManagers.associateBy { it.javaClass.name.toLowerCase() }

            // Determine active package managers.
            val names = managers.toLowerCase().split(",")
            return names.mapNotNull { packageManagerNames[it] }
        }
    }

    @Parameter(names = arrayOf("--package-managers", "-m"), description = "A list of package managers to activate.", listConverter = PackageManagerListConverter::class, order = 0)
    var packageManagers: List<PackageManager> = PACKAGE_MANAGERS

    @Parameter(names = arrayOf("--help", "-h"), description = "Display the command line help.", help = true, order = 100)
    var help = false

    @Parameter(description = "project path(s)")
    var projectPaths: List<String>? = null

    @JvmStatic
    fun main(args: Array<String>) {
        val jc = JCommander(this)
        jc.parse(*args)
        jc.programName = "pran"

        if (help) {
            jc.usage()
            exitProcess(1)
        }

        if (projectPaths == null) {
            logger.error("Please specify at least one project path.")
            exitProcess(2)
        }

        println("The following package managers are activated:")
        println("\t" + packageManagers.map { it.javaClass.name }.joinToString(", "))

        // Map of paths managed by the respective package manager.
        val managedProjectPaths = HashMap<PackageManager, MutableList<Path>>()

        projectPaths!!.forEach { projectPath ->
            val absolutePath = Paths.get(projectPath).toAbsolutePath()
            println("Scanning project path '$absolutePath'.")

            if (packageManagers.size == 1 && Files.isRegularFile(absolutePath)) {
                // If only one package manager is activated, treat given paths to files as definition files for that
                // package manager despite their name.
                managedProjectPaths.getOrPut(packageManagers.first()) { mutableListOf() }.add(absolutePath)
            } else {
                Files.walkFileTree(absolutePath, object : SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                        packageManagers.forEach { manager ->
                            if (manager.globForDefinitionFiles.matches(file)) {
                                managedProjectPaths.getOrPut(manager) { mutableListOf() }.add(file)
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
        }
    }
}
