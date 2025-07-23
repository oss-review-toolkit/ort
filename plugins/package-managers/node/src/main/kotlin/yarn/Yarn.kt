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
import java.util.LinkedList

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeToSequence
import kotlinx.serialization.json.jsonPrimitive

import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packagemanagers.node.ModuleInfoResolver
import org.ossreviewtoolkit.plugins.packagemanagers.node.NodePackageManager
import org.ossreviewtoolkit.plugins.packagemanagers.node.NodePackageManagerType
import org.ossreviewtoolkit.plugins.packagemanagers.node.PackageJson
import org.ossreviewtoolkit.plugins.packagemanagers.node.Scope
import org.ossreviewtoolkit.plugins.packagemanagers.node.getDependenciesForScope
import org.ossreviewtoolkit.plugins.packagemanagers.node.getInstalledModulesDirs
import org.ossreviewtoolkit.plugins.packagemanagers.node.getNames
import org.ossreviewtoolkit.plugins.packagemanagers.node.parsePackageJson
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.DirectoryStash
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.alsoIfNull

import org.semver4j.range.RangeList
import org.semver4j.range.RangeListFactory

internal object YarnCommand : CommandLineTool {
    override fun command(workingDir: File?) = if (Os.isWindows) "yarn.cmd" else "yarn"

    override fun getVersionRequirement(): RangeList = RangeListFactory.create("1.3.* - 1.22.*")
}

/**
 * The [Yarn package manager](https://classic.yarnpkg.com/).
 */
@OrtPlugin(
    displayName = "Yarn",
    description = "The Yarn package manager for Node.js.",
    factory = PackageManagerFactory::class
)
class Yarn(override val descriptor: PluginDescriptor = YarnFactory.descriptor) :
    NodePackageManager(NodePackageManagerType.YARN) {
    override val globsForDefinitionFiles = listOf(NodePackageManagerType.DEFINITION_FILE)

    private lateinit var stash: DirectoryStash

    private val moduleInfoResolver = ModuleInfoResolver.create { workingDir, moduleId ->
        val process = YarnCommand.run(workingDir, "info", "--json", moduleId)

        parseYarnInfo(process.stdout, process.stderr)
    }

    private val handler = YarnDependencyHandler(moduleInfoResolver)
    override val graphBuilder = DependencyGraphBuilder(handler)

    override fun beforeResolution(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ) {
        super.beforeResolution(analysisRoot, definitionFiles, analyzerConfig)

        YarnCommand.checkVersion()

        val directories = definitionFiles.mapTo(mutableSetOf()) { it.resolveSibling("node_modules") }
        stash = DirectoryStash(directories)
    }

    override fun afterResolution(analysisRoot: File, definitionFiles: List<File>) {
        stash.close()
    }

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile
        moduleInfoResolver.workingDir = workingDir
        installDependencies(workingDir)

        val workspaceModuleDirs = getWorkspaceModuleDirs(workingDir)
        handler.setContext(workingDir, getInstalledModulesDirs(workingDir), workspaceModuleDirs)

        val scopes = Scope.entries.filterNot { scope -> scope.isExcluded(excludes) }
        val moduleInfosForScope = scopes.associateWith { scope ->
            listModules(workingDir, scope).removeDanglingLinks(workingDir).resolveVersions().undoDeduplication()
        }

        // Warm-up the cache to speed-up processing.
        getAllModuleIds(moduleInfosForScope.values.flatten()).let { moduleIds ->
            moduleInfoResolver.getModuleInfos(moduleIds)
        }

        return workspaceModuleDirs.map { projectDir ->
            val packageJsonFile = projectDir.resolve(NodePackageManagerType.DEFINITION_FILE)
            val packageJson = parsePackageJson(packageJsonFile)
            val project = parseProject(packageJsonFile, analysisRoot)

            scopes.forEach { scope ->
                val moduleInfos = if (projectDir == workingDir.absoluteFile) {
                    moduleInfosForScope.getValue(scope)
                } else {
                    moduleInfosForScope.getValue(scope).single { it.moduleName == packageJson.name }.children.orEmpty()
                }

                val dependencies = moduleInfos.filter { it.moduleName in packageJson.getDependenciesForScope(scope) }

                graphBuilder.addDependencies(project.id, scope.descriptor, dependencies)
            }

            ProjectAnalyzerResult(
                project = project.copy(scopeNames = scopes.getNames()),
                packages = emptySet()
            )
        }
    }

    private fun installDependencies(workingDir: File) =
        YarnCommand.run(
            workingDir,
            "install",
            "--ignore-scripts",
            "--ignore-engines",
            "--immutable"
        ).requireSuccess()

    private fun getWorkspaceModuleDirs(workingDir: File): Set<File> {
        val result = mutableSetOf(workingDir.absoluteFile)

        val process = YarnCommand.run(
            workingDir,
            "workspaces",
            "--json",
            "info"
        )

        // If the package.json does not define a workspace, the command fails.
        if (process.isSuccess) {
            parseWorkspaceInfo(process.stdout).mapTo(result) { workingDir.resolve(it.value.location) }
        }

        return result
    }

    private fun listModules(workingDir: File, scope: Scope): List<YarnListNode> {
        val scopeOption = when (scope) {
            Scope.DEPENDENCIES -> "--prod"
            Scope.DEV_DEPENDENCIES -> "--dev"
        }

        val json = YarnCommand.run(workingDir, "list", "--json", scopeOption).requireSuccess().stdout

        return parseYarnList(json)
    }
}

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

