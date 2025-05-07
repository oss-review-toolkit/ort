/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.utils.DependencyHandler
import org.ossreviewtoolkit.plugins.packagemanagers.node.ModuleInfoResolver
import org.ossreviewtoolkit.plugins.packagemanagers.node.NodePackageManagerType
import org.ossreviewtoolkit.plugins.packagemanagers.node.PackageJson
import org.ossreviewtoolkit.plugins.packagemanagers.node.moduleId
import org.ossreviewtoolkit.plugins.packagemanagers.node.parsePackage
import org.ossreviewtoolkit.plugins.packagemanagers.node.parsePackageJson

internal class YarnDependencyHandler(
    private val moduleInfoResolver: ModuleInfoResolver
) : DependencyHandler<YarnListNode> {
    private val packageJsonForModuleId = mutableMapOf<String, PackageJson>()
    private val moduleDirForModuleId = mutableMapOf<String, File>()
    private val projectDirs = mutableSetOf<File>()
    private lateinit var workingDir: File

    fun setContext(workingDir: File, moduleDirs: Set<File>, projectDirs: Set<File>) {
        this.workingDir = workingDir

        this.projectDirs.apply {
            clear()
            addAll(projectDirs)
        }

        packageJsonForModuleId.clear()
        moduleDirForModuleId.clear()

        moduleDirs.forEach { moduleDir ->
            val packageJson = parsePackageJson(moduleDir.resolve(NodePackageManagerType.DEFINITION_FILE))
            packageJsonForModuleId[packageJson.moduleId] = packageJson
            moduleDirForModuleId[packageJson.moduleId] = moduleDir
        }
    }

    override fun identifierFor(dependency: YarnListNode): Identifier =
        Identifier(
            type = if (dependency.isProject()) NodePackageManagerType.YARN.projectType else "NPM",
            namespace = dependency.moduleName.substringBefore("/", ""),
            name = dependency.moduleName.substringAfter("/"),
            version = dependency.moduleVersion
        )

    override fun dependenciesFor(dependency: YarnListNode): List<YarnListNode> =
        dependency.children.orEmpty().filter { it.name in packageJsonForModuleId }

    override fun linkageFor(dependency: YarnListNode): PackageLinkage =
        PackageLinkage.DYNAMIC.takeUnless { dependency.isProject() } ?: PackageLinkage.PROJECT_DYNAMIC

    override fun createPackage(dependency: YarnListNode, issues: MutableCollection<Issue>): Package? {
        val packageJson = packageJsonForModuleId[dependency.name]?.takeUnless { dependency.isProject() } ?: return null

        return parsePackage(packageJson, moduleInfoResolver)
    }

    private fun YarnListNode.isProject(): Boolean = isProject(name)

    private fun isProject(moduleId: String) = moduleDirForModuleId[moduleId] in projectDirs
}
