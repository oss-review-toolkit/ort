/*
 * Copyright (C) 2024 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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
import java.util.LinkedList

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.utils.DependencyHandler
import org.ossreviewtoolkit.plugins.packagemanagers.node.ModuleInfoResolver
import org.ossreviewtoolkit.plugins.packagemanagers.node.NON_EXISTING_SEMVER
import org.ossreviewtoolkit.plugins.packagemanagers.node.NodePackageManagerType
import org.ossreviewtoolkit.plugins.packagemanagers.node.PackageJson
import org.ossreviewtoolkit.plugins.packagemanagers.node.Scope
import org.ossreviewtoolkit.plugins.packagemanagers.node.parsePackage
import org.ossreviewtoolkit.plugins.packagemanagers.node.parsePackageJson
import org.ossreviewtoolkit.plugins.packagemanagers.node.pnpm.ModuleInfo.Dependency
import org.ossreviewtoolkit.utils.common.realFile

internal class PnpmDependencyHandler(
    private val moduleInfoResolver: ModuleInfoResolver
) : DependencyHandler<Dependency> {
    private val workspaceModuleDirs = mutableSetOf<File>()
    private val workspaceModuleDirForPublishDir = mutableMapOf<File, File>()
    private val packageJsonCache = mutableMapOf<File, PackageJson>()

    /**
     * The directory of the workspace module this dependency refers to, or null if it does not refer to a workspace
     * module. If a workspace module sets "publishConfig.directory", PNPM links dependents to that publish directory
     * instead of to the module directory, no matter whether the publish directory exists.
     */
    private val Dependency.projectDir: File?
        get() = workspaceModuleDirForPublishDir[workingDir.absoluteFile.normalize()]
            ?: workingDir.takeIf { isInstalled }?.realFile?.takeIf { it in workspaceModuleDirs }

    private val Dependency.isProject: Boolean
        get() = projectDir != null

    fun setWorkspaceModuleDirs(dirs: Collection<File>) {
        workspaceModuleDirs.apply {
            clear()
            addAll(dirs)
        }

        workspaceModuleDirForPublishDir.clear()
        dirs.forEach { dir ->
            val packageJson = readPackageJson(dir.resolve(NodePackageManagerType.DEFINITION_FILE))
            packageJson.publishConfig?.directory?.let { publishDir ->
                workspaceModuleDirForPublishDir[dir.resolve(publishDir).normalize()] = dir
            }
        }
    }

    /**
     * Return whether the [dependency] is relevant for the dependency graph, which is the case if it is installed or
     * refers to a workspace module.
     */
    fun isRelevant(dependency: Dependency): Boolean = dependency.isInstalled || dependency.isProject

    override fun identifierFor(dependency: Dependency): Identifier {
        val type = with(NodePackageManagerType.PNPM) { if (dependency.isProject) projectType else packageType }
        val namespace = dependency.from.substringBeforeLast("/", "")
        val name = dependency.from.substringAfterLast("/")
        val projectDir = dependency.projectDir
        val version = if (projectDir != null) {
            readPackageJson(projectDir.resolve(NodePackageManagerType.DEFINITION_FILE)).version.orEmpty()
        } else {
            dependency.resolvableVersion.orEmpty()
        }

        return Identifier(type, namespace, name, version)
    }

    override fun dependenciesFor(dependency: Dependency): List<Dependency> =
        (dependency.dependencies + dependency.optionalDependencies).values.filter { isRelevant(it) }

    override fun linkageFor(dependency: Dependency): PackageLinkage =
        PackageLinkage.DYNAMIC.takeUnless { dependency.isProject } ?: PackageLinkage.PROJECT_DYNAMIC

    override fun createPackage(dependency: Dependency, issues: MutableCollection<Issue>): Package? =
        dependency.takeUnless { it.isProject || !it.isInstalled }?.let {
            parsePackage(it.packageJsonFile, moduleInfoResolver, it.isRegistryDependency, it.resolved)
        }

    /**
     * Warm up the cache of [moduleInfoResolver] with the details of all installed, non-project dependencies
     * (recursively) found in [moduleInfos], to speed up subsequent calls to [createPackage].
     */
    fun requestAllPackageDetails(moduleInfos: Collection<ModuleInfo>) {
        val moduleIds = getAllPackageModuleIds(moduleInfos)

        moduleInfoResolver.getModuleInfos(moduleIds)
    }

    /**
     * Return a [Set] with the module IDs for all installed, non-project dependencies (recursively) found in
     * [moduleInfos]. The module IDs are derived from the dependency metadata already available in [ModuleInfo],
     * without the need to read and parse the corresponding "package.json" files.
     */
    private fun getAllPackageModuleIds(moduleInfos: Collection<ModuleInfo>): Set<String> =
        buildSet {
            val queue = LinkedList<Dependency>()
            moduleInfos.flatMapTo(queue) {
                it.getScopeDependencies(Scope.DEPENDENCIES) + it.getScopeDependencies(Scope.DEV_DEPENDENCIES)
            }

            while (queue.isNotEmpty()) {
                val dependency = queue.removeFirst()

                if (dependency.isInstalled && !dependency.isProject && dependency.isRegistryDependency) {
                    dependency.moduleId?.let(::add)
                }

                queue += dependency.dependencies.values + dependency.optionalDependencies.values
            }
        }

    /**
     * Return the module IDs in the form "name@version" of all packages which are (transitive) dependencies of the given
     * [dependencies]. This allows requesting the package details for all packages upfront.
     */
    fun getAllPackageModuleIds(dependencies: Collection<Dependency>): Set<String> {
        val moduleIds = mutableSetOf<String>()
        val queue = ArrayDeque(dependencies)

        // Note that the same module may occur multiple times with different (deduplicated) dependencies in the output
        // of "pnpm list", so all occurrences need to be visited.
        while (queue.isNotEmpty()) {
            val dependency = queue.removeFirst()

            if (!dependency.isProject) {
                val packageJson = readPackageJson(dependency.packageJsonFile)
                moduleIds += "${packageJson.name.orEmpty()}@${packageJson.version ?: NON_EXISTING_SEMVER}"
            }

            queue += dependenciesFor(dependency)
        }

        return moduleIds
    }

    private fun readPackageJson(packageJsonFile: File): PackageJson =
        packageJsonCache.getOrPut(packageJsonFile.realFile) { parsePackageJson(packageJsonFile) }
}

