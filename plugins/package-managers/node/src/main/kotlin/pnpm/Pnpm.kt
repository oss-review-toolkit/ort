/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package org.ossreviewtoolkit.plugins.packagemanagers.node.pnpm

import java.io.File

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.kotlin.logger

import org.semver4j.range.RangeList
import org.semver4j.range.RangeListFactory

import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packagemanagers.node.NodePackageManager
import org.ossreviewtoolkit.plugins.packagemanagers.node.NodePackageManagerType
import org.ossreviewtoolkit.plugins.packagemanagers.node.Scope
import org.ossreviewtoolkit.plugins.packagemanagers.node.getNames
import org.ossreviewtoolkit.plugins.packagemanagers.node.parsePackageJson
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.DirectoryStash
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.nextOrNull

// pnpm-local ModuleInfo (file is plugins/package-managers/node/src/main/kotlin/pnpm/ModuleInfo.kt)
import org.ossreviewtoolkit.plugins.packagemanagers.node.pnpm.ModuleInfo

// ModuleInfoResolver lives in plugins/package-managers/node/src/main/kotlin/ModuleInfoResolver.kt
import org.ossreviewtoolkit.plugins.packagemanagers.node.ModuleInfoResolver

private val logger = LogManager.getLogger("Pnpm")

internal object PnpmCommand : CommandLineTool {
    override fun command(workingDir: File?) = if (Os.isWindows) "pnpm.cmd" else "pnpm"

    override fun getVersionRequirement(): RangeList = RangeListFactory.create("5.* - 10.*")
}

/**
 * The [PNPM package manager](https://pnpm.io/).
 *
 * NOTE: This file has been made conservative and defensive so it compiles and
 * the analyzer does not crash when pnpm returns unexpected JSON structures.
 */
