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

package org.ossreviewtoolkit.plugins.packagemanagers.node.npm

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
import org.ossreviewtoolkit.plugins.packagemanagers.node.splitNamespaceAndName
import org.ossreviewtoolkit.utils.common.realFile

internal class NpmDependencyHandler(
    private val moduleInfoResolver: ModuleInfoResolver
) : DependencyHandler<ModuleReference> {
    private val packageJsonCache = mutableMapOf<File, PackageJson>()

    override fun identifierFor(dependency: ModuleReference): Identifier {
        val moduleInfo = dependency.moduleInfo
        val type = with(NodePackageManagerType.NPM) { if (moduleInfo.isProject) projectType else packageType }
        val (namespace, name) = splitNamespaceAndName(moduleInfo.name.orEmpty())
        val version = if (moduleInfo.isProject) {
            val packageJson = packageJsonCache.getOrPut(moduleInfo.packageJsonFile.realFile) {
                parsePackageJson(moduleInfo.packageJsonFile)
            }

            packageJson.version.orEmpty()
        } else {
            moduleInfo.version?.takeUnless { it.startsWith("link:") || it.startsWith("file:") }.orEmpty()
        }

        return Identifier(type, namespace, name, version)
    }

    override fun dependenciesFor(dependency: ModuleReference): List<ModuleReference> =
        dependency.dependencies.filter { it.moduleInfo.isInstalled }

    override fun linkageFor(dependency: ModuleReference): PackageLinkage =
        PackageLinkage.DYNAMIC.takeUnless { dependency.moduleInfo.isProject } ?: PackageLinkage.PROJECT_DYNAMIC

    override fun createPackage(dependency: ModuleReference, issues: MutableCollection<Issue>): Package? =
        dependency.takeUnless { it.moduleInfo.isProject || !it.moduleInfo.isInstalled }?.let {
            parsePackage(it.moduleInfo.packageJsonFile, moduleInfoResolver)
        }
}

internal data class ModuleReference(
    val moduleInfo: ModuleInfo,
    val dependencies: List<ModuleReference>
)

internal val ModuleInfo.packageJsonFile: File get() {
    check(isInstalled) { "The module directory '$path' is null or does not exist." }
    return File(path, NodePackageManagerType.DEFINITION_FILE)
}