private val Dependency.workingDir: File
    get() = File(path)

private val Dependency.packageJsonFile: File
    get() = workingDir.resolve(NodePackageManagerType.DEFINITION_FILE)

/**
 * Return the version of this [Dependency] that can be resolved via a registry, or `null` if the dependency does
 * not refer to such a version, e.g. because it is a local "link:" or "file:" dependency.
 */
private val Dependency.resolvableVersion: String?
    get() = version.takeUnless { it.startsWith("link:") || it.startsWith("file:") }

/**
 * Return the module ID (in the form "name@version") for this [Dependency], or `null` if the dependency does not
 * refer to a version resolvable via a registry, e.g. because it is a local "link:" or "file:" dependency.
 */
private val Dependency.moduleId: String?
    get() = resolvableVersion?.let { "$from@$it" }

/**
 * pnpm install skips optional dependencies which are not compatible with the environment. In this case the path
 * property points to a non-existing directory. For example, the fsevents package gets skipped under Linux. One could
 * install such dependencies too, with the --force option, but the documentation says that this also forces updating the
 * lockfile. Maybe, this can be mitigated by also using --frozen-lockfile. However, the documentation does not explain
 * how the combination of these two options works.
 */
private val Dependency.isInstalled: Boolean
    get() = workingDir.isDirectory
