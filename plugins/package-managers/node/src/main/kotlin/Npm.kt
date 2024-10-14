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

import java.io.File
import java.util.concurrent.ConcurrentHashMap

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManager.Companion.getFallbackProjectName
import org.ossreviewtoolkit.analyzer.PackageManager.Companion.processProjectVcs
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
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.readTree
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.NON_EXISTING_SEMVER
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.NodePackageManager
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.NpmDependencyHandler
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.NpmDetection
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.NpmModuleInfo
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.expandNpmShortcutUrl
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.fixNpmDownloadUrl
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.mapNpmLicenses
import org.ossreviewtoolkit.plugins.packagemanagers.node.utils.parseNpmAuthor
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
    companion object {
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

    private val graphBuilder by lazy { DependencyGraphBuilder(NpmDependencyHandler(this)) }

    private val npmViewCache = ConcurrentHashMap<String, Deferred<PackageJson>>()

    protected open fun hasLockfile(projectDir: File) = NodePackageManager.NPM.hasLockfile(projectDir)

    /**
     * Check if [this] represents a workspace within a `node_modules` directory.
     */
    protected open fun File.isWorkspaceDir() = isSymbolicLink()

    /**
     * Load the submodule directories of the project defined in [moduleDir].
     */
    protected open fun loadWorkspaceSubmodules(moduleDir: File): Set<File> {
        val nodeModulesDir = moduleDir.resolve("node_modules")
        if (!nodeModulesDir.isDirectory) return emptySet()

        val searchDirs = nodeModulesDir.walk().maxDepth(1).filter {
            (it.isDirectory && it.name.startsWith("@")) || it == nodeModulesDir
        }

        return searchDirs.flatMapTo(mutableSetOf()) { dir ->
            dir.walk().maxDepth(1).filter {
                it.isDirectory && it.isSymbolicLink() && it != dir
            }
        }
    }

    override fun command(workingDir: File?) = if (Os.isWindows) "npm.cmd" else "npm"

    override fun getVersionRequirement(): RangesList = RangesListFactory.create("6.* - 10.*")

    override fun mapDefinitionFiles(definitionFiles: List<File>) =
        NpmDetection(definitionFiles).filterApplicable(NodePackageManager.NPM)

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

    // TODO: Add support for bundledDependencies.
    private fun resolveDependenciesInternal(definitionFile: File): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile

        // Actually installing the dependencies is the easiest way to get the metadata of all transitive
        // dependencies (i.e. their respective "package.json" files). As NPM uses a global cache, the same
        // dependency is only ever downloaded once.
        val installIssues = installDependencies(workingDir)

        if (installIssues.any { it.severity == Severity.ERROR }) {
            val project = runCatching {
                parseProject(definitionFile, analysisRoot, managerName)
            }.getOrElse {
                logger.error { "Failed to parse project information: ${it.collectMessages()}" }
                Project.EMPTY
            }

            return listOf(ProjectAnalyzerResult(project, emptySet(), installIssues))
        }

        val projectDirs = findWorkspaceSubmodules(workingDir).toSet() + definitionFile.parentFile

        return projectDirs.map { projectDir ->
            val issues = mutableListOf<Issue>().apply {
                if (projectDir == workingDir) addAll(installIssues)
            }

            val project = runCatching {
                parseProject(projectDir.resolve("package.json"), analysisRoot, managerName)
            }.getOrElse {
                issues += createAndLogIssue(
                    source = managerName,
                    message = "Failed to parse project information: ${it.collectMessages()}"
                )

                Project.EMPTY
            }

            val scopeNames = setOfNotNull(
                // Optional dependencies are just like regular dependencies except that NPM ignores failures when
                // installing them (see https://docs.npmjs.com/files/package.json#optionaldependencies), i.e. they are
                // not a separate scope in ORT semantics.
                buildDependencyGraphForScopes(
                    project,
                    projectDir,
                    setOf(DEPENDENCIES_SCOPE, OPTIONAL_DEPENDENCIES_SCOPE),
                    DEPENDENCIES_SCOPE,
                    projectDirs,
                    workspaceDir = workingDir
                ),

                buildDependencyGraphForScopes(
                    project,
                    projectDir,
                    setOf(DEV_DEPENDENCIES_SCOPE),
                    DEV_DEPENDENCIES_SCOPE,
                    projectDirs,
                    workspaceDir = workingDir
                )
            )

            ProjectAnalyzerResult(
                project = project.copy(scopeNames = scopeNames),
                // Packages are set later by createPackageManagerResult().
                packages = emptySet(),
                issues = issues
            )
        }
    }

    /**
     * Construct a [Package] by parsing its _package.json_ file and - if applicable - querying additional
     * content via the `npm view` command. The result is a [Pair] with the raw identifier and the new package.
     */
    internal suspend fun parsePackage(workingDir: File, packageJsonFile: File): Package {
        val packageDir = packageJsonFile.parentFile

        logger.debug { "Found a 'package.json' file in '$packageDir'." }

        val packageJson = parsePackageJson(packageJsonFile)

        // The "name" and "version" fields are only required if the package is going to be published, otherwise they are
        // optional, see
        // - https://docs.npmjs.com/cli/v10/configuring-npm/package-json#name
        // - https://docs.npmjs.com/cli/v10/configuring-npm/package-json#version
        // So, projects analyzed by ORT might not have these fields set.
        val rawName = packageJson.name.orEmpty() // TODO: Fall back to a generated name if the name is unset.
        val (namespace, name) = splitNpmNamespaceAndName(rawName)
        val version = packageJson.version ?: NON_EXISTING_SEMVER

        val declaredLicenses = packageJson.licenses.mapNpmLicenses()
        val authors = parseNpmAuthor(packageJson.authors.firstOrNull()) // TODO: parse all authors.

        var description = packageJson.description.orEmpty()
        var homepageUrl = packageJson.homepage.orEmpty()

        // Note that all fields prefixed with "_" are considered private to NPM and should not be relied on.
        var downloadUrl = expandNpmShortcutUrl(packageJson.resolved.orEmpty()).ifEmpty {
            // If the normalized form of the specified dependency contains a URL as the version, expand and use it.
            val fromVersion = packageJson.from.orEmpty().substringAfterLast('@')
            expandNpmShortcutUrl(fromVersion).takeIf { it != fromVersion }.orEmpty()
        }

        var hash = Hash.create(packageJson.integrity.orEmpty())

        var vcsFromPackage = parseNpmVcsInfo(packageJson)

        val id = Identifier("NPM", namespace, name, version)

        val hasIncompleteData = description.isEmpty() || homepageUrl.isEmpty() || downloadUrl.isEmpty()
            || hash == Hash.NONE || vcsFromPackage == VcsInfo.EMPTY

        if (hasIncompleteData) {
            runCatching {
                getRemotePackageDetailsAsync(workingDir, "$rawName@$version").await()
            }.onSuccess { details ->
                if (description.isEmpty()) description = details.description.orEmpty()
                if (homepageUrl.isEmpty()) homepageUrl = details.homepage.orEmpty()

                details.dist?.let { dist ->
                    if (downloadUrl.isEmpty() || hash == Hash.NONE) {
                        downloadUrl = dist.tarball.orEmpty()
                        hash = Hash.create(dist.shasum.orEmpty())
                    }
                }

                // Do not replace but merge, because it happens that `package.json` has VCS info while
                // `npm view` doesn't, for example for dependencies hosted on GitLab package registry.
                vcsFromPackage = vcsFromPackage.merge(parseNpmVcsInfo(details))
            }.onFailure { e ->
                logger.debug { "Unable to get package details from a remote registry: ${e.collectMessages()}" }
            }
        }

        downloadUrl = downloadUrl.fixNpmDownloadUrl()

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

        return module
    }

    private suspend fun getRemotePackageDetailsAsync(workingDir: File, packageName: String): Deferred<PackageJson> =
        withContext(Dispatchers.IO) {
            npmViewCache.getOrPut(packageName) {
                async {
                    getRemotePackageDetails(workingDir, packageName)
                }
            }
        }

    protected open fun getRemotePackageDetails(workingDir: File, packageName: String): PackageJson {
        val process = run(workingDir, "info", "--json", packageName)
        return parsePackageJson(process.stdout)
    }

    /** Cache for submodules identified by its moduleDir absolutePath */
    private val submodulesCache = ConcurrentHashMap<String, Set<File>>()

    /**
     * Find the directories which are defined as submodules of the project within [moduleDir].
     */
    protected fun findWorkspaceSubmodules(moduleDir: File): Set<File> =
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
        targetScope: String,
        projectDirs: Set<File>,
        workspaceDir: File? = null
    ): String? {
        if (excludes.isScopeExcluded(targetScope)) return null

        val qualifiedScopeName = DependencyGraph.qualifyScope(project, targetScope)
        val moduleInfo = checkNotNull(getModuleInfo(workingDir, scopes, projectDirs, listOfNotNull(workspaceDir)))

        moduleInfo.dependencies.forEach { graphBuilder.addDependency(qualifiedScopeName, it) }

        return targetScope.takeUnless { moduleInfo.dependencies.isEmpty() }
    }

    private fun getModuleInfo(
        moduleDir: File,
        scopes: Set<String>,
        projectDirs: Set<File>,
        ancestorModuleDirs: List<File> = emptyList(),
        ancestorModuleIds: List<Identifier> = emptyList()
    ): NpmModuleInfo? {
        val moduleInfo = parsePackageJson(moduleDir, scopes)
        val dependencies = mutableSetOf<NpmModuleInfo>()
        val packageType = managerName.takeIf { moduleDir.realFile() in projectDirs } ?: "NPM"

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
                    projectDirs,
                    ancestorModuleDirs = dependencyModuleDirPath.subList(1, dependencyModuleDirPath.size),
                    ancestorModuleIds = ancestorModuleIds + moduleId
                )?.let { dependencies += it }

                return@forEach
            }

            logger.debug {
                "It seems that the '$dependencyName' module was not installed as the package file could not be found " +
                    "anywhere in '${pathToRoot.joinToString()}'. This might be fine if the module is specific to a " +
                    "platform other than the one ORT is running on. A typical example is the 'fsevents' module."
            }
        }

        return NpmModuleInfo(
            id = moduleId,
            workingDir = moduleDir,
            packageFile = moduleInfo.packageJson,
            dependencies = dependencies,
            isProject = moduleDir.realFile() in projectDirs
        )
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

    /**
     * Install dependencies using the given package manager command.
     */
    private fun installDependencies(workingDir: File): List<Issue> {
        requireLockfile(workingDir) { hasLockfile(workingDir) }

        // Install all NPM dependencies to enable NPM to list dependencies.
        val process = runInstall(workingDir)

        val lines = process.stderr.lines()
        val issues = mutableListOf<Issue>()

        // Generally forward issues from the NPM CLI to the ORT NPM package manager. Lower the severity of warnings to
        // hints, as warnings usually do not prevent the ORT NPM package manager from getting the dependencies right.
        lines.groupLines("npm WARN ", "npm warn ").mapTo(issues) {
            Issue(source = managerName, message = it, severity = Severity.HINT)
        }

        // For errors, however, something clearly went wrong, so keep the severity here.
        lines.groupLines("npm ERR! ", "npm error ").mapTo(issues) {
            Issue(source = managerName, message = it, severity = Severity.ERROR)
        }

        return issues
    }

    protected open fun runInstall(workingDir: File): ProcessCapture {
        val options = listOfNotNull(
            "--ignore-scripts",
            "--no-audit",
            "--legacy-peer-deps".takeIf { legacyPeerDeps }
        )

        val subcommand = if (hasLockfile(workingDir)) "ci" else "install"
        return ProcessCapture(workingDir, command(workingDir), subcommand, *options.toTypedArray())
    }
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

