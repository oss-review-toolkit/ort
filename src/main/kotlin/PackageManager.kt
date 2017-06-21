package com.here.provenanceanalyzer

import com.here.provenanceanalyzer.model.Dependency

import java.nio.file.FileSystems
import java.nio.file.Path

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
    val globForDefinitionFiles = FileSystems.getDefault().getPathMatcher("glob:**/{" + pathsToDefinitionFiles.joinToString(",") + "}")!!

    /**
     * Return the Java class name to make JCommander display a proper name in list parameters of this custom type.
     */
    override fun toString(): String {
        return javaClass.name
    }

    /**
     * Return a tree of resolved dependencies (not necessarily declared dependencies, in case conflicts were resolved)
     * for each provided path.
     */
    abstract fun resolveDependencies(definitionFiles: List<Path>): Map<Path, Dependency>
}
