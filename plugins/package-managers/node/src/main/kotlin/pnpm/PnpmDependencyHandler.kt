/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.utils.DependencyHandler
import org.ossreviewtoolkit.plugins.packagemanagers.node.ModuleInfoResolver
import org.ossreviewtoolkit.plugins.packagemanagers.node.NodePackageManagerType
import org.ossreviewtoolkit.plugins.packagemanagers.node.PackageJson
import org.ossreviewtoolkit.plugins.packagemanagers.node.parsePackage
import org.ossreviewtoolkit.plugins.packagemanagers.node.parsePackageJson
import org.ossreviewtoolkit.plugins.packagemanagers.node.pnpm.ModuleInfo.Dependency
import org.ossreviewtoolkit.utils.common.realFile

internal class PnpmDependencyHandler(
    private val moduleInfoResolver: ModuleInfoResolver
) : DependencyHandler<Dependency> {
    private val workspaceModuleDirs = mutableSetOf<File>()
    private val packageJsonCache = mutableMapOf<File, PackageJson>()

    private fun Dependency.isProject(): Boolean = isInstalled && workingDir.realFile in workspaceModuleDirs

    fun setWorkspaceModuleDirs(dirs: Collection<File>) {
        workspaceModuleDirs.apply {
            clear()
            addAll(dirs)
        }
    }

    override fun identifierFor(dependency: Dependency): Identifier {
        val type = if (dependency.isProject()) NodePackageManagerType.PNPM.projectType else "NPM"
        val namespace = dependency.from.substringBeforeLast("/", "")
        val name = dependency.from.substringAfterLast("/")
        val version = if (dependency.isProject()) {
            readPackageJson(dependency.packageJsonFile).version.orEmpty()
        } else {
            dependency.version.takeUnless { it.startsWith("link:") || it.startsWith("file:") }.orEmpty()
        }

        return Identifier(type, namespace, name, version)
    }

    override fun dependenciesFor(dependency: Dependency): List<Dependency> =
        (dependency.dependencies + dependency.optionalDependencies).values.filter { it.isInstalled }

    override fun linkageFor(dependency: Dependency): PackageLinkage =
        PackageLinkage.DYNAMIC.takeUnless { dependency.isProject() } ?: PackageLinkage.PROJECT_DYNAMIC

    override fun createPackage(dependency: Dependency, issues: MutableCollection<Issue>): Package? =
        dependency.takeUnless { it.isProject() || !it.isInstalled }?.let {
            parsePackage(it.packageJsonFile, moduleInfoResolver)
        }

    private fun readPackageJson(packageJsonFile: File): PackageJson =
        packageJsonCache.getOrPut(packageJsonFile.realFile) { parsePackageJson(packageJsonFile) }
}

private val Dependency.workingDir: File get() = File(path)

private val Dependency.packageJsonFile: File get() = workingDir.resolve(NodePackageManagerType.DEFINITION_FILE)

/**
 * pnpm install skips optional dependencies which are not compatible with the environment. In this case the path
 * property points to a non-existing directory. For example, the fsevents package gets skipped under Linux. One could
 * install such dependencies too, with the --force option, but the documentation says that this also forces updating the
 * lockfile. Maybe, this can be mitigated by also using --frozen-lockfile. However, the documentation does not explain
 * how the combination of these two options works.
 */
private val Dependency.isInstalled: Boolean get() = workingDir.isDirectory
