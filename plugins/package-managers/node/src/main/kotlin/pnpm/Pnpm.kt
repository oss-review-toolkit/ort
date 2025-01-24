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

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.plugins.packagemanagers.node.NodePackageManager
import org.ossreviewtoolkit.plugins.packagemanagers.node.NodePackageManagerType
import org.ossreviewtoolkit.plugins.packagemanagers.node.PackageJson
import org.ossreviewtoolkit.plugins.packagemanagers.node.parsePackageJson
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.DirectoryStash
import org.ossreviewtoolkit.utils.common.Os

import org.semver4j.RangesList
import org.semver4j.RangesListFactory

internal object PnpmCommand : CommandLineTool {
    override fun command(workingDir: File?) = if (Os.isWindows) "pnpm.cmd" else "pnpm"

    override fun getVersionRequirement(): RangesList = RangesListFactory.create("5.* - 9.*")
}

/**
 * The [fast, disk space efficient package manager](https://pnpm.io/).
 */
class Pnpm(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : NodePackageManager(name, NodePackageManagerType.PNPM, analysisRoot, analyzerConfig, repoConfig) {
    class Factory : AbstractPackageManagerFactory<Pnpm>("PNPM") {
        override val globsForDefinitionFiles = listOf(NodePackageManagerType.DEFINITION_FILE, "pnpm-lock.yaml")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Pnpm(type, analysisRoot, analyzerConfig, repoConfig)
    }

    private lateinit var stash: DirectoryStash

    private val packageDetailsCache = mutableMapOf<String, PackageJson>()
    private val handler = PnpmDependencyHandler(projectType, this::getRemotePackageDetails)

    override val graphBuilder by lazy { DependencyGraphBuilder(handler) }

    override fun beforeResolution(definitionFiles: List<File>) {
        PnpmCommand.checkVersion()

        val directories = definitionFiles.mapTo(mutableSetOf()) { it.resolveSibling("node_modules") }
        stash = DirectoryStash(directories)
    }

    override fun afterResolution(definitionFiles: List<File>) {
        stash.close()
    }

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile
        installDependencies(workingDir)

        val workspaceModuleDirs = getWorkspaceModuleDirs(workingDir)
        handler.setWorkspaceModuleDirs(workspaceModuleDirs)

        val scopes = Scope.entries.filterNot { scope -> excludes.isScopeExcluded(scope.descriptor) }
        val moduleInfosForScope = scopes.associateWith { scope -> listModules(workingDir, scope) }

        return workspaceModuleDirs.map { projectDir ->
            val packageJsonFile = projectDir.resolve(NodePackageManagerType.DEFINITION_FILE)
            val project = parseProject(packageJsonFile, analysisRoot)

            val scopeNames = scopes.mapTo(mutableSetOf()) { scope ->
                val scopeName = scope.descriptor
                val moduleInfo = moduleInfosForScope.getValue(scope).single { it.path == projectDir.absolutePath }

                graphBuilder.addDependencies(project.id, scopeName, moduleInfo.getScopeDependencies(scope))

                scopeName
            }

            ProjectAnalyzerResult(
                project = project.copy(scopeNames = scopeNames),
                packages = emptySet(),
                issues = emptyList()
            )
        }
    }

    private fun getWorkspaceModuleDirs(workingDir: File): Set<File> {
        val json = PnpmCommand.run(workingDir, "list", "--json", "--only-projects", "--recursive").requireSuccess()
            .stdout

        return parsePnpmList(json).mapTo(mutableSetOf()) { File(it.path) }
    }

    private fun listModules(workingDir: File, scope: Scope): List<ModuleInfo> {
        val scopeOption = when (scope) {
            Scope.DEPENDENCIES -> "--prod"
            Scope.DEV_DEPENDENCIES -> "--dev"
        }

        val json = PnpmCommand.run(workingDir, "list", "--json", "--recursive", "--depth", "Infinity", scopeOption)
            .requireSuccess().stdout

        return parsePnpmList(json)
    }

    private fun installDependencies(workingDir: File) =
        PnpmCommand.run(
            "install",
            "--ignore-pnpmfile",
            "--ignore-scripts",
            "--frozen-lockfile", // Use the existing lockfile instead of updating an outdated one.
            workingDir = workingDir
        ).requireSuccess()

    internal fun getRemotePackageDetails(packageName: String): PackageJson? {
        packageDetailsCache[packageName]?.let { return it }

        return runCatching {
            val process = PnpmCommand.run("info", "--json", packageName).requireSuccess()

            parsePackageJson(process.stdout)
        }.onFailure { e ->
            logger.warn { "Error getting details for $packageName: ${e.message.orEmpty()}" }
        }.onSuccess {
            packageDetailsCache[packageName] = it
        }.getOrNull()
    }
}

private enum class Scope(val descriptor: String) {
    DEPENDENCIES("dependencies"),
    DEV_DEPENDENCIES("devDependencies")
}

private fun ModuleInfo.getScopeDependencies(scope: Scope) =
    when (scope) {
        Scope.DEPENDENCIES -> buildList {
            addAll(dependencies.values)
            addAll(optionalDependencies.values)
        }

        Scope.DEV_DEPENDENCIES -> devDependencies.values.toList()
    }
