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

import kotlin.time.Duration.Companion.days

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeToSequence
import kotlinx.serialization.json.jsonPrimitive

import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.plugins.packagemanagers.node.PackageJson
import org.ossreviewtoolkit.plugins.packagemanagers.node.parsePackageJson
import org.ossreviewtoolkit.plugins.packagemanagers.node.parseProject
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.DiskCache
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.alsoIfNull
import org.ossreviewtoolkit.utils.common.mebibytes
import org.ossreviewtoolkit.utils.common.stashDirectories
import org.ossreviewtoolkit.utils.ort.ortDataDirectory

import org.semver4j.RangesList
import org.semver4j.RangesListFactory

private val yarnInfoCache = DiskCache(
    directory = ortDataDirectory.resolve("cache/analyzer/yarn/info"),
    maxCacheSizeInBytes = 100.mebibytes,
    maxCacheEntryAgeInSeconds = 7.days.inWholeSeconds
)

/**
 * The [Yarn](https://classic.yarnpkg.com/) package manager for JavaScript.
 */
class Yarn(
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

    private val handler = YarnDependencyHandler(this)
    private val graphBuilder by lazy { DependencyGraphBuilder(handler) }

    override fun command(workingDir: File?) = if (Os.isWindows) "yarn.cmd" else "yarn"

    override fun getVersionRequirement(): RangesList = RangesListFactory.create("1.3.* - 1.22.*")

    override fun beforeResolution(definitionFiles: List<File>) =
        // We do not actually depend on any features specific to a Yarn version, but we still want to stick to a
        // fixed minor version to be sure to get consistent results.
        checkVersion()

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> =
        stashDirectories(definitionFile.resolveSibling("node_modules")).use {
            resolveDependencies(definitionFile)
        }

    private fun resolveDependencies(definitionFile: File): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile
        installDependencies(workingDir)

        val packageJsonForModuleId = getInstalledModules(workingDir).associateBy { "${it.name}@${it.version}" }
        handler.setContext(workingDir, packageJsonForModuleId)

        val project = parseProject(workingDir.resolve("package.json"), analysisRoot, managerName)

        val scopeNames = Scope.entries.mapTo(mutableSetOf()) { scope ->
            val scopeName = scope.descriptor

            val list = listModules(workingDir, scope)

            val dependencies = undoDeduplication(list).filter {
                it.color == "bold" && it.name in packageJsonForModuleId
            }

            graphBuilder.addDependencies(project.id, scopeName, dependencies)

            scopeName
        }

        return ProjectAnalyzerResult(
            project = project.copy(scopeNames = scopeNames),
            packages = emptySet(),
            issues = emptyList()
        ).let { listOf(it) }
    }

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())

    private fun installDependencies(workingDir: File) =
        run(workingDir, "install", "--ignore-scripts", "--ignore-engines", "--immutable")

    private fun listModules(workingDir: File, scope: Scope): List<YarnListNode> {
        val scopeOption = when (scope) {
            Scope.DEPENDENCIES -> "--prod"
            Scope.DEV_DEPENDENCIES -> "--dev"
        }

        val json = run(workingDir, "list", "--json", scopeOption).stdout

        return parseYarnList(json)
    }

    internal fun getRemotePackageDetails(workingDir: File, packageName: String): PackageJson? {
        yarnInfoCache.read(packageName)?.let { return parsePackageJson(it) }

        val process = run(workingDir, "info", "--json", packageName)

        return parseYarnInfo(process.stdout, process.stderr)?.also {
            yarnInfoCache.write(packageName, Json.encodeToString(it))
        }
    }
}

private enum class Scope(val descriptor: String) {
    DEPENDENCIES("dependencies"),
    DEV_DEPENDENCIES("devDependencies")
}

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

private fun undoDeduplication(moduleInfos: Collection<YarnListNode>): List<YarnListNode> {
    val replacements = getNonDeduplicatedModuleInfosForId(moduleInfos)

    fun YarnListNode.undoDeduplicationRec(
        ancestorsIds: Set<String> = emptySet(),
        versionForName: Map<String, String> = emptyMap()
    ): YarnListNode? {
        val moduleId = "$moduleName@${versionForName[moduleName]}"
        if (moduleId in ancestorsIds) return null // break cycles.

        val childrenAncestorIds = ancestorsIds + setOfNotNull(moduleId)
        val childrenVersionForName = buildMap {
            putAll(versionForName)

            children?.forEach { child ->
                if (child.children != null) {
                    put(child.moduleName, child.moduleVersion)
                }
            }
        }

        val replacedNode = replacements[moduleId] ?: this.copy(name = moduleId)

        return replacedNode.copy(
            children = replacedNode.children?.mapNotNull { child ->
                child.undoDeduplicationRec(childrenAncestorIds, childrenVersionForName)
            }
        )
    }

    return YarnListNode(name = "root", children = moduleInfos.toList()).undoDeduplicationRec()?.children.orEmpty()
}

private fun getNonDeduplicatedModuleInfosForId(moduleInfos: Collection<YarnListNode>): Map<String, YarnListNode> {
    val queue = LinkedList<YarnListNode>().apply { addAll(moduleInfos) }
    val result = mutableMapOf<String, YarnListNode>()

    while (queue.isNotEmpty()) {
        val info = queue.removeFirst()

        if (info.children != null) {
            result[info.name] = info
        }

        queue += info.children.orEmpty()
    }

    return result
}

internal val YarnListNode.moduleName: String get() = name.substringBeforeLast("@")

internal val YarnListNode.moduleVersion: String get() = name.substringAfterLast("@")

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

private fun getInstalledModules(moduleDir: File) =
    moduleDir.resolve("node_modules").walkBottomUp().filter {
        it.isFile && it.name == "package.json"
    }.mapTo(mutableListOf()) { parsePackageJson(it) }
