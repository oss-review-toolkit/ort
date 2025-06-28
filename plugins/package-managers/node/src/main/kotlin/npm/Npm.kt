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

package org.ossreviewtoolkit.plugins.packagemanagers.node.npm

import java.io.File
import java.util.LinkedList

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packagemanagers.node.ModuleInfoResolver
import org.ossreviewtoolkit.plugins.packagemanagers.node.NodePackageManager
import org.ossreviewtoolkit.plugins.packagemanagers.node.NodePackageManagerType
import org.ossreviewtoolkit.plugins.packagemanagers.node.Scope
import org.ossreviewtoolkit.plugins.packagemanagers.node.getNames
import org.ossreviewtoolkit.plugins.packagemanagers.node.parsePackageJson
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.DirectoryStash
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.withoutPrefix

import org.semver4j.range.RangeList
import org.semver4j.range.RangeListFactory

internal object NpmCommand : CommandLineTool {
    override fun command(workingDir: File?) = if (Os.isWindows) "npm.cmd" else "npm"

    override fun getVersionRequirement(): RangeList = RangeListFactory.create("6.* - 10.*")
}

data class NpmConfig(
    /**
     * If true, the "--legacy-peer-deps" flag is passed to NPM to ignore conflicts in peer dependencies which are
     * reported since NPM 7. This allows to analyze NPM 6 projects with peer dependency conflicts. For more information
     * see the [documentation](https://docs.npmjs.com/cli/v8/commands/npm-install#strict-peer-deps) and the
     * [NPM Blog](https://blog.npmjs.org/post/626173315965468672/npm-v7-series-beta-release-and-semver-major).
     */
    @OrtPluginOption(defaultValue = "false")
    val legacyPeerDeps: Boolean
)

/**
 * The [Node package manager](https://www.npmjs.com/).
 */
@OrtPlugin(
    id = "NPM",
    displayName = "NPM",
    description = "The Node package manager for Node.js.",
    factory = PackageManagerFactory::class
)
class Npm(override val descriptor: PluginDescriptor = NpmFactory.descriptor, private val config: NpmConfig) :
    NodePackageManager(NodePackageManagerType.NPM) {

    override val globsForDefinitionFiles = listOf(NodePackageManagerType.DEFINITION_FILE)

    private lateinit var stash: DirectoryStash

    private val moduleInfoResolver = ModuleInfoResolver.create { workingDir, moduleId ->
        runCatching {
            val process = NpmCommand.run(workingDir, "info", "--json", moduleId).requireSuccess()
            parsePackageJson(process.stdout)
        }.onFailure { e ->
            logger.warn { "Error getting module info for $moduleId: ${e.message.orEmpty()}" }
        }.getOrNull()
    }

    private val handler = NpmDependencyHandler(moduleInfoResolver)

    override val graphBuilder = DependencyGraphBuilder(handler)

    override fun beforeResolution(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ) {
        super.beforeResolution(analysisRoot, definitionFiles, analyzerConfig)

        NpmCommand.checkVersion()

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

        val issues = installDependencies(analysisRoot, workingDir, analyzerConfig.allowDynamicVersions).toMutableList()

        if (issues.any { it.severity == Severity.ERROR }) {
            val project = runCatching {
                parseProject(definitionFile, analysisRoot)
            }.getOrElse {
                logger.error { "Failed to parse project information: ${it.collectMessages()}" }
                Project.EMPTY
            }

            return listOf(ProjectAnalyzerResult(project, emptySet(), issues))
        }

        val project = parseProject(definitionFile, analysisRoot)
        val projectModuleInfo = listModules(workingDir, issues).undoDeduplication()
        val scopes = Scope.entries.filterNotTo(mutableSetOf()) { scope -> scope.isExcluded(excludes) }

        // Warm-up the cache to speed-up processing.
        requestAllPackageDetails(projectModuleInfo, scopes)

        scopes.forEach { scope ->
            graphBuilder.addDependencies(project.id, scope.descriptor, projectModuleInfo.getScopeDependencies(scope))
        }

        return ProjectAnalyzerResult(
            project = project.copy(scopeNames = scopes.getNames()),
            packages = emptySet(),
            issues = issues
        ).let { listOf(it) }
    }

    private fun listModules(workingDir: File, issues: MutableList<Issue>): ModuleInfo {
        val listProcess = NpmCommand.run(workingDir, "list", "--depth", "Infinity", "--json", "--long")
        issues += listProcess.extractNpmIssues()

        return parseNpmList(listProcess.stdout)
    }

    private fun installDependencies(analysisRoot: File, workingDir: File, allowDynamicVersions: Boolean): List<Issue> {
        requireLockfile(analysisRoot, workingDir, allowDynamicVersions) { managerType.hasLockfile(workingDir) }

        val options = listOfNotNull(
            "--ignore-scripts",
            "--no-audit",
            "--legacy-peer-deps".takeIf { config.legacyPeerDeps }
        )

        val subcommand = if (managerType.hasLockfile(workingDir)) "ci" else "install"

        val process = NpmCommand.run(workingDir, subcommand, *options.toTypedArray())

        return process.extractNpmIssues()
    }

    private fun requestAllPackageDetails(projectModuleInfo: ModuleInfo, scopes: Set<Scope>) {
        projectModuleInfo.getAllPackageNodeModuleIds(scopes).let { moduleIds ->
            moduleInfoResolver.getModuleInfos(moduleIds)
        }
    }
}

