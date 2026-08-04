/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.node.bun

import java.io.File

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packagemanagers.node.ModuleInfoResolver
import org.ossreviewtoolkit.plugins.packagemanagers.node.NODE_MODULES_DIRNAME
import org.ossreviewtoolkit.plugins.packagemanagers.node.NodePackageManager
import org.ossreviewtoolkit.plugins.packagemanagers.node.NodePackageManagerType
import org.ossreviewtoolkit.plugins.packagemanagers.node.PackageJson
import org.ossreviewtoolkit.plugins.packagemanagers.node.Scope
import org.ossreviewtoolkit.plugins.packagemanagers.node.getNames
import org.ossreviewtoolkit.plugins.packagemanagers.node.npm.ModuleInfo
import org.ossreviewtoolkit.plugins.packagemanagers.node.npm.NpmCommand
import org.ossreviewtoolkit.plugins.packagemanagers.node.npm.NpmDependencyHandler
import org.ossreviewtoolkit.plugins.packagemanagers.node.npm.extractNpmIssues
import org.ossreviewtoolkit.plugins.packagemanagers.node.npm.filterInstalled
import org.ossreviewtoolkit.plugins.packagemanagers.node.npm.getAllPackageNodeModuleIds
import org.ossreviewtoolkit.plugins.packagemanagers.node.npm.getScopeDependencies
import org.ossreviewtoolkit.plugins.packagemanagers.node.npm.parseNpmList
import org.ossreviewtoolkit.plugins.packagemanagers.node.npm.undoDeduplication
import org.ossreviewtoolkit.plugins.packagemanagers.node.parsePackageJson
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.realFile

import org.semver4j.range.RangeList
import org.semver4j.range.RangeListFactory

internal object BunCommand : CommandLineTool {
    // Note that Bun ships a real executable also on Windows, so no ".cmd" suffix is required.
    override fun command(workingDir: File?) = "bun"

    // Require at least Bun 1.2.0 which introduced the textual "bun.lock" lockfile format. Older binary "bun.lockb"
    // lockfiles can still be read by modern Bun versions.
    override fun getVersionRequirement(): RangeList = RangeListFactory.create(">=1.2.0")
}

/**
 * The [Bun package manager](https://bun.sh/). Bun installs dependencies to an NPM-compatible hoisted "node_modules"
 * directory. As "bun pm ls" does not (yet) support machine-readable output, the dependency tree is obtained by running
 * "npm list" on the Bun-installed "node_modules" directory. Once Bun supports "bun pm ls --json" (see
 * https://github.com/oven-sh/bun/issues/8283), the listing could be switched to use Bun directly.
 */
@OrtPlugin(
    displayName = "Bun",
    summary = "The Bun package manager for JavaScript.",
    factory = PackageManagerFactory::class
)
class Bun(override val descriptor: PluginDescriptor = BunFactory.descriptor) :
    NodePackageManager(NodePackageManagerType.BUN) {
    override val globsForDefinitionFiles = listOf(NodePackageManagerType.DEFINITION_FILE)

    private val moduleInfoResolver = ModuleInfoResolver.create { workingDir, moduleId ->
        runCatching {
            val process = NpmCommand.run(workingDir, "info", "--json", moduleId).requireSuccess()
            parsePackageJson(process.stdout)
        }.onFailure { e ->
            logger.warn { "Error getting module info for $moduleId: ${e.message.orEmpty()}" }
        }.getOrNull()
    }

    private val handler = NpmDependencyHandler(moduleInfoResolver, NodePackageManagerType.BUN)

    override val graphBuilder = DependencyGraphBuilder(handler)

    override fun beforeResolution(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ) {
        super.beforeResolution(analysisRoot, definitionFiles, analyzerConfig)

        BunCommand.checkVersion()
        NpmCommand.checkVersion()
    }

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        includes: Includes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile
        moduleInfoResolver.workingDir = workingDir

        installDependencies(analysisRoot, workingDir, analyzerConfig.allowDynamicVersions)

        val issues = mutableListOf<Issue>()

        val project = parseProject(definitionFile, analysisRoot)
        val projectModuleInfo = listModules(workingDir, issues)
            .markDevDependencies(parsePackageJson(definitionFile))
            .undoDeduplication()
            .filterInstalled()
        val scopes = Scope.entries.filterNotTo(mutableSetOf()) { scope -> scope.isExcluded(excludes, includes) }

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

        issues += listProcess.extractNpmIssues(descriptor.displayName).map { issue ->
            // NPM's version validation does not understand Bun-specific version specifiers like the "workspace:"
            // protocol and reports respective (installed) dependencies as invalid, so lower the severity accordingly.
            if (issue.severity == Severity.ERROR && issue.message.startsWith("invalid: ")) {
                issue.copy(severity = Severity.WARNING)
            } else {
                issue
            }
        }

        return parseNpmList(listProcess.stdout).markPackageModules()
    }

    private fun installDependencies(analysisRoot: File, workingDir: File, allowDynamicVersions: Boolean) {
        requireLockfile(analysisRoot, workingDir, allowDynamicVersions) { managerType.hasLockfile(workingDir) }

        val options = listOfNotNull(
            "--ignore-scripts",
            // Always use the "hoisted" linker to get the NPM-compatible layout that "npm list" requires. Since Bun
            // 1.3, workspace projects use the "isolated" linker by default.
            "--linker", "hoisted",
            // Use the existing lockfile instead of updating an outdated one.
            "--frozen-lockfile".takeIf { managerType.hasLockfile(workingDir) }
        )

        BunCommand.run(workingDir, "install", *options.toTypedArray()).requireSuccess()
    }

    private fun requestAllPackageDetails(projectModuleInfo: ModuleInfo, scopes: Set<Scope>) {
        projectModuleInfo.getAllPackageNodeModuleIds(scopes).let { moduleIds ->
            moduleInfoResolver.getModuleInfos(moduleIds)
        }
    }
}

/**
 * Bun does not write NPM's metadata to "node_modules", so "npm list" cannot report the URLs modules were resolved
 * from. As a non-null "resolved" property is what tells packages apart from (workspace) projects, normalize the
 * property based on the module path instead: Modules whose real path is inside a "node_modules" directory are
 * packages, all other modules (the project itself and symlinked workspace projects) are projects.
 */
private fun ModuleInfo.markPackageModules(): ModuleInfo {
    // Note that NPM may report non-existing paths for modules that are not installed.
    val realPath = path?.let { File(it).takeIf(File::exists)?.realFile }
    val isPackageModule = realPath != null
        && NODE_MODULES_DIRNAME in realPath.invariantSeparatorsPath.split('/')

    return copy(
        resolved = if (isPackageModule) resolved.orEmpty() else null,
        dependencies = dependencies.mapValues { it.value.markPackageModules() }
    )
}

/**
 * NPM only knows about development dependencies from its own lockfile, which does not exist for Bun projects. So
 * instead mark all direct dependencies of the project as development dependencies based on the respective declaration
 * in the given [packageJson]. This is enough for the scope assignment, which only looks at direct project
 * dependencies.
 */
private fun ModuleInfo.markDevDependencies(packageJson: PackageJson): ModuleInfo {
    val devDependencyNames = packageJson.devDependencies.keys - packageJson.dependencies.keys

    return copy(
        dependencies = dependencies.mapValues { (name, info) ->
            if (name in devDependencyNames) info.copy(dev = true) else info
        }
    )
}
