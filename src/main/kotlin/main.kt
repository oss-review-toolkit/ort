import com.beust.jcommander.IStringConverter
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter

import java.util.ArrayList

import kotlin.system.exitProcess

abstract class PackageManager {
    // Returns the URL to the package manager's homepage.
    abstract fun homepageUrl(): String

    // Returns the name of the programming language this package manager is primarily used with.
    abstract fun primaryLanguage(): String

    // Returns a prioritized list of glob patterns of definition files supported by this package manager.
    abstract fun pathsToDefinitionFiles(): List<String>

    override fun toString(): String {
        return javaClass.name
    }
}

object Bower: PackageManager() {
    override fun homepageUrl(): String = "https://bower.io/"

    override fun primaryLanguage(): String = "JavaScript"

    override fun pathsToDefinitionFiles(): List<String> {
        return arrayListOf("bower.json")
    }
}

object Bundler: PackageManager() {
    override fun homepageUrl(): String = "http://bundler.io/"

    override fun primaryLanguage(): String = "Ruby"

    override fun pathsToDefinitionFiles(): List<String> {
        return arrayListOf("Gemfile.lock", "Gemfile")
    }
}

object CocoaPods: PackageManager() {
    override fun homepageUrl(): String = "https://cocoapods.org/"

    override fun primaryLanguage(): String = "Objective-C"

    override fun pathsToDefinitionFiles(): List<String> {
        return arrayListOf("Podfile.lock", "Podfile")
    }
}

object Godep: PackageManager() {
    override fun homepageUrl(): String = "https://godoc.org/github.com/tools/godep"

    override fun primaryLanguage(): String = "Go"

    override fun pathsToDefinitionFiles(): List<String> {
        return arrayListOf("Godeps/Godeps.json")
    }
}

object Gradle: PackageManager() {
    override fun homepageUrl(): String = "https://gradle.org/"

    override fun primaryLanguage(): String = "Java"

    override fun pathsToDefinitionFiles(): List<String> {
        return arrayListOf("build.gradle")
    }
}

object Maven: PackageManager() {
    override fun homepageUrl(): String = "https://maven.apache.org/"

    override fun primaryLanguage(): String = "Java"

    override fun pathsToDefinitionFiles(): List<String> {
        return arrayListOf("pom.xml", "build.sbt", "build.scala")
    }
}

object NPM: PackageManager() {
    override fun homepageUrl(): String = "https://www.npmjs.com/"

    override fun primaryLanguage(): String = "JavaScript"

    override fun pathsToDefinitionFiles(): List<String> {
        return arrayListOf("package.json")
    }
}

object PIP: PackageManager() {
    override fun homepageUrl(): String = "https://pip.pypa.io/"

    override fun primaryLanguage(): String = "Python"

    override fun pathsToDefinitionFiles(): List<String> {
        return arrayListOf("setup.py", "requirements*.txt")
    }
}

object ProvenanceAnalzyer {
    // Prioritized list of package managers.
    val PACKAGE_MANAGERS = arrayListOf(
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

        for (projectPath in projectPaths.orEmpty()) {
            println("Scanning project path '$projectPath'.")
        }
    }
}
