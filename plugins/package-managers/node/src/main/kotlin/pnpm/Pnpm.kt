/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.node.pnpm

import java.io.File

import org.apache.logging.log4j.kotlin.logger

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
import org.ossreviewtoolkit.plugins.packagemanagers.node.Scope
import org.ossreviewtoolkit.plugins.packagemanagers.node.getNames
import org.ossreviewtoolkit.plugins.packagemanagers.node.parsePackageJson
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.DirectoryStash
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.nextOrNull

import org.semver4j.range.RangeList
import org.semver4j.range.RangeListFactory

internal object PnpmCommand : CommandLineTool {
    override fun command(workingDir: File?) = if (Os.isWindows) "pnpm.cmd" else "pnpm"

    override fun getVersionRequirement(): RangeList = RangeListFactory.create("5.* - 10.*")
}

/**
 * The [PNPM package manager](https://pnpm.io/).
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

    private val moduleInfoResolver = ModuleInfoResolver.create { workingDir, moduleId ->
        runCatching {
            // Note that pnpm does not actually implement the "info" subcommand itself, but just forwards to npm, see
            // https://github.com/pnpm/pnpm/issues/5935.
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

        installDependencies(workingDir, scopes)

        val workspaceModuleDirs = getWorkspaceModuleDirs(workingDir)
        handler.setWorkspaceModuleDirs(workspaceModuleDirs)

        val moduleInfosForScope = scopes.associateWith { scope -> listModules(workingDir, scope) }

        return workspaceModuleDirs.map { projectDir ->
            val packageJsonFile = projectDir.resolve(NodePackageManagerType.DEFINITION_FILE)
            val project = parseProject(packageJsonFile, analysisRoot)

            scopes.forEach { scope ->
                val moduleInfo = moduleInfosForScope.getValue(scope).single { it.path == projectDir.absolutePath }
                graphBuilder.addDependencies(project.id, scope.descriptor, moduleInfo.getScopeDependencies(scope))
            }

            ProjectAnalyzerResult(
                project = project.copy(scopeNames = scopes.getNames()),
                packages = emptySet()
            )
        }
    }

    private fun getWorkspaceModuleDirs(workingDir: File): Set<File> {
        val json = PnpmCommand.run(workingDir, "list", "--json", "--only-projects", "--recursive").requireSuccess()
            .stdout

        val listResult = parsePnpmList(json)
        return listResult.findModulesFor(workingDir).mapTo(mutableSetOf()) { File(it.path) }
    }

    private fun listModules(workingDir: File, scope: Scope): List<ModuleInfo> {
        val scopeOption = when (scope) {
            Scope.DEPENDENCIES -> "--prod"
            Scope.DEV_DEPENDENCIES -> "--dev"
        }

        val json = PnpmCommand.run(workingDir, "list", "--json", "--recursive", "--depth", "Infinity", scopeOption)
            .requireSuccess().stdout

        return parsePnpmList(json).flatten().toList()
    }

    private fun installDependencies(workingDir: File, scopes: Collection<Scope>) {
        val args = listOfNotNull(
            "install",
            "--ignore-pnpmfile",
            "--ignore-scripts",
            "--frozen-lockfile", // Use the existing lockfile instead of updating an outdated one.
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

/**
 * Find the [List] of [ModuleInfo] objects for the project in the given [workingDir]. If there are nested projects,
 * the `pnpm list` command yields multiple arrays with modules. In this case, only the top-level project should be
 * analyzed. This function tries to detect the corresponding [ModuleInfo]s based on the [workingDir]. If this is not
 * possible, as a fallback the first list of [ModuleInfo] objects is returned.
 */
private fun Sequence<List<ModuleInfo>>.findModulesFor(workingDir: File): List<ModuleInfo> {
    val moduleInfoIterator = iterator()
    val first = moduleInfoIterator.nextOrNull() ?: return emptyList()

    fun List<ModuleInfo>.matchesWorkingDir() = any { File(it.path).absoluteFile == workingDir }

    fun findMatchingModules(): List<ModuleInfo>? =
        moduleInfoIterator.nextOrNull()?.takeIf { it.matchesWorkingDir() } ?: findMatchingModules()

    return first.takeIf { it.matchesWorkingDir() } ?: findMatchingModules() ?: first
}