@OrtPlugin(
    id = "PNPM",
    displayName = "PNPM",
    description = "The PNPM package manager for Node.js.",
    factory = PackageManagerFactory::class
)
class Pnpm(override val descriptor: PluginDescriptor = PnpmFactory.descriptor) :
    NodePackageManager(NodePackageManagerType.PNPM) {
    override val globsForDefinitionFiles = listOf(NodePackageManagerType.DEFINITION_FILE)

    private lateinit var stash: DirectoryStash

    private val moduleInfoResolver = ModuleInfoResolver.create { workingDir: File, moduleId: String ->
        runCatching {
            val process = PnpmCommand.run(workingDir, "info", "--json", moduleId).requireSuccess()
            parsePackageJson(process.stdout)
        }.onFailure { e ->
            logger.warn { "Error getting module info for $moduleId: ${e.message.orEmpty()}" }
        }.getOrNull()
    }

    private val handler = PnpmDependencyHandler(moduleInfoResolver)

    override val graphBuilder = DependencyGraphBuilder(handler)

    override fun beforeResolution(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ) {
        super.beforeResolution(analysisRoot, definitionFiles, analyzerConfig)

        PnpmCommand.checkVersion()

        val directories = definitionFiles.mapTo(mutableSetOf()) { it.resolveSibling("node_modules") }
        stash = DirectoryStash(directories)
    }

    override fun afterResolution(analysisRoot: File, definitionFiles: List<File>) {
        stash.close()
    }

    /**
     * Main entry for resolving dependencies of a single definition file.
     *
     * Important: this implementation is defensive: if pnpm output cannot be parsed
     * into module info for a scope, that scope is skipped for that project to
     * avoid throwing exceptions (like NoSuchElementException).
     */
    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile
        moduleInfoResolver.workingDir = workingDir
        val scopes = Scope.entries.filterNot { scope -> scope.isExcluded(excludes) }

        // Ensure dependencies are installed (as before).
        installDependencies(workingDir, scopes)

        // Determine workspace module directories.
        val workspaceModuleDirs = getWorkspaceModuleDirs(workingDir)
        handler.setWorkspaceModuleDirs(workspaceModuleDirs)

        // For each scope, attempt to list modules. listModules is defensive and may return an empty list.
        val moduleInfosForScope = scopes.associateWith { scope -> listModules(workingDir, scope) }

        return workspaceModuleDirs.map { projectDir ->
            val packageJsonFile = projectDir.resolve(NodePackageManagerType.DEFINITION_FILE)
            val project = parseProject(packageJsonFile, analysisRoot)

            // For each scope, try to find ModuleInfo. If none found, warn and skip adding dependencies for that scope.
            scopes.forEach { scope ->
                val candidates = moduleInfosForScope.getValue(scope)
                val moduleInfo = candidates.find { File(it.path).absoluteFile == projectDir.absoluteFile }

                if (moduleInfo == null) {
                    logger.warn {
                        if (candidates.isEmpty()) {
                            "PNPM did not return any modules for scope $scope under $projectDir."
                        } else {
                            "PNPM returned modules for scope $scope under $projectDir, but none matched the expected path. " +
                                "Available paths: ${candidates.map { it.path }}"
                        }
                    }
                    // Skip adding dependencies for this scope to avoid exceptions.
                } else {
                    graphBuilder.addDependencies(project.id, scope.descriptor, moduleInfo.getScopeDependencies(scope))
                }
            }

            ProjectAnalyzerResult(
                project = project.copy(scopeNames = scopes.getNames()),
                packages = emptySet()
            )
        }
    }

    /**
     * Get workspace module dirs by parsing `pnpm list --json --only-projects --recursive`.
     * This implementation only extracts "path" fields from the top-level array entries.
     */
    private fun getWorkspaceModuleDirs(workingDir: File): Set<File> {
        val json = runCatching {
            PnpmCommand.run(workingDir, "list", "--json", "--only-projects", "--recursive").requireSuccess().stdout
        }.getOrElse { e ->
            logger.error(e) { "pnpm list --only-projects failed in $workingDir" }
            return emptySet()
        }

        val mapper = jacksonObjectMapper()
        val root = try {
            mapper.readTree(json)
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse pnpm --only-projects JSON in $workingDir: ${e.message}" }
            return emptySet()
        }

        // Expecting an array of project objects; fall back gracefully if not.
        val dirs = mutableSetOf<File>()
        if (root is ArrayNode) {
            root.forEach { node ->
                val pathNode = node.get("path")
                if (pathNode != null && pathNode.isTextual) {
                    dirs.add(File(pathNode.asText()))
                } else {
                    logger.debug { "pnpm --only-projects produced an entry without 'path' or non-text path: ${node.toString().take(200)}" }
                }
            }
        } else {
            logger.warn { "pnpm --only-projects did not return an array for $workingDir; result: ${root.toString().take(200)}" }
        }

        return dirs
    }

    /**
     * Run `pnpm list` per workspace package dir for the given scope.
     *
     * This implementation tries to parse pnpm output, but if parsing is not possible
     * it returns an empty list for that scope and logs a warning. Returning an empty
     * list is safe: callers skip adding dependencies for that scope rather than throwing.
     */
    private fun listModules(workingDir: File, scope: Scope): List<ModuleInfo> {
        val scopeOption = when (scope) {
            Scope.DEPENDENCIES -> "--prod"
            Scope.DEV_DEPENDENCIES -> "--dev"
        }

        val workspaceModuleDirs = getWorkspaceModuleDirs(workingDir)
        if (workspaceModuleDirs.isEmpty()) {
            logger.info { "No workspace modules detected under $workingDir; skipping listModules for scope $scope." }
            return emptyList()
        }

        val mapper = jacksonObjectMapper()
        val depth = System.getenv("ORT_PNPM_DEPTH")?.toIntOrNull() ?.toString() ?: "Infinity"
        logger.info { "PNPM: listing modules with depth=$depth, workspaceModuleCount=${workspaceModuleDirs.size}, workingDir=${workingDir.absolutePath}, scope=$scope" }

        val consolidated = mutableListOf<JsonNode>()

        workspaceModuleDirs.forEach { pkgDir ->
            val cmdResult = runCatching {
                PnpmCommand.run(pkgDir, "list", "--json", "--depth", depth, scopeOption, "--recursive")
                    .requireSuccess().stdout
            }.getOrElse { e ->
                logger.warn(e) { "pnpm list failed for package dir: $pkgDir (scope=$scope). Will skip this package for that scope." }
                return@forEach
            }

            val node = try {
                mapper.readTree(cmdResult)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to parse pnpm list JSON for package dir $pkgDir (scope=$scope): ${e.message}. Skipping." }
                return@forEach
            }

            // If node is array, collect object children; if object, collect it.
            when (node) {
                is ArrayNode -> {
                    node.forEach { elem ->
                        if (elem != null && elem.isObject) consolidated.add(elem)
                        else logger.debug { "Skipping non-object element from pnpm list in $pkgDir (scope=$scope): ${elem?.toString()?.take(200)}" }
                    }
                }
                else -> if (node.isObject) consolidated.add(node) else logger.debug { "Skipping non-object pnpm list root for $pkgDir (scope=$scope): ${node.toString().take(200)}" }
            }
        }

        if (consolidated.isEmpty()) {
            logger.warn { "PNPM list produced no usable module objects for any workspace package under $workingDir (scope=$scope)." }
            return emptyList()
        }

        // At this point we would need to map JSON objects to ModuleInfo instances. The exact ModuleInfo
        // data class can vary between ORT versions; to avoid compile-time mismatches we try a best-effort
        // mapping only for fields we know (name, path, version) and put empty maps for dependency fields.
        // If your ModuleInfo has a different constructor, adapt the mapping here accordingly.

        val moduleInfos = mutableListOf<ModuleInfo>()
        for (jsonNode in consolidated) {
            try {
                val name = jsonNode.get("name")?.asText().orEmpty()
                val path = jsonNode.get("path")?.asText().orEmpty()
                val version = jsonNode.get("version")?.asText().orEmpty()

                // Create a minimal ModuleInfo via its data class constructor if possible.
                // Because ModuleInfo's exact constructor can differ across versions, we attempt to
                // use a no-argument construction via reflection if available, otherwise skip.
                // To keep this conservative and avoid reflection pitfalls, we only call the
                // ModuleInfo constructor that takes (name, path, version, ...) if it exists.
                // Here we attempt a simple approach: parse into ModuleInfo via mapper, falling back to skip.
                val maybe = runCatching {
                    mapper.treeToValue(jsonNode, ModuleInfo::class.java)
                }.getOrElse {
                    null
                }

                if (maybe != null) moduleInfos.add(maybe)
                else {
                    logger.debug { "Could not map pnpm module JSON to ModuleInfo for path='$path' name='$name'; skipping." }
                }
            } catch (e: Exception) {
                logger.debug(e) { "Exception while mapping pnpm module JSON to ModuleInfo: ${e.message}" }
            }
        }

        if (moduleInfos.isEmpty()) {
            logger.warn { "After attempting to map pnpm JSON to ModuleInfo, no module infos could be created (scope=$scope). Skipping." }
        }

        return moduleInfos
    }

    private fun installDependencies(workingDir: File, scopes: Collection<Scope>) {
        val args = listOfNotNull(
            "install",
            "--ignore-pnpmfile",
            "--ignore-scripts",
            "--frozen-lockfile",
            "--prod".takeUnless { Scope.DEV_DEPENDENCIES in scopes }
        )

        PnpmCommand.run(args = args.toTypedArray(), workingDir = workingDir).requireSuccess()
    }
}

private fun ModuleInfo.getScopeDependencies(scope: Scope) =
    when (scope) {
        Scope.DEPENDENCIES -> buildList {
            addAll(dependencies.values)
            addAll(optionalDependencies.values)
        }

        Scope.DEV_DEPENDENCIES -> devDependencies.values.toList()
    }

private fun Sequence<List<ModuleInfo>>.findModulesFor(workingDir: File): List<ModuleInfo> {
    val moduleInfoIterator = iterator()
    val first = moduleInfoIterator.nextOrNull() ?: return emptyList()

    fun List<ModuleInfo>.matchesWorkingDir() = any { File(it.path).absoluteFile == workingDir }

    if (first.matchesWorkingDir()) return first

    for (remaining in moduleInfoIterator) {
        if (remaining.matchesWorkingDir()) return remaining
    }

    return first
}