internal fun List<String>.groupLines(vararg markers: String): List<String> {
    val ignorableLinePrefixes = setOf("code ", "errno ", "path ", "syscall ")
    val singleLinePrefixes = setOf("deprecated ", "skipping integrity check for git dependency ")
    val minCommonPrefixLength = 5

    val issueLines = mapNotNull { line ->
        markers.firstNotNullOfOrNull { marker ->
            line.withoutPrefix(marker)?.takeUnless { ignorableLinePrefixes.any { prefix -> it.startsWith(prefix) } }
        }
    }

    var commonPrefix: String
    var previousPrefix = ""

    val collapsedLines = issueLines.distinct().fold(mutableListOf<String>()) { messages, line ->
        if (messages.isEmpty()) {
            // The first line is always added including the prefix. The prefix will be removed later.
            messages += line
        } else {
            // Find the longest common prefix that ends with space.
            commonPrefix = line.commonPrefixWith(messages.last())
            if (!commonPrefix.endsWith(' ')) {
                // Deal with prefixes being used on their own as separators.
                commonPrefix = if ("$commonPrefix " == previousPrefix || line.startsWith("$commonPrefix ")) {
                    "$commonPrefix "
                } else {
                    commonPrefix.dropLastWhile { it != ' ' }
                }
            }

            if (commonPrefix !in singleLinePrefixes && commonPrefix.length >= minCommonPrefixLength) {
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

    val nonFooterLines = collapsedLines.takeWhile {
        // Skip any footer as a whole.
        it != "A complete log of this run can be found in:"
    }

    // If no lines but the last end with a dot, assume the message to be a single sentence.
    return if (
        nonFooterLines.size > 1 &&
        nonFooterLines.last().endsWith('.') &&
        nonFooterLines.subList(0, nonFooterLines.size - 1).none { it.endsWith('.') }
    ) {
        listOf(nonFooterLines.joinToString(" "))
    } else {
        nonFooterLines.map { it.trim() }
    }
}

internal fun parseProject(packageJsonFile: File, analysisRoot: File, managerName: String): Project {
    Npm.logger.debug { "Parsing project info from '$packageJsonFile'." }

    val packageJson = parsePackageJson(packageJsonFile)

    val rawName = packageJson.name.orEmpty()
    val (namespace, name) = splitNpmNamespaceAndName(rawName)

    val projectName = name.ifBlank {
        getFallbackProjectName(analysisRoot, packageJsonFile).also {
            Npm.logger.warn { "'$packageJsonFile' does not define a name, falling back to '$it'." }
        }
    }

    val version = packageJson.version.orEmpty()
    if (version.isBlank()) {
        Npm.logger.warn { "'$packageJsonFile' does not define a version." }
    }

    val declaredLicenses = packageJson.licenses.mapNpmLicenses()
    val authors = parseNpmAuthor(packageJson.authors.firstOrNull()) // TODO: parse all authors.
    val homepageUrl = packageJson.homepage.orEmpty()
    val projectDir = packageJsonFile.parentFile.realFile()
    val vcsFromPackage = parseNpmVcsInfo(packageJson)

    return Project(
        id = Identifier(
            type = managerName,
            namespace = namespace,
            name = projectName,
            version = version
        ),
        definitionFilePath = VersionControlSystem.getPathInfo(packageJsonFile.realFile()).path,
        authors = authors,
        declaredLicenses = declaredLicenses,
        vcs = vcsFromPackage,
        vcsProcessed = processProjectVcs(projectDir, vcsFromPackage, homepageUrl),
        homepageUrl = homepageUrl
    )
}