private fun getNonDeduplicatedModuleInfosForId(moduleInfos: Collection<YarnListNode>): Map<String, YarnListNode> {
    val queue = LinkedList<YarnListNode>().apply { addAll(moduleInfos) }
    val result = mutableMapOf<String, YarnListNode>()

    while (queue.isNotEmpty()) {
        val node = queue.removeFirst()

        if (node.children != null) { // The tree is truncated (de-duped) at nodes which have `children == null`.
            result[node.name] = node.copy(color = "bold")
        }

        queue += node.children.orEmpty()
    }

    return result
}

private data class ModuleReference(
    val alias: String?,
    val name: String,
    val version: String,
    val linkPath: String?
)

private val YarnListNode.moduleReference: ModuleReference get() {
    if ("@link:" in name) {
        return ModuleReference(
            alias = null,
            name = name.substringBefore("@link:"),
            version = "",
            linkPath = name.substringAfter("@link:")
        )
    }

    val parts = name.split(Regex("@npm:"))
    val (alias, nameAndVersion) = if (parts.size == 2) {
        parts[0] to parts[1]
    } else {
        null to parts[0]
    }

    return ModuleReference(
        alias = alias,
        name = nameAndVersion.substringBeforeLast("@"),
        version = nameAndVersion.substringAfterLast("@"),
        linkPath = null
    )
}

internal val YarnListNode.moduleAlias: String get() = moduleReference.alias ?: moduleName

internal val YarnListNode.moduleName: String get() = moduleReference.name

internal val YarnListNode.moduleVersion: String get() = moduleReference.version

internal val YarnListNode.linkPath: String? get() = moduleReference.linkPath

private fun List<YarnListNode>.removeDanglingLinks(workingDir: File): List<YarnListNode> =
    map {
        it.copy(children = it.children?.removeDanglingLinks(workingDir))
    }.filterNot { node ->
        node.linkPath?.let { workingDir.resolve(it) }?.isDirectory ?: false
    }

private fun List<YarnListNode>.resolveVersions(): List<YarnListNode> {
    fun YarnListNode.resolveVersions(versionForAlias: Map<String, String> = emptyMap()): YarnListNode {
        if (children == null) return copy(name = "$moduleName@${versionForAlias.getValue(moduleAlias)}")

        val childrenVersionForAlias = buildMap {
            putAll(versionForAlias)

            children.forEach { node ->
                if (node.children != null) put(node.moduleAlias, node.moduleVersion)
            }
        }

        return copy(children = children.map { it.resolveVersions(childrenVersionForAlias) })
    }

    return YarnListNode("", this).resolveVersions().children.orEmpty()
}

private fun List<YarnListNode>.undoDeduplication(): List<YarnListNode> {
    val replacements = getNonDeduplicatedModuleInfosForId(this)

    fun YarnListNode.undoDeduplication(ancestorNames: Set<String> = emptySet()): YarnListNode? {
        // break cycles.
        if (name in ancestorNames) return null
        // Disregard entries which are not a dependency, but only installed in the module's dir for de-duplication.
        if (color == null) return null

        val childrenAncestorIds = ancestorNames + setOfNotNull(name)
        val replacedNode = replacements[name] ?: this.copy(name = name)

        return replacedNode.copy(
            children = replacedNode.children?.mapNotNull { child ->
                child.undoDeduplication(childrenAncestorIds)
            }
        )
    }

    return YarnListNode(name = "root", children = toList(), color = "bold").undoDeduplication()?.children.orEmpty()
}

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

private fun getAllModuleIds(moduleInfos: Collection<YarnListNode>): Set<String> {
    val queue = LinkedList(moduleInfos)
    val result = mutableSetOf<String>()

    while (queue.isNotEmpty()) {
        val moduleInfo = queue.removeFirst()

        result += moduleInfo.name
        queue += moduleInfo.children.orEmpty()
    }

    return result
}