private fun ModuleInfo.getAllPackageNodeModuleIds(scopes: Set<Scope>): Set<String> =
    buildSet {
        val queue = scopes.flatMapTo(LinkedList()) { getScopeDependencies(it) }

        while (queue.isNotEmpty()) {
            val info = queue.removeFirst()

            @Suppress("ComplexCondition")
            if (!info.isProject && info.isInstalled && !info.name.isNullOrBlank() && !info.version.isNullOrBlank()) {
                add("${info.name}@${info.version}")
            }

            scopes.flatMapTo(queue) { info.getScopeDependencies(it) }
        }
    }

private fun ModuleInfo.getScopeDependencies(scope: Scope) =
    when (scope) {
        Scope.DEPENDENCIES -> dependencies.values.filter { !it.dev }
        Scope.DEV_DEPENDENCIES -> dependencies.values.filter { it.dev && !it.optional }
    }

private fun ModuleInfo.undoDeduplication(): ModuleInfo {
    val replacements = getNonDeduplicatedModuleInfosForId()

    fun ModuleInfo.undoDeduplicationRec(ancestorsIds: Set<String> = emptySet()): ModuleInfo {
        val dependencyAncestorIds = ancestorsIds + setOfNotNull(id)
        val dependencies = (replacements[id] ?: this)
            .dependencies
            .filter { it.value.id !in dependencyAncestorIds } // break cycles.
            .mapValues { it.value.undoDeduplicationRec(dependencyAncestorIds) }

        return copy(dependencies = dependencies)
    }

    return undoDeduplicationRec()
}

private fun ModuleInfo.getNonDeduplicatedModuleInfosForId(): Map<String, ModuleInfo> {
    val queue = LinkedList<ModuleInfo>().apply { add(this@getNonDeduplicatedModuleInfosForId) }
    val result = mutableMapOf<String, ModuleInfo>()

    while (queue.isNotEmpty()) {
        val info = queue.removeFirst()

        if (info.id != null && info.dependencyConstraints.keys.subtract(info.dependencies.keys).isEmpty()) {
            result[info.id] = info
        }

        queue += info.dependencies.values
    }

    return result
}

internal fun List<String>.groupLines(vararg markers: String): List<String> {
    val ignorableLinePrefixes = setOf(
        "A complete log of this run can be found in: ",
        "code ",
        "errno ",
        "path ",
        "syscall "
    )
    val singleLinePrefixes =
        setOf("deprecated ", "invalid: ", "missing: ", "skipping integrity check for git dependency ")
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

private fun ProcessCapture.extractNpmIssues(): List<Issue> {
    val lines = stderr.lines()
    val issues = mutableListOf<Issue>()

    // Generally forward issues from the NPM CLI to the ORT NPM package manager. Lower the severity of warnings to
    // hints, as warnings usually do not prevent the ORT NPM package manager from getting the dependencies right.
    lines.groupLines("npm WARN ", "npm warn ").mapTo(issues) {
        Issue(source = NpmFactory.descriptor.displayName, message = it, severity = Severity.HINT)
    }

    // For errors, however, something clearly went wrong, so keep the severity here.
    lines.groupLines("npm ERR! ", "npm error ").mapTo(issues) {
        Issue(source = NpmFactory.descriptor.displayName, message = it, severity = Severity.ERROR)
    }

    return issues
}
