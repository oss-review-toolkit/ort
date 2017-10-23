package com.here.ort.analyzer

import ch.frankel.slf4k.*

import com.here.ort.analyzer.managers.*
import com.here.ort.model.AnalyzerResult
import com.here.ort.util.log

import java.io.File
import java.nio.file.FileSystems

import kotlin.system.measureTimeMillis

typealias ResolutionResult = MutableMap<File, AnalyzerResult>

/**
 * A class representing a package manager that handles software dependencies.
 *
 * @property homepageUrl The URL to the package manager's homepage.
 * @property primaryLanguage The name of the programming language this package manager is primarily used with.
 * @property globsForDefinitionFiles A prioritized list of glob patterns of definition files supported by this package
 *                                   manager.
 *
 */
abstract class PackageManager(
        val homepageUrl: String,
        val primaryLanguage: String,
        private val globsForDefinitionFiles: List<String>
) {
    companion object {
        /**
         * The prioritized list of all available package managers. This needs to be initialized lazily to ensure the
         * referred objects, which derive from this class, exist.
         */
        val ALL by lazy {
            listOf(
                    Gradle,
                    // TODO: Maven,
                    // TODO: SBT,
                    NPM,
                    // TODO: CocoaPods,
                    // TODO: Godep,
                    // TODO: Bower,
                    PIP
                    // TODO: Bundler
            )
        }
    }

    /**
     * The glob matchers for all definition files.
     */
    val matchersForDefinitionFiles = globsForDefinitionFiles.map {
        FileSystems.getDefault().getPathMatcher("glob:**/" + it)
    }

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
    open fun resolveDependencies(projectDir: File, definitionFiles: List<File>): ResolutionResult {
        prepareResolution()

        val result = mutableMapOf<File, AnalyzerResult>()

        definitionFiles.forEach { definitionFile ->
            val workingDir = definitionFile.parentFile

            println("Resolving ${javaClass.simpleName} dependencies in '$workingDir'...")

            val elapsed = measureTimeMillis {
                resolveDependency(projectDir, workingDir, definitionFile, result)
            }

            log.info {
                "Resolving ${javaClass.simpleName} dependencies in '${workingDir.name}' took ${elapsed / 1000}s."
            }
        }

        return result
    }

    /**
     * Optional preparation step for dependency resolution, like checking for prerequisites.
     */
    protected open fun prepareResolution() {
        log.debug { "Resolving ${javaClass.simpleName} dependencies does not define any preparation." }
    }

    /**
     * Resolve dependencies for single [definitionFile], amending the [result].
     */
    protected open fun resolveDependency(projectDir: File, workingDir: File, definitionFile: File,
                                         result: ResolutionResult) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
