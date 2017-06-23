package com.here.provenanceanalyzer.managers

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.contains
import com.github.salomonbrys.kotson.forEach
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
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
            result[definitionFile] = if (File(parent, "yarn.lock").isFile) {
                resolveYarnDependencies(parent)
            } else {
                resolveNpmDependencies(parent)
            }
        }

        return result
    }

    /**
     * Resolve dependencies using NPM. Supports detection of production and development scope.
     */
    fun resolveNpmDependencies(parent: File): Dependency {
        val modulesDir = File(parent, "node_modules")
        if (modulesDir.exists()) {
            throw IllegalArgumentException("node_modules directory already exists.")
        }

        val npm = if (OS.isWindows) "npm.cmd" else "npm"

        // Install all NPM dependencies to enable NPM to list dependencies.
        val install = ProcessCapture(parent, npm, "install")
        if (install.exitValue() != 0) {
            throw IOException("npm install failed with exit code ${install.exitValue()}.")
        }

        // Get all production dependencies.
        val prodJson = processToJson(parent, npm, "list", "--json", "--only=prod")
        val prodDependencies = if (prodJson.contains("dependencies")) {
            parseNpmDependencies(modulesDir, prodJson["dependencies"].obj, "production")
        } else {
            listOf()
        }

        // Get all dev dependencies.
        val devJson = processToJson(parent, npm, "list", "--json", "--only=dev")
        val devDependencies = if (devJson.contains("dependencies")) {
            parseNpmDependencies(modulesDir, devJson["dependencies"].obj, "development")
        } else {
            listOf()
        }

        // Delete node_modules folder to not pollute the scan.
        modulesDir.deleteRecursively()

        val artifact = prodJson["name"].string
        val version = prodJson["version"].string

        return Dependency(artifact = artifact, version = version, dependencies = prodDependencies + devDependencies,
                scope = "production")
    }

    private fun parseNpmDependencies(modulesDir: File, json: JsonObject, scope: String): List<Dependency> {
        val result = mutableListOf<Dependency>()
        json.forEach { key, jsonElement ->
            val version = jsonElement["version"].string
            val dependencies = if (jsonElement.obj.contains("dependencies")) {
                parseNpmDependencies(modulesDir, jsonElement["dependencies"].obj, scope)
            } else {
                listOf()
            }

            var scm: String? = null
            val packageFile = File(modulesDir, "${key}/package.json")
            if (packageFile.isFile) {
                val packageJson = Gson().fromJson<JsonObject>(packageFile.readText())
                if (packageJson.contains("repository")) {
                    scm = packageJson["repository"]["url"].string
                }
            }

            val dependency = Dependency(artifact = key, version = version, dependencies = dependencies, scope = scope,
                    scm = scm)
            result.add(dependency)
        }
        return result
    }

    /**
     * Resolve dependencies using yarn. Does not support detection of scope, all dependencies are marked as production
     * dependencies.
     */
    fun resolveYarnDependencies(parent: File): Dependency {
        val command = if (OS.isWindows) "yarn.cmd" else "yarn"
        val jsonObject = processToJson(parent, command, "list", "--json", "--no-progress")
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

    private fun processToJson(workingDir: File, vararg command: String): JsonObject {
        val process = ProcessCapture(workingDir, *command)
        if (process.exitValue() != 0) {
            throw IOException("${command.joinToString(" ")} failed with exit code ${process.exitValue()}")
        }
        val jsonString = process.stdout()
        return Gson().fromJson<JsonObject>(jsonString)
    }
}
