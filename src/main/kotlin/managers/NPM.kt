package com.here.provenanceanalyzer.managers

import com.github.salomonbrys.kotson.contains
import com.github.salomonbrys.kotson.forEach
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.Gson
import com.google.gson.JsonObject

import com.here.provenanceanalyzer.model.Dependency
import com.here.provenanceanalyzer.OS
import com.here.provenanceanalyzer.PackageManager
import com.here.provenanceanalyzer.ProcessCapture

import java.io.File
import java.io.IOException

object NPM : PackageManager(
        "https://www.npmjs.com/",
        "JavaScript",
        listOf("package.json")
) {
    private val npm = if (OS.isWindows) "npm.cmd" else "npm"

    override fun resolveDependencies(definitionFiles: List<File>): Map<File, Dependency> {
        val result = mutableMapOf<File, Dependency>()

        definitionFiles.forEach { definitionFile ->
            val parent = definitionFile.parentFile

            val modulesDir = File(parent, "node_modules")
            if (modulesDir.isDirectory) {
                throw IllegalArgumentException("node_modules directory already exists.")
            }

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
        // Install all NPM dependencies to enable NPM to list dependencies.
        val install = ProcessCapture(parent, npm, "install")
        if (install.exitValue() != 0) {
            throw IOException("npm install failed with exit code ${install.exitValue()}.")
        }

        return parseInstalledDependencies(parent)
    }

    private fun parseInstalledDependencies(parent: File): Dependency {
        val modulesDir = File(parent, "node_modules")

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
            val packageFile = File(modulesDir, "$key/package.json")
            if (packageFile.isFile) {
                val packageJson = Gson().fromJson<JsonObject>(packageFile.readText())
                if (packageJson.contains("repository")) {
                    val repository = packageJson["repository"]

                    // For some packages "yarn install" generates a non-conforming shortcut repository entry like this:
                    // "repository": "git://..."
                    // For details about the correct format see: https://docs.npmjs.com/files/package.json#repository
                    scm = if (repository.isJsonObject) packageJson["repository"]["url"].string else repository.string
                }
            }

            val dependency = Dependency(artifact = key, version = version, dependencies = dependencies, scope = scope,
                    scm = scm)
            result.add(dependency)
        }
        return result
    }

    /**
     * Resolve dependencies using yarn. Supports detection of production and development scope.
     */
    fun resolveYarnDependencies(parent: File): Dependency {
        // Install all NPM dependencies to enable NPM to list dependencies.
        val yarn = if (OS.isWindows) "yarn.cmd" else "yarn"
        val install = ProcessCapture(parent, yarn, "install")
        if (install.exitValue() != 0) {
            throw IOException("yarn install failed with exit code ${install.exitValue()}.")
        }

        return parseInstalledDependencies(parent)
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
