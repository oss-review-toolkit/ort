package com.here.ort.analyzer.managers

import ch.frankel.slf4k.*

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

import com.here.ort.analyzer.Main
import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.ResolutionResult
import com.here.ort.model.AnalyzerResult
import com.here.ort.model.Package
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.Scope
import com.here.ort.util.OS
import com.here.ort.util.ProcessCapture
import com.here.ort.util.asTextOrEmpty
import com.here.ort.util.checkCommandVersion
import com.here.ort.util.jsonMapper
import com.here.ort.util.log

import com.vdurmont.semver4j.Semver
import com.vdurmont.semver4j.Semver.SemverType

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.SortedSet

@Suppress("LargeClass", "TooManyFunctions")
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

    override fun prepareResolution() {
        // We do not actually depend on any features specific to an NPM 5.x or Yarn version, but we still want to
        // stick to fixed versions to be sure to get consistent results.
        checkCommandVersion(npm, Semver("5.3.0", SemverType.NPM), ignoreActualVersion = Main.ignoreVersions)
        checkCommandVersion(yarn, Semver("1.1.0", SemverType.NPM), ignoreActualVersion = Main.ignoreVersions)
    }

    override fun resolveDependency(projectDir: File, workingDir: File, definitionFile: File, result: ResolutionResult) {
        val modulesDir = File(workingDir, "node_modules")

        var tempModulesDir: File? = null
        try {
            // Temporarily move away any existing "node_modules" directory within the same filesystem to ensure
            // the move can be performed atomically.
            if (modulesDir.isDirectory) {
                val tempDir = createTempDir("analyzer", ".tmp", workingDir)
                tempModulesDir = File(tempDir, "node_modules")
                log.warn { "'$modulesDir' already exists, temporarily moving it to '$tempModulesDir'." }
                Files.move(modulesDir.toPath(), tempModulesDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
            }

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
                    packages.values.toSortedSet())
            result[definitionFile] = project
        } finally {
            // Delete node_modules folder to not pollute the scan.
            if (!modulesDir.deleteRecursively()) {
                throw IOException("Unable to delete the '$modulesDir' directory.")
            }

            // Restore any previously existing "node_modules" directory.
            if (tempModulesDir != null) {
                log.info { "Restoring original '$modulesDir' directory from '$tempModulesDir'." }
                Files.move(tempModulesDir.toPath(), modulesDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
                if (!tempModulesDir.parentFile.delete()) {
                    throw IOException("Unable to delete the '${tempModulesDir.parent}' directory.")
                }
            }
        }
    }

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
            val (namespace, name) = splitNamespaceAndName(rawName)
            val version = json["version"].asText()

            var description: String
            var homepageUrl: String
            var downloadUrl: String
            var hash: String
            var hashAlgorithm = ""
            val vcsPath = ""
            var vcsProvider = ""
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

                description = infoJson["description"].asTextOrEmpty()
                homepageUrl = infoJson["homepage"].asTextOrEmpty()

                val dist = infoJson["dist"]
                downloadUrl = dist["tarball"].asText()
                hash = dist["shasum"].asText()
                // TODO: add detection of hash algorithm

                with(parseRepository(infoJson)) {
                    vcsProvider = first
                    vcsUrl = second
                }
                vcsRevision = infoJson["gitHead"].asTextOrEmpty()
            } catch (e: FileNotFoundException) {
                if (Main.stacktrace) {
                    e.printStackTrace()
                }

                // Fallback to getting detailed info from the package.json file. Some info will likely be missing.

                description = json["description"].asTextOrEmpty()
                homepageUrl = json["homepage"].asTextOrEmpty()
                downloadUrl = json["_resolved"].asTextOrEmpty()
                hash = json["_integrity"].asTextOrEmpty()
                // TODO: add detection of hash algorithm

                with(parseRepository(json)) {
                    vcsProvider = first
                    vcsUrl = second
                }
            }

            val module = Package(javaClass.simpleName, namespace, name, version, description, homepageUrl, downloadUrl,
                    hash, hashAlgorithm, vcsPath, vcsProvider, vcsUrl, vcsRevision)

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

    private fun parseDependencies(packageJson: File, scope: String, packages: Map<String, Package>)
            : SortedSet<PackageReference> {
        // Read package.json
        val json = jsonMapper.readTree(packageJson)
        val dependencies = sortedSetOf<PackageReference>()
        if (json[scope] != null) {
            log.debug { "Looking for dependencies in scope $scope" }
            val dependencyMap = json[scope]
            dependencyMap.fields().forEach { (name, _) ->
                val dependency = buildTree(packageJson.parentFile, packageJson.parentFile, name, packages)
                if (dependency != null) {
                    dependencies.add(dependency)
                }
            }
        } else {
            log.warn { "Could not find scope $scope in ${packageJson.absolutePath}" }
        }

        return dependencies
    }

    private fun parseRepository(node: JsonNode): Pair<String, String> {
        var type = ""
        var url = ""
        if (node["repository"] != null) {
            if (node["repository"].textValue() != null)
                url = node["repository"].asText()
            else {
                type = node["repository"]["type"].asTextOrEmpty()
                url = node["repository"]["url"].asTextOrEmpty()
            }
        }
        return Pair(type, url)
    }

    private fun buildTree(rootDir: File, startDir: File, name: String, packages: Map<String, Package>,
                          dependencyBranch: List<String> = listOf()): PackageReference? {
        log.debug { "Building dependency tree for $name from directory ${startDir.absolutePath}" }

        val nodeModulesDir = File(startDir, "node_modules")
        val moduleDir = File(nodeModulesDir, name)
        val packageFile = File(moduleDir, "package.json")

        if (packageFile.isFile) {
            log.debug { "Found package file for module $name: ${packageFile.absolutePath}" }

            val packageJson = jsonMapper.readTree(packageFile)
            val version = packageJson["version"].asText()
            val identifier = "$name@$version"

            if (dependencyBranch.contains(identifier)) {
                log.warn {
                    "Not adding circular dependency $identifier to the tree, it is already on this branch of the " +
                            "dependency tree: ${dependencyBranch.joinToString(" -> ")}"
                }
                return null
            }

            val newDependencyBranch = dependencyBranch + identifier
            val packageInfo = packages[identifier] ?:
                    throw IOException("Could not find package info for $identifier")
            val dependencies = sortedSetOf<PackageReference>()

            if (packageJson["dependencies"] != null) {
                val dependencyMap = packageJson["dependencies"]
                dependencyMap.fields().forEach { (dependencyName, _) ->
                    val dependency = buildTree(rootDir, packageFile.parentFile, dependencyName, packages,
                            newDependencyBranch)
                    if (dependency != null) {
                        dependencies.add(dependency)
                    }
                }
            }

            return packageInfo.toReference(dependencies)
        } else if (rootDir == startDir) {
            log.error { "Could not find module $name" }
            return PackageReference("", name, "unknown, package not installed", sortedSetOf())
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
            return buildTree(rootDir, parent, name, packages, dependencyBranch)
        }
    }

    private fun parseProject(packageJson: File, scopes: List<Scope>, packages: SortedSet<Package>): AnalyzerResult {
        log.debug { "Parsing project info from ${packageJson.absolutePath}." }

        val json = jsonMapper.readTree(packageJson)
        val rawName = json["name"].asText()
        val (namespace, name) = splitNamespaceAndName(rawName)
        val version = json["version"].asText()
        val vcsPath = ""
        val (vcsProvider, vcsUrl) = parseRepository(json)
        val homepageUrl = json["homepage"].asTextOrEmpty()

        // TODO: parse revision from vcs

        val project = Project(
                packageManager = javaClass.simpleName,
                namespace = namespace,
                name = name,
                version = version,
                aliases = emptyList(),
                vcsPath = vcsPath,
                vcsProvider = vcsProvider,
                vcsUrl = vcsUrl,
                vcsRevision = "",
                homepageUrl = homepageUrl,
                scopes = scopes
        )

        return AnalyzerResult(project, packages, Main.allowDynamicVersions)
    }

    /**
     * Install dependencies using the given package manager command.
     */
    private fun installDependencies(workingDir: File) {
        if (!Main.allowDynamicVersions) {
            val lockFiles = listOf("npm-shrinkwrap.json", "package-lock.json", "yarn.lock").filter {
                File(workingDir, it).isFile
            }
            when (lockFiles.size) {
                0 -> throw IllegalArgumentException(
                        "No lockfile found in $workingDir, dependency versions are unstable.")
                1 -> log.debug { "Found lock file '${lockFiles.first()}'." }
                else -> throw IllegalArgumentException(
                        "$workingDir contains multiple lockfiles. It is ambiguous which one to use.")
            }
        }

        val managerCommand = command(workingDir)
        log.debug { "Using '$managerCommand' to install ${javaClass.simpleName} dependencies." }

        // Install all NPM dependencies to enable NPM to list dependencies.
        ProcessCapture(workingDir, managerCommand, "install").requireSuccess()

        // TODO: capture warnings from npm output, e.g. "Unsupported platform" which happens for fsevents on all
        // platforms except for Mac.
    }

    private fun splitNamespaceAndName(rawName: String): Pair<String, String> {
        val name = rawName.substringAfterLast("/")
        val namespace = rawName.removeSuffix(name).removeSuffix("/")
        return Pair(namespace, name)
    }

}
