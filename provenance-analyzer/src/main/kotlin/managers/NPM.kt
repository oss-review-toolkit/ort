package com.here.provenanceanalyzer.managers

import ch.frankel.slf4k.debug
import ch.frankel.slf4k.info
import ch.frankel.slf4k.warn

import com.fasterxml.jackson.databind.node.ObjectNode

import com.here.provenanceanalyzer.OS
import com.here.provenanceanalyzer.PackageManager
import com.here.provenanceanalyzer.ProcessCapture
import com.here.provenanceanalyzer.log
import com.here.provenanceanalyzer.model.Dependency
import com.here.provenanceanalyzer.model.Package
import com.here.provenanceanalyzer.model.Project
import com.here.provenanceanalyzer.model.ScanResult
import com.here.provenanceanalyzer.model.Scope
import com.here.provenanceanalyzer.model.jsonMapper

import com.vdurmont.semver4j.Semver
import com.vdurmont.semver4j.Semver.SemverType

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URL
import java.net.URLEncoder

import kotlin.system.measureTimeMillis

@Suppress("LargeClass")
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

    override fun resolveDependencies(definitionFiles: List<File>): Map<File, ScanResult> {
        val version = ProcessCapture(npm, "--version")
        if (version.exitValue() != 0) {
            throw IOException("Unable to determine the $npm version:\n${version.stderr()}")
        }

        val expectedVersion = Semver("5.1.0", SemverType.NPM)
        val actualVersion = Semver(version.stdout().trim(), SemverType.NPM)
        if (actualVersion != expectedVersion) {
            throw IOException(
                    "Unsupported $npm version $actualVersion, version $expectedVersion is required.")
        }

        val result = mutableMapOf<File, ScanResult>()

        definitionFiles.forEach { definitionFile ->
            val workingDir = definitionFile.parentFile

            log.debug { "Resolving ${javaClass.simpleName} dependencies in '${workingDir.name}'..." }

            val elapsed = measureTimeMillis {
                val modulesDir = File(workingDir, "node_modules")
                require(!modulesDir.isDirectory) { "'$modulesDir' directory already exists." }

                try {
                    // Actually installing the dependencies is the easiest way to get the meta-data of all transitive
                    // dependencies (i.e. their respective "package.json" files). As npm (and yarn) use a global cache,
                    // the same dependency is only ever downloaded once.
                    installDependencies(workingDir)

                    val packages = parseInstalledModules(workingDir)

                    val dependencies = Scope("dependencies", true,
                            parseDependencies(definitionFile, "dependencies", packages))
                    val devDependencies = Scope("devDependencies", false,
                            parseDependencies(definitionFile, "devDependencies", packages))

                    // TODO: add support for peerDependencies, bundledDependencies, and optionalDependencies.

                    val project = parseProject(definitionFile, listOf(dependencies, devDependencies),
                            packages.values.toList().sortedBy { it.identifier })
                    result[definitionFile] = project
                } finally {
                    // Delete node_modules folder to not pollute the scan.
                    if (!modulesDir.deleteRecursively()) {
                        throw IOException("Unable to delete the '$modulesDir' directory.")
                    }
                }
            }

            log.debug {
                "Resolving ${javaClass.simpleName} dependencies in '${workingDir.name}' took ${elapsed / 1000}s."
            }
        }

        return result
    }

    @Suppress("ComplexMethod")
    private fun parseInstalledModules(rootDirectory: File): Map<String, Package> {
        val packages = mutableMapOf<String, Package>()
        val nodeModulesDir = File(rootDirectory, "node_modules")

        log.info { "Searching for package.json files in ${nodeModulesDir.absolutePath}..." }

        nodeModulesDir.walkTopDown().filter {
            it.name == "package.json" && isValidNodeModulesDirectory(nodeModulesDir, nodeModulesDirForPackageJson(it))
        }.forEach {
            log.debug { "Found module: ${it.absolutePath}" }

            @Suppress("UnsafeCast")
            val json = jsonMapper.readTree(it) as ObjectNode
            val rawName = json["name"].asText()
            val name = rawName.substringAfterLast("/")
            val namespace = if (rawName.contains("/")) rawName.substringBeforeLast("/") else ""
            val version = json["version"].asText()

            var description: String
            var homepageUrl: String
            var downloadUrl: String
            var hash: String
            var vcsUrl = ""
            var vcsRevision = ""

            val identifier = "$rawName@$version"

            // Download package info from registry.npmjs.org.
            // TODO: check if unpkg.com can be used as a fallback in case npmjs.org is down.
            log.debug { "Retrieving package info for $identifier" }
            val encodedName = if (rawName.startsWith("@")) {
                "@${URLEncoder.encode(rawName.substringAfter("@"), "UTF-8")}"
            } else {
                rawName
            }
            val url = URL("https://registry.npmjs.org/$encodedName")
            try {
                val packageInfo = jsonMapper.readTree(url.readText())
                val infoJson = packageInfo["versions"][version]

                description = infoJson["description"].asText()
                homepageUrl = if (infoJson["homepage"] != null) infoJson["homepage"].asText() else ""

                val dist = infoJson["dist"]
                downloadUrl = dist["tarball"].asText()
                hash = dist["shasum"].asText()

                if (infoJson["repository"] != null) {
                    val repository = infoJson["repository"]
                    vcsUrl = repository["url"].asText()
                }
                vcsRevision = if (infoJson["gitHead"] != null) infoJson["gitHead"].asText() else ""
            } catch (e: FileNotFoundException) {
                // Fallback to getting detailed info from the package.json file. Some info will likely be missing.

                description = if (json["description"] != null) json["description"].asText() else ""
                homepageUrl = if (json["homepage"] != null) json["homepage"].asText() else ""
                downloadUrl = if (json["_resolved"] != null) json["_resolved"].asText() else ""
                hash = if (json["_integrity"] != null) json["_integrity"].asText() else ""

                vcsUrl = if (json["repository"] != null) {
                    if (json["repository"].textValue() != null)
                        json["repository"].asText()
                    else
                        json["repository"]["url"].asText()
                } else ""
            }

            val module = Package("NPM", namespace, name, description, version, homepageUrl, downloadUrl,
                    hash, vcsUrl, vcsRevision)

            require(module.name.isNotEmpty()) {
                "Generated package info for $identifier has no name."
            }

            require(module.version.isNotEmpty()) {
                "Generated package info for $identifier has no version."
            }

            packages[identifier] = module
        }

        return packages
    }

    private fun isValidNodeModulesDirectory(rootModulesDir: File, modulesDir: File?): Boolean {
        if (modulesDir == null) {
            return false
        }

        var currentDir: File = modulesDir
        while (currentDir != rootModulesDir) {
            if (currentDir.name != "node_modules") {
                return false
            }

            currentDir = currentDir.parentFile.parentFile
            if (currentDir.name.startsWith("@")) {
                currentDir = currentDir.parentFile
            }
        }

        return true
    }

    private fun nodeModulesDirForPackageJson(packageJson: File): File? {
        var modulesDir = packageJson.parentFile.parentFile
        if (modulesDir.name.startsWith("@")) {
            modulesDir = modulesDir.parentFile
        }

        return if (modulesDir.name == "node_modules") modulesDir else null
    }

    private fun parseDependencies(packageJson: File, scope: String, packages: Map<String, Package>): List<Dependency> {
        // Read package.json
        val json = jsonMapper.readTree(packageJson)
        val dependencies = mutableListOf<Dependency>()
        if (json[scope] != null) {
            log.debug { "Looking for dependencies in scope $scope" }
            val dependencyMap = json[scope]
            dependencyMap.fields().forEach { (name, _) ->
                val dependency = buildTree(packageJson.parentFile, packageJson.parentFile, name, packages)
                dependencies.add(dependency)
            }
        } else {
            log.warn { "Could not find scope $scope in ${packageJson.absolutePath}" }
        }
        dependencies.sortBy { it.identifier }
        return dependencies
    }

    private fun buildTree(rootDir: File, startDir: File, name: String, packages: Map<String, Package>): Dependency {
        log.debug { "Building dependency tree for $name from directory ${startDir.absolutePath}" }
        val nodeModulesDir = File(startDir, "node_modules")
        val moduleDir = File(nodeModulesDir, name)
        val packageFile = File(moduleDir, "package.json")
        if (packageFile.isFile) {
            log.debug { "Found package file for module $name: ${packageFile.absolutePath}" }
            val packageJson = jsonMapper.readTree(packageFile)
            val version = packageJson["version"].asText()
            val packageInfo = packages["$name@$version"] ?:
                    throw IOException("Could not find package info for $name@$version")
            val dependencies = mutableListOf<Dependency>()
            if (packageJson["dependencies"] != null) {
                val dependencyMap = packageJson["dependencies"]
                dependencyMap.fields().forEach { (name, _) ->
                    val dependency = buildTree(rootDir, packageFile.parentFile, name, packages)
                    dependencies.add(dependency)
                }
            }
            // TODO: make the identifier below a dynamic getter in the dependency class which is not serialized
            dependencies.sortBy { it.identifier }
            return Dependency(packageInfo.name, packageInfo.namespace, packageInfo.version, packageInfo.hash,
                    dependencies)
        } else if (rootDir == startDir) {
            log.error("Could not find module $name")
            return Dependency(name, "", "unknown, package not installed", "", listOf())
        } else {
            var parent = startDir.parentFile.parentFile

            // For scoped packages we need to go one more dir up.
            if (parent.name == "node_modules") {
                parent = parent.parentFile
            }
            log.debug {
                "Could not find package file for $name in ${startDir.absolutePath}, looking in " +
                        "${parent.absolutePath} instead"
            }
            return buildTree(rootDir, parent, name, packages)
        }
    }

    private fun parseProject(packageJson: File, scopes: List<Scope>, packages: List<Package>): ScanResult {
        val json = jsonMapper.readTree(packageJson)
        val name = json["name"].asText()
        val version = json["version"].asText()
        val vcsUrl = if (json["repository"] != null) {
            if (json["repository"].textValue() != null)
                json["repository"].asText()
            else
                json["repository"]["url"].asText()
        } else ""
        val homepageUrl = if (json["homepage"] != null) json["homepage"].asText() else ""

        // TODO: parse revision from vcs

        return ScanResult(Project(name, listOf(), version, vcsUrl, "", homepageUrl, scopes), packages)
    }

    /**
     * Install dependencies using the given package manager command.
     */
    fun installDependencies(workingDir: File) {
        val lockFiles = listOf("npm-shrinkwrap.json", "package-lock.json", "yarn.lock")
        val lockFileCount = lockFiles.count { File(workingDir, it).isFile }
        when {
            lockFileCount == 0 -> throw IllegalArgumentException(
                    "No lockfile found in ${workingDir}, dependency versions are unstable.")
            lockFileCount > 1 -> throw IllegalArgumentException(
                    "${workingDir} contains multiple lockfiles. It is ambiguous which one to use.")
        }

        val managerCommand = command(workingDir)
        log.debug { "Using '$managerCommand' to install ${javaClass.simpleName} dependencies." }

        // Install all NPM dependencies to enable NPM to list dependencies.
        val install = ProcessCapture(workingDir, managerCommand, "install")
        if (install.exitValue() != 0) {
            throw IOException(
                    "'${install.commandLine}' failed with exit code ${install.exitValue()}:\n${install.stderr()}")
        }

        // TODO: capture warnings from npm output, e.g. "Unsupported platform" which happens for fsevents on all
        // platforms except for Mac.
    }

}
