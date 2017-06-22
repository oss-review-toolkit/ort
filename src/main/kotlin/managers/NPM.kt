package com.here.provenanceanalyzer.managers

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.contains
import com.github.salomonbrys.kotson.forEach
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.nullArray
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject

import com.here.provenanceanalyzer.model.Dependency
import com.here.provenanceanalyzer.OS
import com.here.provenanceanalyzer.PackageManager
import com.here.provenanceanalyzer.ProcessCapture

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
        val jsonObject = Gson().fromJson<JsonObject>(lockfile.readText())

        val projectName = jsonObject["name"].string
        val projectVersion = jsonObject["version"].string
        val projectDependencies = jsonObject["dependencies"].obj

        val dependencies = mutableListOf<Dependency>()
        projectDependencies.forEach { name, versionObject ->
            val version = versionObject["version"].string
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
        val process = ProcessCapture(parent, command, "list", "--json", "--no-progress")
        if (process.exitValue() != 0) {
            throw IOException("Yarn failed with exit code ${process.exitValue()}.")
        }
        val jsonString = process.stdout()
        val jsonObject = Gson().fromJson<JsonObject>(jsonString)
        val jsonDependencies = jsonObject["data"]["trees"].array
        val dependencies = parseYarnDependencies(jsonDependencies)
        // The "todo"s below will be replaced once parsing of package.json is implemented.
        return Dependency(artifact = "todo", version = "todo", dependencies = dependencies,
                scope = "production")
    }

    private fun parseYarnDependencies(jsonDependencies: JsonArray): List<Dependency> {
        val result = mutableListOf<Dependency>()
        jsonDependencies.forEach { jsonDependency ->
            val data = jsonDependency["name"].string.split("@")
            val dependencies = if (jsonDependency.obj.contains("children")) {
                parseYarnDependencies(jsonDependency["children"].array)
            } else {
                listOf()
            }
            val dependency = Dependency(artifact = data[0], version = data[1], dependencies = dependencies,
                    scope = "production")
            result.add(dependency)
        }
        return result
    }
}
