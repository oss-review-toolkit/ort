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

internal class Yarn2DependencyHandler(
    private val moduleInfoResolver: ModuleInfoResolver
) : DependencyHandler<PackageInfo> {
    private val packageJsonForModuleId = mutableMapOf<String, PackageJson>()
    private val moduleDirForModuleId = mutableMapOf<String, File>()
    private val packageInfoForLocator = mutableMapOf<String, PackageInfo>()
    private lateinit var workingDir: File

    fun setContext(workingDir: File, moduleDirs: Set<File>, packageInfoForLocator: Map<String, PackageInfo>) {
        this.workingDir = workingDir

        this.packageInfoForLocator.apply {
            clear()
            putAll(packageInfoForLocator)
        }

        packageJsonForModuleId.clear()
        moduleDirForModuleId.clear()

        moduleDirs.forEach { moduleDir ->
            val packageJson = parsePackageJson(moduleDir.resolve(NodePackageManagerType.DEFINITION_FILE))
            packageJsonForModuleId[packageJson.moduleId] = packageJson
            moduleDirForModuleId[packageJson.moduleId] = moduleDir
        }
    }

    override fun identifierFor(dependency: PackageInfo): Identifier =
        Identifier(
            type = if (dependency.isProject) NodePackageManagerType.YARN2.projectType else "NPM",
            namespace = dependency.moduleName.substringBefore("/", ""),
            name = dependency.moduleName.substringAfter("/"),
            version = dependency.children.version
        )

    override fun dependenciesFor(dependency: PackageInfo): List<PackageInfo> =
        dependency.children.dependencies.map { packageInfoForLocator.getValue(it.locator) }

    override fun linkageFor(dependency: PackageInfo): PackageLinkage =
        if (dependency.isProject) PackageLinkage.PROJECT_DYNAMIC else PackageLinkage.DYNAMIC

    override fun createPackage(dependency: PackageInfo, issues: MutableCollection<Issue>): Package? {
        val packageJson = packageJsonForModuleId[dependency.moduleId]?.takeUnless { dependency.isProject }
            ?: return null

        return parsePackage(packageJson, moduleInfoResolver)
    }
}

internal val PackageInfo.isProject: Boolean get() = value.substringAfterLast("@").startsWith("workspace:")

internal val PackageInfo.moduleName: String get() = value.substringBeforeLast("@")

internal val PackageInfo.moduleId: String get() = buildString {
    append(moduleName)

    if (children.version.isNotBlank()) {
        append("@")
        append(children.version)
    }
}
