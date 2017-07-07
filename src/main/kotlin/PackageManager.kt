package com.here.provenanceanalyzer

import com.here.provenanceanalyzer.managers.*
import com.here.provenanceanalyzer.model.Dependency

import java.io.File
import java.nio.file.FileSystems

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

/**
 * A class representing a package manager that handles software dependencies.
 *
 * @property homepageUrl The URL to the package manager's homepage.
 * @property primaryLanguage The name of the programming language this package manager is primarily used with.
 * @property pathsToDefinitionFiles A prioritized list of glob patterns of definition files supported by this package
 *                                  manager.
 *
 */
abstract class PackageManager(
        val homepageUrl: String,
        val primaryLanguage: String,
        val pathsToDefinitionFiles: List<String>
) {
    /**
     * The recursive glob matcher for all definition files.
     */
    val globForDefinitionFiles = FileSystems.getDefault().getPathMatcher(
            "glob:**/{" + pathsToDefinitionFiles.joinToString(",") + "}")!!

    /**
     * Return the Java class name to make JCommander display a proper name in list parameters of this custom type.
     */
    override fun toString(): String {
        return javaClass.simpleName
    }

    /**
     * Return the name of the package manager's command line application. As the preferred command might depend on the
     * working directory it needs to be provided.
     */
    abstract fun command(workingDir: File): String

    /**
     * Return a tree of resolved dependencies (not necessarily declared dependencies, in case conflicts were resolved)
     * for each provided path.
     */
    abstract fun resolveDependencies(definitionFiles: List<File>): Map<File, Dependency>
}
