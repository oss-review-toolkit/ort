package com.here.ort.analyzer.managers

import Dependency
import DependencyTreeModel

import ch.frankel.slf4k.*

import com.here.ort.analyzer.Main
import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.ResolutionResult
import com.here.ort.model.AnalyzerResult
import com.here.ort.model.Package
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.Scope
import com.here.ort.util.OS
import com.here.ort.util.log

import org.gradle.tooling.BuildException
import org.gradle.tooling.GradleConnector

import java.io.File

import kotlin.system.measureTimeMillis

object Gradle : PackageManager(
        "https://gradle.org/",
        "Java",
        listOf("build.gradle")
) {
    val gradle: String
    val wrapper: String

    init {
        if (OS.isWindows) {
            gradle = "gradle.bat"
            wrapper = "gradlew.bat"
        } else {
            gradle = "gradle"
            wrapper = "gradlew"
        }
    }

    override fun command(workingDir: File): String {
        return if (File(workingDir, wrapper).isFile) wrapper else gradle
    }

    override fun resolveDependency(projectDir: File, workingDir: File, definitionFile: File, result: ResolutionResult) {
        val connection = GradleConnector
                .newConnector()
                .forProjectDirectory(workingDir)
                .connect()

        try {
            val initScriptFile = File.createTempFile("init", "gradle")
            initScriptFile.writeBytes(javaClass.classLoader.getResource("init.gradle").readBytes())

            val dependencyTreeModel = connection
                    .model(DependencyTreeModel::class.java)
                    .withArguments("--init-script", initScriptFile.absolutePath)
                    .get()

            if (!initScriptFile.delete()) {
                log.warn { "Init script file '${initScriptFile.absolutePath}' could not be deleted." }
            }

            val packages = mutableMapOf<String, Package>()
            val scopes = dependencyTreeModel.configurations.map { configuration ->
                val dependencies = configuration.dependencies.map { dependency ->
                    parseDependency(dependency, packages)
                }

                Scope(configuration.name, true, dependencies.toSortedSet())
            }

            val project = Project(javaClass.simpleName, "", dependencyTreeModel.name, "", emptyList(), "", "", "",
                    "", "", scopes)

            result.put(definitionFile, AnalyzerResult(project, packages.values.toSortedSet(), true))
        } catch (e: BuildException) {
            if (Main.stacktrace) {
                e.printStackTrace()
            }

            log.error { "Could not analyze '${definitionFile.absolutePath}': ${e.message}" }
        } finally {
            connection.close()
        }
    }

    private fun parseDependency(dependency: Dependency, packages: MutableMap<String, Package>): PackageReference {
        if (dependency.error == null) {
            // Only look for a package when there was no error resolving the dependency.
            packages.getOrPut("${dependency.groupId}:${dependency.artifactId}:${dependency.version}") {
                // TODO: add metadata for package
                Package(javaClass.simpleName, dependency.groupId, dependency.artifactId,
                        dependency.version, "", "", "", "", "", "", "", "", "")
            }
        }
        val transitiveDependencies = dependency.dependencies.map { parseDependency(it, packages) }
        return PackageReference(dependency.groupId, dependency.artifactId, dependency.version,
                transitiveDependencies.toSortedSet(), dependency.error?.let { listOf(it) } ?: emptyList())
    }

}
