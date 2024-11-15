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
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.plugins.packagemanagers.node.NodePackageManager
import org.ossreviewtoolkit.plugins.packagemanagers.node.NpmDetection
import org.ossreviewtoolkit.plugins.packagemanagers.node.PackageJson
import org.ossreviewtoolkit.plugins.packagemanagers.node.parsePackageJson
import org.ossreviewtoolkit.plugins.packagemanagers.node.parseProject
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.stashDirectories

import org.semver4j.RangesList
import org.semver4j.RangesListFactory

/**
 * The [fast, disk space efficient package manager](https://pnpm.io/).
 */
class Pnpm(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, "PNPM", analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<Pnpm>("PNPM") {
        override val globsForDefinitionFiles = listOf("package.json", "pnpm-lock.yaml")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Pnpm(type, analysisRoot, analyzerConfig, repoConfig)
    }

    private val handler = PnpmDependencyHandler(this)
    private val graphBuilder by lazy { DependencyGraphBuilder(handler) }
    private val packageDetailsCache = mutableMapOf<String, PackageJson>()

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> =
        stashDirectories(definitionFile.resolveSibling("node_modules")).use {
            resolveDependencies(definitionFile)
        }

    private fun resolveDependencies(definitionFile: File): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile
        installDependencies(workingDir)

        val workspaceModuleDirs = getWorkspaceModuleDirs(workingDir)
        handler.setWorkspaceModuleDirs(workspaceModuleDirs)

        val scopes = Scope.entries.filterNot { scope -> excludes.isScopeExcluded(scope.descriptor) }
        val moduleInfosForScope = scopes.associateWith { scope -> listModules(workingDir, scope) }

        return workspaceModuleDirs.map { projectDir ->
            val project = parseProject(projectDir.resolve("package.json"), analysisRoot, managerName)

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

    override fun command(workingDir: File?) = if (Os.isWindows) "pnpm.cmd" else "pnpm"

    private fun getWorkspaceModuleDirs(workingDir: File): Set<File> {
        val json = run(workingDir, "list", "--json", "--only-projects", "--recursive").stdout

        return parsePnpmList(json).mapTo(mutableSetOf()) { File(it.path) }
    }

    private fun listModules(workingDir: File, scope: Scope): List<ModuleInfo> {
        val scopeOption = when (scope) {
            Scope.DEPENDENCIES -> "--prod"
            Scope.DEV_DEPENDENCIES -> "--dev"
        }

        val json = run(workingDir, "list", "--json", "--recursive", "--depth", "Infinity", scopeOption).stdout

        return parsePnpmList(json)
    }

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())

    override fun getVersionRequirement(): RangesList = RangesListFactory.create("5.* - 9.*")

    override fun mapDefinitionFiles(definitionFiles: List<File>) =
        NpmDetection(definitionFiles).filterApplicable(NodePackageManager.PNPM)

    private fun installDependencies(workingDir: File) =
        run(
            "install",
            "--ignore-pnpmfile",
            "--ignore-scripts",
            "--frozen-lockfile", // Use the existing lockfile instead of updating an outdated one.
            workingDir = workingDir
        )

    override fun beforeResolution(definitionFiles: List<File>) =
        // We do not actually depend on any features specific to a PNPM version, but we still want to stick to a
        // fixed major version to be sure to get consistent results.
        checkVersion()

    internal fun getRemotePackageDetails(workingDir: File, packageName: String): PackageJson? {
        packageDetailsCache[packageName]?.let { return it }

        return runCatching {
            val process = run(workingDir, "info", "--json", packageName)

            parsePackageJson(process.stdout)
        }.onFailure { e ->
            logger.warn { "Error getting details for $packageName in directory $workingDir: ${e.message.orEmpty()}" }
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
