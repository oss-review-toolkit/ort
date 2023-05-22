/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

@file:Suppress("TooManyFunctions")

package org.ossreviewtoolkit.plugins.packagemanagers.node

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

import java.io.File
import java.util.concurrent.ConcurrentHashMap

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.DependencyGraph
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.model.readTree
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.NpmDependencyHandler
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.NpmModuleInfo
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.expandNpmShortcutUrl
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.fixNpmDownloadUrl
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.hasNpmLockFile
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.mapDefinitionFilesForNpm
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.parseNpmAuthors
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.parseNpmLicenses
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.parseNpmVcsInfo
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.splitNpmNamespaceAndName
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.fieldNamesOrEmpty
import org.ossreviewtoolkit.utils.common.isSymbolicLink
import org.ossreviewtoolkit.utils.common.realFile
import org.ossreviewtoolkit.utils.common.stashDirectories
import org.ossreviewtoolkit.utils.common.textValueOrEmpty
import org.ossreviewtoolkit.utils.common.withoutPrefix

import org.semver4j.RangesList
import org.semver4j.RangesListFactory

/** Name of the scope with the regular dependencies. */
private const val DEPENDENCIES_SCOPE = "dependencies"

/** Name of the scope with optional dependencies. */
private const val OPTIONAL_DEPENDENCIES_SCOPE = "optionalDependencies"

/** Name of the scope with development dependencies. */
private const val DEV_DEPENDENCIES_SCOPE = "devDependencies"

/**
 * The [Node package manager](https://www.npmjs.com/) for JavaScript.
 *
 * This package manager supports the following [options][PackageManagerConfiguration.options]:
 * - *legacyPeerDeps*: If true, the "--legacy-peer-deps" flag is passed to NPM to ignore conflicts in peer dependencies
 *   which are reported since NPM 7. This allows to analyze NPM 6 projects with peer dependency conflicts. For more
 *   information see the [documentation](https://docs.npmjs.com/cli/v8/commands/npm-install#strict-peer-deps) and the
 *   [NPM Blog](https://blog.npmjs.org/post/626173315965468672/npm-v7-series-beta-release-and-semver-major).
 */
