/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.node.yarn2

import java.io.File

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
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
import org.ossreviewtoolkit.plugins.packagemanagers.node.getDependenciesForScope
import org.ossreviewtoolkit.plugins.packagemanagers.node.getInstalledModulesDirs
import org.ossreviewtoolkit.plugins.packagemanagers.node.getNames
import org.ossreviewtoolkit.plugins.packagemanagers.node.parsePackageJson
import org.ossreviewtoolkit.plugins.packagemanagers.node.parsePackageJsons
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.realFile
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.ort.runBlocking

/**
 * The amount of package details to query at once with `yarn npm info`.
 */
private const val YARN_NPM_INFO_CHUNK_SIZE = 1000

data class Yarn2Config(
    /**
     * If true, the `yarn npm info` commands called by this package manager will not verify the server certificate of
     * the HTTPS connection to the NPM registry. This allows replacing the latter by a local one, e.g., for intercepting
     * the requests or replaying them.
     */
    @OrtPluginOption(defaultValue = "false")
    val disableRegistryCertificateVerification: Boolean,

    /**
     * Per default, this class determines via auto-detection whether Yarn has been installed via
     * [Corepack](https://yarnpkg.com/corepack), which impacts the name of the executable to use. With this option,
     * auto-detection can be disabled, and the enabled status of Corepack can be explicitly specified. This is useful to
     * force a specific behavior in some environments.
     */
    val corepackEnabled: Boolean?
)

/**
 * The [Yarn 2+ package manager](https://v2.yarnpkg.com/).
 */
@OrtPlugin(
    displayName = "Yarn 2+",
    description = "The Yarn 2+ package manager for Node.js.",
    factory = PackageManagerFactory::class
)
class Yarn2(override val descriptor: PluginDescriptor = Yarn2Factory.descriptor, private val config: Yarn2Config) :
    NodePackageManager(NodePackageManagerType.YARN2) {
    override val globsForDefinitionFiles = listOf(NodePackageManagerType.DEFINITION_FILE)
    internal val yarn2Command = Yarn2Command(config.corepackEnabled)
    private val moduleInfoResolver = ModuleInfoResolver { workingDir, moduleIds ->
        runBlocking(Dispatchers.IO.limitedParallelism(20)) {
            moduleIds.chunked(YARN_NPM_INFO_CHUNK_SIZE).map { chunk ->
                async {
                    val process = yarn2Command.run(
                        "npm",
                        "info",
                        "--json",
                        *chunk.toTypedArray(), // Not all Yarn Berry versions execute the `npm info` calls in parallel.
                        workingDir = workingDir,
                        environment = mapOf("NODE_TLS_REJECT_UNAUTHORIZED" to "0")
                            .takeIf { config.disableRegistryCertificateVerification }
                            .orEmpty()
                    ).requireSuccess()

                    parsePackageJsons(process.stdout)
                }
            }.awaitAll().flatten().toSet()
        }
    }

    private val handler = Yarn2DependencyHandler(moduleInfoResolver)
    override val graphBuilder = DependencyGraphBuilder(handler)

    override fun beforeResolution(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ) {
        super.beforeResolution(analysisRoot, definitionFiles, analyzerConfig)

        definitionFiles.forEach {
            yarn2Command.checkVersion(it.parentFile)
        }
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
        val packageInfoForLocator = getPackageInfos(workingDir).associateBy { it.value }
        handler.setContext(workingDir, getInstalledModulesDirs(workingDir), packageInfoForLocator)

        // Warm-up the cache to speed-up processing.
        requestAllPackageDetails(packageInfoForLocator.values)

        val scopes = Scope.entries.filterNot { scope -> scope.isExcluded(excludes) }

        return workspaceModuleDirs.map { projectDir ->
            val packageJsonFile = projectDir / NodePackageManagerType.DEFINITION_FILE
            val packageJson = parsePackageJson(packageJsonFile)
            val project = parseProject(packageJsonFile, analysisRoot)
            val packageInfo = packageInfoForLocator.values.single { it.getProjectDir(workingDir) == projectDir }

            scopes.forEach { scope ->
                val dependencyNames = packageJson.getDependenciesForScope(scope)
                val dependencies = packageInfo.children.dependencies
                    .map { packageInfoForLocator.getValue(it.locator) }
                    .filter { it.moduleName in dependencyNames }

                graphBuilder.addDependencies(project.id, scope.descriptor, dependencies)
            }

            ProjectAnalyzerResult(
                project = project.copy(scopeNames = scopes.getNames()),
                packages = emptySet()
            )
        }
    }

    private fun installDependencies(workingDir: File) {
        yarn2Command.run("install", workingDir = workingDir).requireSuccess()
    }

    private fun getPackageInfos(workingDir: File): List<PackageInfo> {
        val process = yarn2Command.run(
            "info",
            "--all",
            "--recursive",
            "--manifest",
            "--virtuals",
            "--json",
            workingDir = workingDir,
            environment = mapOf("YARN_NODE_LINKER" to "pnp")
        ).requireSuccess()

        return parsePackageInfos(process.stdout)
    }

    private fun getWorkspaceModuleDirs(workingDir: File): Set<File> {
        val process = yarn2Command.run(
            workingDir,
            "workspaces",
            "list",
            "--json"
        ).requireSuccess()

        return parseWorkspaceInfo(process.stdout).mapTo(mutableSetOf()) {
            workingDir.resolve(it.location).realFile
        }
    }

    private fun requestAllPackageDetails(packageInfos: Collection<PackageInfo>) {
        val moduleIds = packageInfos.mapNotNullTo(mutableSetOf()) { info ->
            if (info.isProject) null else info.moduleId
        }

        moduleInfoResolver.getModuleInfos(moduleIds)
    }
}

private fun PackageInfo.getProjectDir(workingDir: File): File? =
    value.substringAfterLast("@").withoutPrefix("workspace:")?.let { workingDir.resolve(it).realFile }
