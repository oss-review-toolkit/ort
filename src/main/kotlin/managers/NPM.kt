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
import com.here.provenanceanalyzer.parseJsonProcessOutput

import java.io.File
import java.io.IOException

object NPM : PackageManager(
        "https://www.npmjs.com/",
        "JavaScript",
        listOf("package.json")
) {
    val npm: String
    val yarn: String

    init {
        if (OS.isWindows) {
            npm = "npm.cmd"
            yarn = "yarn.cmd"
        } else {
            npm = "npm"
            yarn = "yarn"
        }
    }

    override fun resolveDependencies(definitionFiles: List<File>): Map<File, Dependency> {
        val result = mutableMapOf<File, Dependency>()

        definitionFiles.forEach { definitionFile ->
            val parent = definitionFile.parentFile

            val modulesDir = File(parent, "node_modules")
            if (modulesDir.isDirectory) {
                throw IllegalArgumentException("'$modulesDir' directory already exists.")
            }

            result[definitionFile] = if (File(parent, "yarn.lock").isFile) {
                installDependencies(parent, yarn)
            } else {
                installDependencies(parent, npm)
            }
        }

        return result
    }

    /**
     * Resolve dependencies using the given package manager command. Supports detection of production and development scope.
     */
    fun installDependencies(workingDir: File, managerCommand: String): Dependency {
        // Install all NPM dependencies to enable NPM to list dependencies.
        val install = ProcessCapture(workingDir, managerCommand, "install")
        if (install.exitValue() != 0) {
            throw IOException("'$managerCommand install' failed with exit code ${install.exitValue()}: ${install.stderr()}")
        }

        return parseInstalledDependencies(workingDir)
    }

    private fun parseInstalledDependencies(parent: File): Dependency {
        val modulesDir = File(parent, "node_modules")

        // Get all production dependencies.
        val prodJson = parseJsonProcessOutput(parent, npm, "list", "--json", "--only=prod") as JsonObject
        val prodDependencies = if (prodJson.contains("dependencies")) {
            parseNpmDependencies(modulesDir, prodJson["dependencies"].obj, "production")
        } else {
            listOf()
        }

        // Get all dev dependencies.
        val devJson = parseJsonProcessOutput(parent, npm, "list", "--json", "--only=dev") as JsonObject
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
}