open class Npm(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    companion object : Logging {
        /** Name of the configuration option to toggle legacy peer dependency support. */
        const val OPTION_LEGACY_PEER_DEPS = "legacyPeerDeps"
    }

    class Factory : AbstractPackageManagerFactory<Npm>("NPM") {
        override val globsForDefinitionFiles = listOf("package.json")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Npm(type, analysisRoot, analyzerConfig, repoConfig)
    }

    private val legacyPeerDeps = options[OPTION_LEGACY_PEER_DEPS].toBoolean()

    private val graphBuilder = DependencyGraphBuilder(NpmDependencyHandler(this))

    private val npmViewCache = ConcurrentHashMap<String, Deferred<JsonNode>>()

    /**
     * Search depth in the `node_modules` directory for `package.json` files used for collecting all packages of the
     * projects.
     */
    protected open val modulesSearchDepth = Int.MAX_VALUE

    protected open fun hasLockFile(projectDir: File) = hasNpmLockFile(projectDir)

    /**
     * Check if [this] represents a workspace within a `node_modules` directory.
     */
    protected open fun File.isWorkspaceDir() = isSymbolicLink()

    /**
     * Load the submodule directories of the project defined in [moduleDir].
     */
    protected open fun loadWorkspaceSubmodules(moduleDir: File): List<File> {
        val nodeModulesDir = moduleDir.resolve("node_modules")
        if (!nodeModulesDir.isDirectory) return emptyList()

        val searchDirs = nodeModulesDir.walk().maxDepth(1).filter {
            it.isDirectory && it.name.startsWith("@")
        }.toList() + nodeModulesDir

        return searchDirs.flatMap { dir ->
            dir.walk().maxDepth(1).filter { it.isDirectory && it.isSymbolicLink() }.toList()
        }
    }

    override fun command(workingDir: File?) = if (Os.isWindows) "npm.cmd" else "npm"

    override fun getVersionRequirement(): RangesList = RangesListFactory.create("6.* - 8.*")

    override fun mapDefinitionFiles(definitionFiles: List<File>) = mapDefinitionFilesForNpm(definitionFiles).toList()

    override fun beforeResolution(definitionFiles: List<File>) {
        // We do not actually depend on any features specific to an NPM version, but we still want to stick to a
        // fixed minor version to be sure to get consistent results.
        checkVersion()
    }

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile

        return try {
            stashDirectories(workingDir.resolve("node_modules")).use {
                resolveDependenciesInternal(definitionFile)
            }
        } finally {
            rawModuleInfoCache.clear()
        }
    }

    private fun resolveDependenciesInternal(definitionFile: File): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile

        // Actually installing the dependencies is the easiest way to get the metadata of all transitive
        // dependencies (i.e. their respective "package.json" files). As NPM uses a global cache, the same
        // dependency is only ever downloaded once.
        val issues = installDependencies(workingDir)

        val project = runCatching {
            parseProject(definitionFile)
        }.getOrElse {
            logger.error { "Failed to parse project information: ${it.collectMessages()}" }
            Project.EMPTY
        }

        if (issues.any { it.severity == Severity.ERROR }) {
            return listOf(ProjectAnalyzerResult(project, emptySet(), issues))
        }

        // Create packages for all modules found in the workspace and add them to the graph builder. They are
        // reused when they are referenced by scope dependencies.
        val packages = parseInstalledModules(workingDir)
        graphBuilder.addPackages(packages.values)

        val scopeNames = setOfNotNull(
            // Optional dependencies are just like regular dependencies except that NPM ignores failures when
            // installing them (see https://docs.npmjs.com/files/package.json#optionaldependencies), i.e. they are
            // not a separate scope in our semantics.
            buildDependencyGraphForScopes(
                project,
                workingDir,
                setOf(DEPENDENCIES_SCOPE, OPTIONAL_DEPENDENCIES_SCOPE),
                DEPENDENCIES_SCOPE
            ),

            buildDependencyGraphForScopes(
                project,
                workingDir,
                setOf(DEV_DEPENDENCIES_SCOPE),
                DEV_DEPENDENCIES_SCOPE
            )
        )

        // TODO: add support for peerDependencies and bundledDependencies.

        return listOf(
            ProjectAnalyzerResult(
                project = project.copy(scopeNames = scopeNames),
                // Packages are set later by createPackageManagerResult().
                packages = emptySet(),
                issues = issues
            )
        )
    }

    private fun parseInstalledModules(rootDirectory: File): Map<String, Package> {
        val nodeModulesDir = rootDirectory.resolve("node_modules")

        logger.info { "Searching for 'package.json' files in '$nodeModulesDir'..." }

        val nodeModulesFiles = nodeModulesDir.walk().maxDepth(modulesSearchDepth).filter {
            it.name == "package.json" && isValidNodeModulesDirectory(nodeModulesDir, nodeModulesDirForPackageJson(it))
        }

        return runBlocking(Dispatchers.IO) {
            nodeModulesFiles.mapTo(mutableListOf()) { file ->
                logger.debug { "Starting to parse '$file'..." }
                async {
                    parsePackage(rootDirectory, file).also { (id, _) ->
                        logger.debug { "Finished parsing '$file' to '$id'." }
                    }
                }
            }.awaitAll().toMap()
        }
    }

    private fun isValidNodeModulesDirectory(rootModulesDir: File, modulesDir: File?): Boolean {
        if (modulesDir == null) return false

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

        return modulesDir.takeIf { it.name == "node_modules" }
    }

    /**
     * Construct a [Package] by parsing its _package.json_ file and - if applicable - querying additional
     * content via the `npm view` command. The result is a [Pair] with the raw identifier and the new package.
     */
    internal suspend fun parsePackage(workingDir: File, packageFile: File): Pair<String, Package> {
        val packageDir = packageFile.parentFile

        logger.debug { "Found a 'package.json' file in '$packageDir'." }

        // The "name" and "version" are the only required fields, see:
        // https://docs.npmjs.com/creating-a-package-json-file#required-name-and-version-fields
        val json = packageFile.readValue<ObjectNode>()
        val rawName = json["name"].textValue()
        val (namespace, name) = splitNpmNamespaceAndName(rawName)
        val version = json["version"].textValue()

        val declaredLicenses = parseNpmLicenses(json)
        val authors = parseNpmAuthors(json)

        var description = json["description"].textValueOrEmpty()
        var homepageUrl = json["homepage"].textValueOrEmpty()

        // Note that all fields prefixed with "_" are considered private to NPM and should not be relied on.
        var downloadUrl = expandNpmShortcutUrl(json["_resolved"].textValueOrEmpty()).ifEmpty {
            // If the normalized form of the specified dependency contains a URL as the version, expand and use it.
            val fromVersion = json["_from"].textValueOrEmpty().substringAfterLast('@')
            expandNpmShortcutUrl(fromVersion).takeIf { it != fromVersion }.orEmpty()
        }

        var hash = Hash.create(json["_integrity"].textValueOrEmpty())

        var vcsFromPackage = parseNpmVcsInfo(json)

        val id = Identifier("NPM", namespace, name, version)

        if (packageDir.isWorkspaceDir()) {
            val realPackageDir = packageDir.realFile()

            logger.debug { "The package directory '$packageDir' links to '$realPackageDir'." }

            // Yarn workspaces refer to project dependencies from the same workspace via symbolic links. Use that
            // as the trigger to get VcsInfo locally instead of querying the NPM registry.
            logger.debug { "Resolving the package info for '${id.toCoordinates()}' locally from '$realPackageDir'." }

            val vcsFromDirectory = VersionControlSystem.forDirectory(realPackageDir)?.getInfo().orEmpty()
            vcsFromPackage = vcsFromPackage.merge(vcsFromDirectory)
        } else {
            val hasIncompleteData = description.isEmpty() || homepageUrl.isEmpty() || downloadUrl.isEmpty()
                    || hash == Hash.NONE || vcsFromPackage == VcsInfo.EMPTY

            if (hasIncompleteData) {
                runCatching {
                    getRemotePackageDetailsAsync(workingDir, "$rawName@$version").await()
                }.onSuccess { details ->
                    if (description.isEmpty()) description = details["description"].textValueOrEmpty()
                    if (homepageUrl.isEmpty()) homepageUrl = details["homepage"].textValueOrEmpty()

                    details["dist"]?.let { dist ->
                        if (downloadUrl.isEmpty() || hash == Hash.NONE) {
                            downloadUrl = dist["tarball"].textValueOrEmpty()
                            hash = Hash.create(dist["shasum"].textValueOrEmpty())
                        }
                    }

                    vcsFromPackage = parseNpmVcsInfo(details)
                }.onFailure { e ->
                    logger.debug { "Unable to get package details from a remote registry: ${e.collectMessages()}" }
                }
            }
        }

        downloadUrl = fixNpmDownloadUrl(downloadUrl)

        val vcsFromDownloadUrl = VcsHost.parseUrl(downloadUrl)
        if (vcsFromDownloadUrl.url != downloadUrl) {
            vcsFromPackage = vcsFromPackage.merge(vcsFromDownloadUrl)
        }

        val module = Package(
            id = id,
            authors = authors,
            declaredLicenses = declaredLicenses,
            description = description,
            homepageUrl = homepageUrl,
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact(
                url = VcsHost.toArchiveDownloadUrl(vcsFromDownloadUrl) ?: downloadUrl,
                hash = hash
            ),
            vcs = vcsFromPackage,
            vcsProcessed = processPackageVcs(vcsFromPackage, homepageUrl)
        )

        require(module.id.name.isNotEmpty()) {
            "Generated package info for '${id.toCoordinates()}' has no name."
        }

        require(module.id.version.isNotEmpty()) {
            "Generated package info for '${id.toCoordinates()}' has no version."
        }

        return Pair(id.toCoordinates(), module)
    }

    private suspend fun getRemotePackageDetailsAsync(workingDir: File, packageName: String): Deferred<JsonNode> =
        withContext(Dispatchers.IO) {
            npmViewCache.getOrPut(packageName) {
                async {
                    getRemotePackageDetails(workingDir, packageName)
                }
            }
        }

    protected open fun getRemotePackageDetails(workingDir: File, packageName: String): JsonNode {
        val process = run(workingDir, "view", "--json", packageName)
        return jsonMapper.readTree(process.stdout)
    }

    /** Cache for submodules identified by its moduleDir absolutePath */
    private val submodulesCache = ConcurrentHashMap<String, List<File>>()

    /**
     * Find the directories which are defined as submodules of the project within [moduleDir].
     */
    protected fun findWorkspaceSubmodules(moduleDir: File): List<File> =
        submodulesCache.getOrPut(moduleDir.absolutePath) {
            loadWorkspaceSubmodules(moduleDir)
        }

    /**
     * Retrieve all the dependencies of [project] from the given [scopes] and add them to the dependency graph under
     * the given [targetScope]. Return the target scope name if dependencies are found; *null* otherwise.
     */
    private fun buildDependencyGraphForScopes(
        project: Project,
        workingDir: File,
        scopes: Set<String>,
        targetScope: String
    ): String? {
        if (excludes.isScopeExcluded(targetScope)) return null

        val qualifiedScopeName = DependencyGraph.qualifyScope(project, targetScope)
        val moduleDependencies = getModuleDependencies(workingDir, scopes)

        moduleDependencies.forEach { graphBuilder.addDependency(qualifiedScopeName, it) }

        return targetScope.takeUnless { moduleDependencies.isEmpty() }
    }

    private fun getModuleDependencies(moduleDir: File, scopes: Set<String>): Set<NpmModuleInfo> {
        val workspaceModuleDirs = findWorkspaceSubmodules(moduleDir)

        return buildSet {
            addAll(getModuleInfo(moduleDir, scopes)!!.dependencies)

            workspaceModuleDirs.forEach { workspaceModuleDir ->
                addAll(getModuleInfo(workspaceModuleDir, scopes, listOf(moduleDir))!!.dependencies)
            }
        }
    }

    private fun getModuleInfo(
        moduleDir: File,
        scopes: Set<String>,
        ancestorModuleDirs: List<File> = emptyList(),
        ancestorModuleIds: List<Identifier> = emptyList(),
        packageType: String = managerName
    ): NpmModuleInfo? {
        val moduleInfo = parsePackageJson(moduleDir, scopes)
        val dependencies = mutableSetOf<NpmModuleInfo>()
        val moduleId = splitNpmNamespaceAndName(moduleInfo.name).let { (namespace, name) ->
            Identifier(packageType, namespace, name, moduleInfo.version)
        }

        val cycleStartIndex = ancestorModuleIds.indexOf(moduleId)
        if (cycleStartIndex >= 0) {
            val cycle = (ancestorModuleIds.subList(cycleStartIndex, ancestorModuleIds.size) + moduleId)
                .joinToString(" -> ")
            logger.debug { "Not adding dependency '$moduleId' to avoid cycle: $cycle." }
            return null
        }

        val pathToRoot = listOf(moduleDir) + ancestorModuleDirs
        moduleInfo.dependencyNames.forEach { dependencyName ->
            val dependencyModuleDirPath = findDependencyModuleDir(dependencyName, pathToRoot)

            if (dependencyModuleDirPath.isNotEmpty()) {
                val dependencyModuleDir = dependencyModuleDirPath.first()

                getModuleInfo(
                    moduleDir = dependencyModuleDir,
                    scopes = setOf("dependencies", "optionalDependencies"),
                    ancestorModuleDirs = dependencyModuleDirPath.subList(1, dependencyModuleDirPath.size),
                    ancestorModuleIds = ancestorModuleIds + moduleId,
                    packageType = "NPM"
                )?.let { dependencies += it }

                return@forEach
            }

            logger.debug {
                "It seems that the '$dependencyName' module was not installed as the package file could not be found " +
                    "anywhere in '${pathToRoot.joinToString()}'. This might be fine if the module is specific to a " +
                        "platform other than the one ORT is running on. A typical example is the 'fsevents' module."
            }
        }

        return NpmModuleInfo(moduleId, moduleDir, moduleInfo.packageJson, dependencies)
    }

    /**
     * An internally used data class with information about a module retrieved from the module's package.json. This
     * information is further processed and eventually converted to an [NpmModuleInfo] object containing everything
     * required by the Npm package manager.
     */
    private data class RawModuleInfo(
        val name: String,
        val version: String,
        val dependencyNames: Set<String>,
        val packageJson: File
    )

    private val rawModuleInfoCache = mutableMapOf<Pair<File, Set<String>>, RawModuleInfo>()

    private fun parsePackageJson(moduleDir: File, scopes: Set<String>): RawModuleInfo =
        rawModuleInfoCache.getOrPut(moduleDir to scopes) {
            val packageJsonFile = moduleDir.resolve("package.json")
            logger.debug { "Parsing module info from '${packageJsonFile.absolutePath}'." }
            val json = packageJsonFile.readTree()

            val name = json["name"].textValueOrEmpty()
            if (name.isBlank()) {
                logger.warn {
                    "The '$packageJsonFile' does not set a name, which is only allowed for unpublished packages."
                }
            }

            val version = json["version"].textValueOrEmpty()
            if (version.isBlank()) {
                logger.warn {
                    "The '$packageJsonFile' does not set a version, which is only allowed for unpublished packages."
                }
            }

            val dependencyNames = scopes.flatMapTo(mutableSetOf()) { scope ->
                // Yarn ignores "//" keys in the dependencies to allow comments, therefore ignore them here as well.
                json[scope].fieldNamesOrEmpty().asSequence().filterNot { it == "//" }
            }

            RawModuleInfo(
                name = name,
                version = version,
                dependencyNames = dependencyNames,
                packageJson = packageJsonFile
            )
        }

    private fun findDependencyModuleDir(dependencyName: String, searchModuleDirs: List<File>): List<File> {
        searchModuleDirs.forEachIndexed { index, moduleDir ->
            // Note: resolve() also works for scoped dependencies, e.g. dependencyName = "@x/y"
            val dependencyModuleDir = moduleDir.resolve("node_modules/$dependencyName")
            if (dependencyModuleDir.isDirectory) {
                return listOf(dependencyModuleDir) + searchModuleDirs.subList(index, searchModuleDirs.size)
            }
        }
        return emptyList()
    }

    private fun parseProject(packageJson: File): Project {
        logger.debug { "Parsing project info from '$packageJson'." }

        val json = jsonMapper.readTree(packageJson)

        val rawName = json["name"].textValueOrEmpty()
        val (namespace, name) = splitNpmNamespaceAndName(rawName)
        if (name.isBlank()) {
            logger.warn { "'$packageJson' does not define a name." }
        }

        val version = json["version"].textValueOrEmpty()
        if (version.isBlank()) {
            logger.warn { "'$packageJson' does not define a version." }
        }

        val declaredLicenses = parseNpmLicenses(json)
        val authors = parseNpmAuthors(json)
        val homepageUrl = json["homepage"].textValueOrEmpty()
        val projectDir = packageJson.parentFile
        val vcsFromPackage = parseNpmVcsInfo(json)

        return Project(
            id = Identifier(
                type = managerName,
                namespace = namespace,
                name = name,
                version = version
            ),
            definitionFilePath = VersionControlSystem.getPathInfo(packageJson).path,
            authors = authors,
            declaredLicenses = declaredLicenses,
            vcs = vcsFromPackage,
            vcsProcessed = processProjectVcs(projectDir, vcsFromPackage, homepageUrl),
            homepageUrl = homepageUrl
        )
    }

    /**
     * Install dependencies using the given package manager command.
     */
    private fun installDependencies(workingDir: File): List<Issue> {
        requireLockfile(workingDir) { hasLockFile(workingDir) }

        // Install all NPM dependencies to enable NPM to list dependencies.
        val process = runInstall(workingDir)

        val lines = process.stderr.lines()
        val issues = mutableListOf<Issue>()

        fun mapLinesToIssues(prefix: String, severity: Severity) {
            val ignorablePrefixes = setOf("code ", "errno ", "path ", "syscall ")
            val singleLinePrefixes = setOf("deprecated ")
            val minSecondaryPrefixLength = 5

            val issueLines = lines.mapNotNull { line ->
                line.withoutPrefix(prefix)?.takeUnless { ignorablePrefixes.any { prefix -> it.startsWith(prefix) } }
            }

            var commonPrefix: String
            var previousPrefix = ""

            val collapsedLines = issueLines.fold(mutableListOf<String>()) { messages, line ->
                if (messages.isEmpty()) {
                    // The first line is always added including the prefix. The prefix will be removed later.
                    messages += line
                } else {
                    // Find the longest common prefix that ends with space.
                    commonPrefix = line.commonPrefixWith(messages.last())
                    if (!commonPrefix.endsWith(' ')) {
                        // Deal with prefixes being used on their own as separators.
                        commonPrefix = if ("$commonPrefix " == previousPrefix) {
                            "$commonPrefix "
                        } else {
                            commonPrefix.dropLastWhile { it != ' ' }
                        }
                    }

                    if (commonPrefix !in singleLinePrefixes && commonPrefix.length >= minSecondaryPrefixLength) {
                        // Do not drop the whole prefix but keep the space when concatenating lines.
                        messages[messages.size - 1] += line.drop(commonPrefix.length - 1).trimEnd()
                        previousPrefix = commonPrefix
                    } else {
                        // Remove the prefix from previously added message start.
                        messages[messages.size - 1] = messages.last().removePrefix(previousPrefix).trimStart()
                        messages += line
                    }
                }

                messages
            }

            if (collapsedLines.isNotEmpty()) {
                // Remove the prefix from the last added message start.
                collapsedLines[collapsedLines.size - 1] = collapsedLines.last().removePrefix(previousPrefix).trimStart()
            }

            collapsedLines.forEach { line ->
                // Skip any footer as a whole.
                if (line == "A complete log of this run can be found in:") return

                issues += Issue(
                    source = managerName,
                    message = line,
                    severity = severity
                )
            }
        }

        mapLinesToIssues("npm WARN ", Severity.WARNING)
        mapLinesToIssues("npm ERR! ", Severity.ERROR)

        return issues
    }

    protected open fun runInstall(workingDir: File): ProcessCapture {
        val options = listOfNotNull(
            "--ignore-scripts",
            "--no-audit",
            "--legacy-peer-deps".takeIf { legacyPeerDeps }
        )

        val subcommand = if (hasLockFile(workingDir)) "ci" else "install"
        return ProcessCapture(workingDir, command(workingDir), subcommand, *options.toTypedArray())
    }
}
