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
) : DependencyHandler<ModuleInfo> {
    private val packageJsonCache = mutableMapOf<File, PackageJson>()

    override fun identifierFor(dependency: ModuleInfo): Identifier {
        val type = if (dependency.isProject) NodePackageManagerType.NPM.projectType else "NPM"
        val (namespace, name) = splitNamespaceAndName(dependency.name.orEmpty())
        val version = if (dependency.isProject) {
            val packageJson = packageJsonCache.getOrPut(dependency.packageJsonFile.realFile) {
                parsePackageJson(dependency.packageJsonFile)
            }

            packageJson.version.orEmpty()
        } else {
            dependency.version?.takeUnless { it.startsWith("link:") || it.startsWith("file:") }.orEmpty()
        }

        return Identifier(type, namespace, name, version)
    }

    override fun dependenciesFor(dependency: ModuleInfo): List<ModuleInfo> =
        dependency.dependencies.values.filter { it.isInstalled }

    override fun linkageFor(dependency: ModuleInfo): PackageLinkage =
        PackageLinkage.DYNAMIC.takeUnless { dependency.isProject } ?: PackageLinkage.PROJECT_DYNAMIC

    override fun createPackage(dependency: ModuleInfo, issues: MutableCollection<Issue>): Package? =
        dependency.takeUnless { it.isProject || !it.isInstalled }?.let {
            parsePackage(it.packageJsonFile, moduleInfoResolver)
        }
}

internal val ModuleInfo.isInstalled: Boolean get() = path != null

internal val ModuleInfo.isProject: Boolean get() = resolved == null

private val ModuleInfo.packageJsonFile: File get() =
    File(
        checkNotNull(path) {
            "The path to '${NodePackageManagerType.DEFINITION_FILE}' is null in $this."
        },
        NodePackageManagerType.DEFINITION_FILE
    )
