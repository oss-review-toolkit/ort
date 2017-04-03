import com.beust.jcommander.IStringConverter
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter

import java.util.ArrayList

import kotlin.system.exitProcess

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
    listOf("pom.xml", "build.sbt", "build.scala")
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

object ProvenanceAnalzyer {
    // Prioritized list of package managers.
    val PACKAGE_MANAGERS = listOf(
        Gradle,
        Maven,
        NPM,
        CocoaPods,
        Godep,
        Bower,
        PIP,
        Bundler
    )

    class PackageManagerListConverter: IStringConverter<List<PackageManager>> {
        override fun convert(managers: String): List<PackageManager> {
            val activePackageManagers = ArrayList<PackageManager>()

            // Map lower-cased package manager class names to their instances.
            val packageManagerNames = packageManagers.associateBy { it.javaClass.name.toLowerCase() }

            val names = managers.toLowerCase().split(",")
            for (name in names) {
                val manager = packageManagerNames.get(name)
                if (manager != null) {
                    activePackageManagers.add(manager)
                }
            }

            return activePackageManagers
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
        val jc = JCommander(this, *args)
        jc.programName = "pran"

        if (help) {
            jc.usage()
            exitProcess(1)
        }

        if (projectPaths == null) {
            println("Please specify at least one project path.")
            exitProcess(2)
        }

        println("The following package managers are activated:")
        println(packageManagers.map { it.javaClass.name }.joinToString(", "))

        projectPaths!!.forEach { projectPath ->
            println("Scanning project path '$projectPath'.")
        }
    }
}
