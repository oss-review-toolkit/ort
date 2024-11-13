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

package org.ossreviewtoolkit.plugins.packagemanagers.node.yarn

import java.io.File
import java.lang.invoke.MethodHandles
import java.util.concurrent.ConcurrentHashMap

import kotlin.time.Duration.Companion.days

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeToSequence
import kotlinx.serialization.json.jsonPrimitive

import org.apache.logging.log4j.kotlin.logger
import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.readTree
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.plugins.packagemanagers.node.NodePackageManager
import org.ossreviewtoolkit.plugins.packagemanagers.node.NpmDetection
import org.ossreviewtoolkit.plugins.packagemanagers.node.PackageJson
import org.ossreviewtoolkit.plugins.packagemanagers.node.parsePackageJson
import org.ossreviewtoolkit.plugins.packagemanagers.node.parseProject
import org.ossreviewtoolkit.plugins.packagemanagers.node.splitNpmNamespaceAndName
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.DiskCache
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.alsoIfNull
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.fieldNamesOrEmpty
import org.ossreviewtoolkit.utils.common.isSymbolicLink
import org.ossreviewtoolkit.utils.common.mebibytes
import org.ossreviewtoolkit.utils.common.realFile
import org.ossreviewtoolkit.utils.common.stashDirectories
import org.ossreviewtoolkit.utils.common.textValueOrEmpty
import org.ossreviewtoolkit.utils.ort.ortDataDirectory

import org.semver4j.RangesList
import org.semver4j.RangesListFactory

private val yarnInfoCache = DiskCache(
    directory = ortDataDirectory.resolve("cache/analyzer/yarn/info"),
    maxCacheSizeInBytes = 100.mebibytes,
    maxCacheEntryAgeInSeconds = 7.days.inWholeSeconds
)

/** Name of the scope with the regular dependencies. */
private const val DEPENDENCIES_SCOPE = "dependencies"

/** Name of the scope with optional dependencies. */
private const val OPTIONAL_DEPENDENCIES_SCOPE = "optionalDependencies"

/** Name of the scope with development dependencies. */
private const val DEV_DEPENDENCIES_SCOPE = "devDependencies"

/**
 * The [Yarn](https://classic.yarnpkg.com/) package manager for JavaScript.
 */
open class Yarn(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, "Yarn", analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<Yarn>("Yarn") {
        override val globsForDefinitionFiles = listOf("package.json")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Yarn(type, analysisRoot, analyzerConfig, repoConfig)
    }

    /** Cache for submodules identified by its moduleDir absolutePath */
    private val submodulesCache = ConcurrentHashMap<String, Set<File>>()

    private val rawModuleInfoCache = mutableMapOf<Pair<File, Set<String>>, RawModuleInfo>()

    private val graphBuilder by lazy { DependencyGraphBuilder(YarnDependencyHandler(this)) }

    protected open fun hasLockfile(projectDir: File) = NodePackageManager.YARN.hasLockfile(projectDir)

    /**
     * Load the submodule directories of the project defined in [moduleDir].
     */
    private fun loadWorkspaceSubmodules(moduleDir: File): Set<File> {
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

    override fun command(workingDir: File?) = if (Os.isWindows) "yarn.cmd" else "yarn"

    override fun getVersionRequirement(): RangesList = RangesListFactory.create("1.3.* - 1.22.*")

    override fun mapDefinitionFiles(definitionFiles: List<File>) =
        NpmDetection(definitionFiles).filterApplicable(NodePackageManager.YARN)

    override fun beforeResolution(definitionFiles: List<File>) =
        // We do not actually depend on any features specific to a Yarn version, but we still want to stick to a
        // fixed minor version to be sure to get consistent results.
        checkVersion()

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

    // TODO: Add support for bundledDependencies.
    private fun resolveDependenciesInternal(definitionFile: File): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile
        installDependencies(workingDir)

        val projectDirs = findWorkspaceSubmodules(workingDir).toSet() + definitionFile.parentFile

        return projectDirs.map { projectDir ->
            val issues = mutableListOf<Issue>()

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

        val moduleInfo = checkNotNull(getModuleInfo(workingDir, scopes, projectDirs, listOfNotNull(workspaceDir)))

        graphBuilder.addDependencies(project.id, targetScope, moduleInfo.dependencies)

        return targetScope.takeUnless { moduleInfo.dependencies.isEmpty() }
    }

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
     * Find the directories which are defined as submodules of the project within [moduleDir].
     */
    private fun findWorkspaceSubmodules(moduleDir: File): Set<File> =
        submodulesCache.getOrPut(moduleDir.absolutePath) {
            loadWorkspaceSubmodules(moduleDir)
        }

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())

    private fun installDependencies(workingDir: File) {
        requireLockfile(workingDir) { hasLockfile(workingDir) }

        run(workingDir, "install", "--ignore-scripts", "--ignore-engines", "--immutable")
    }

    internal open fun getRemotePackageDetails(workingDir: File, packageName: String): PackageJson? {
        yarnInfoCache.read(packageName)?.let { return parsePackageJson(it) }

        val process = run(workingDir, "info", "--json", packageName)

        return parseYarnInfo(process.stdout, process.stderr)?.also {
            yarnInfoCache.write(packageName, Json.encodeToString(it))
        }
    }
}

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

/**
 * Parse the given [stdout] of a Yarn _info_ command to a [PackageJson]. The output is typically a JSON object with the
 * metadata of the package that was queried. However, under certain circumstances, Yarn may return multiple JSON objects
 * separated by newlines; for instance, if the operation is retried due to network problems. This function filters for
 * the object with the data based on the _type_ field. Result is *null* if no matching object is found or the input is
 * not valid JSON.
 *
 * Note: The mentioned network issue can be reproduced by setting the network timeout to be very short via the command
 * line option '--network-timeout'.
 */
internal fun parseYarnInfo(stdout: String, stderr: String): PackageJson? =
    extractDataNodes(stdout, "inspect").firstOrNull()?.let(::parsePackageJson).alsoIfNull {
        extractDataNodes(stderr, "warning").forEach {
            logger.info { "Warning running Yarn info: ${it.jsonPrimitive.content}" }
        }

        extractDataNodes(stderr, "error").forEach {
            logger.warn { "Error parsing Yarn info: ${it.jsonPrimitive.content}" }
        }
    }

private fun extractDataNodes(output: String, type: String): Set<JsonElement> =
    runCatching {
        output.byteInputStream().use { inputStream ->
            Json.decodeToSequence<JsonObject>(inputStream)
                .filter { (it["type"] as? JsonPrimitive)?.content == type }
                .mapNotNullTo(mutableSetOf()) { it["data"] }
        }
    }.getOrDefault(emptySet())

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
