package com.here.provenanceanalyzer.managers

import ch.frankel.slf4k.*

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
import com.here.provenanceanalyzer.log
import com.here.provenanceanalyzer.parseJsonProcessOutput

import com.vdurmont.semver4j.Semver
import com.vdurmont.semver4j.Semver.SemverType

import java.io.File
import java.io.IOException

import kotlin.system.measureTimeMillis

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

    override fun command(workingDir: File): String {
        return if (File(workingDir, "yarn.lock").isFile) yarn else npm
    }

    override fun resolveDependencies(definitionFiles: List<File>): Map<File, Dependency> {
        val version = ProcessCapture(npm, "--version")
        if (version.exitValue() != 0) {
            throw IOException("Unable to determine the $npm version:\n${version.stderr()}")
        }

        val expectedVersion = Semver("5.1.0", SemverType.NPM)
        val actualVersion = Semver(version.stdout().trim(), SemverType.NPM)
        if (actualVersion != expectedVersion) {
            throw IOException(
                    "Unsupported $npm version ${actualVersion.value}, version ${expectedVersion.value} is required.")
        }

        val result = mutableMapOf<File, Dependency>()

        definitionFiles.forEach { definitionFile ->
            val parent = definitionFile.parentFile

            println("Resolving ${javaClass.simpleName} dependencies in '${parent.name}'...")

            val elapsed = measureTimeMillis {
                // Actually installing the dependencies is the easiest way to get the meta-data of all transitive
                // dependencies (i.e. their respective "package.json" files). As npm (and yarn) use a global cache,
                // the same dependency is only ever downloaded once.
                result[definitionFile] = installDependencies(parent, command(parent))
            }

            println("Resolving ${javaClass.simpleName} dependencies in '${parent.name}' took ${elapsed / 1000}s.")
        }

        return result
    }

    /**
     * Install dependencies using the given package manager command. Parse dependencies afterwards to return a
     * dependency tree.
     */
    fun installDependencies(workingDir: File, managerCommand: String): Dependency {
        log.debug { "Using '$managerCommand' to install ${javaClass.simpleName} dependencies." }

        val modulesDir = File(workingDir, "node_modules")
        require(!modulesDir.isDirectory) { "'$modulesDir' directory already exists." }

        val rootDependency: Dependency

        try {
            // Install all NPM dependencies to enable NPM to list dependencies.
            val install = ProcessCapture(workingDir, managerCommand, "install")
            if (install.exitValue() != 0) {
                throw IOException(
                        "'${install.commandLine}' failed with exit code ${install.exitValue()}:\n${install.stderr()}")
            }

            rootDependency = parseInstalledDependencies(workingDir)
        } finally {
            // Delete node_modules folder to not pollute the scan.
            if (!modulesDir.deleteRecursively()) {
                throw IOException("Unable to delete the '$modulesDir' directory.")
            }
        }

        return rootDependency
    }

    private fun parseInstalledDependencies(workingDir: File): Dependency {
        val modulesDir = File(workingDir, "node_modules")

        // Collect first-order production dependencies and their transitive production dependencies.
        log.debug { "Using '$npm' to list production dependencies." }

        // Note that listing dependencies fails if peer dependencies are missing.
        @Suppress("UnsafeCast")
        val prodJson = parseJsonProcessOutput(workingDir, npm, "list", "--json", "--only=prod") as JsonObject
        val prodDependencies = if (prodJson.contains("dependencies")) {
            parseNodeModules(modulesDir, prodJson["dependencies"].obj, "dependencies")
        } else {
            listOf()
        }

        // Collect first-order development dependencies and their transitive production dependencies.
        log.debug { "Using '$npm' to list development dependencies." }

        // Note that listing dependencies fails if peer dependencies are missing.
        @Suppress("UnsafeCast")
        val devJson = parseJsonProcessOutput(workingDir, npm, "list", "--json", "--only=dev") as JsonObject
        val devDependencies = if (devJson.contains("dependencies")) {
            parseNodeModules(modulesDir, devJson["dependencies"].obj, "devDependencies")
        } else {
            listOf()
        }

        val artifact = prodJson["name"].string
        val version = prodJson["version"].string

        return Dependency(artifact = artifact, version = Semver(version, SemverType.NPM),
                dependencies = prodDependencies + devDependencies, scope = "root")
    }

    private fun parseNodeModules(modulesDir: File, json: JsonObject, scope: String): List<Dependency> {
        val result = mutableListOf<Dependency>()
        json.forEach { key, jsonElement ->
            val version = jsonElement["version"].string
            val dependencies = if (jsonElement.obj.contains("dependencies")) {
                parseNodeModules(modulesDir, jsonElement["dependencies"].obj, scope)
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
                    scm = if (repository.isJsonObject) repository["url"].string else repository.string
                }
            }

            val dependency = Dependency(artifact = key, version = Semver(version, SemverType.NPM),
                    dependencies = dependencies, scope = scope, scm = scm)
            result.add(dependency)
        }
        return result
    }
}
