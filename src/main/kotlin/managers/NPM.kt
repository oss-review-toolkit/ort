package com.here.provenanceanalyzer.managers

import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import com.beust.klaxon.array
import com.beust.klaxon.obj
import com.beust.klaxon.string

import com.here.provenanceanalyzer.model.Dependency
import com.here.provenanceanalyzer.OS
import com.here.provenanceanalyzer.PackageManager

import java.io.File
import java.io.IOException
import java.nio.file.Path

object NPM : PackageManager(
        "https://www.npmjs.com/",
        "JavaScript",
        listOf("package.json")
) {
    override fun resolveDependencies(definitionFiles: List<Path>): Map<Path, Dependency> {
        val result = mutableMapOf<Path, Dependency>()

        definitionFiles.forEach { definitionFile ->
            val parent = definitionFile.parent.toFile()
            val shrinkwrapLockfile = File(parent, "npm-shrinkwrap.json")
            result[definitionFile] = when {
                File(parent, "yarn.lock").isFile ->
                    resolveYarnDependencies(parent)
                shrinkwrapLockfile.isFile ->
                    resolveShrinkwrapDependencies(shrinkwrapLockfile)
                else ->
                    resolveNpmDependencies(parent)
            }
        }

        return result
    }

    fun resolveNpmDependencies(parent: File): Dependency {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    /**
     * Resolve dependencies using the npm-shrinkwrap.json file. Does not support detection of scope, all dependencies
     * are marked as production dependencies. Because the shrinkwrap file does not contain information about the
     * dependency tree all dependencies are added as top-level dependencies.
     */
    fun resolveShrinkwrapDependencies(lockfile: File): Dependency {
        val jsonObject = Parser().parse(lockfile.inputStream()) as JsonObject

        val projectName = jsonObject.string("name")!!
        val projectVersion = jsonObject.string("version")!!
        val projectDependencies = jsonObject.obj("dependencies")!!

        val dependencies = mutableListOf<Dependency>()
        projectDependencies.forEach { name, versionObject ->
            val version = (versionObject as JsonObject).string("version")!!
            val dependency = Dependency(artifact = name, version = version, dependencies = listOf(),
                    scope = "production")
            dependencies.add(dependency)
        }
        return Dependency(artifact = projectName, version = projectVersion, dependencies = dependencies, scope = "production")
    }

    /**
     * Resolve dependencies using yarn. Does not support detection of scope, all dependencies are marked as production
     * dependencies.
     */
    fun resolveYarnDependencies(parent: File): Dependency {
        val command = if (OS.isWindows) "yarn.cmd" else "yarn"
        val process = ProcessBuilder(command, "list", "--json", "--no-progress").directory(parent).start()
        if (process.waitFor() != 0) {
            throw IOException("Yarn failed with exit code ${process.exitValue()}.")
        }
        val jsonObject = Parser().parse(process.inputStream) as JsonObject
        val data = jsonObject.obj("data")!!
        val jsonDependencies = data.array<JsonObject>("trees")!!
        val dependencies = parseYarnDependencies(jsonDependencies)
        // The "todo"s below will be replaced once parsing of package.json is implemented.
        return Dependency(artifact = "todo", version = "todo", dependencies = dependencies,
                scope = "production")
    }

    private fun parseYarnDependencies(jsonDependencies: JsonArray<JsonObject>): List<Dependency> {
        val result = mutableListOf<Dependency>()
        jsonDependencies.forEach { jsonDependency ->
            val data = jsonDependency.string("name")!!.split("@")
            val children = jsonDependency.array<JsonObject>("children")
            val dependencies = if (children != null) parseYarnDependencies(children) else listOf()
            val dependency = Dependency(artifact = data[0], version = data[1], dependencies = dependencies,
                    scope = "production")
            result.add(dependency)
        }
        return result
    }
}
