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
import org.ossreviewtoolkit.plugins.packagemanagers.node.parsePackageJson
import org.ossreviewtoolkit.plugins.packagemanagers.node.pnpm.ModuleInfo.Dependency
import org.ossreviewtoolkit.utils.common.realFile

internal class PnpmDependencyHandler : DependencyHandler<Dependency> {
    private val workspaceModuleDirs = mutableSetOf<File>()

    private fun Dependency.isProject() = File(path).realFile().absoluteFile in workspaceModuleDirs

    fun setWorkspaceModuleDirs(dirs: Collection<File>) {
        workspaceModuleDirs.apply {
            clear()
            addAll(dirs)
        }
    }

    override fun identifierFor(dependency: Dependency): Identifier {
        val type = "PNPM".takeIf { dependency.isProject() } ?: "NPM"
        val namespace = dependency.from.substringBeforeLast("/", "")
        val name = dependency.from.substringAfterLast("/")
        val version = if (dependency.isProject()) {
            // TODO: Read each package.json only once?! (use caching).
            parsePackageJson(File(dependency.path).resolve("package.json")).version.orEmpty()
        } else {
            dependency.version.takeUnless { it.startsWith("link:") || it.startsWith("file:") }.orEmpty()
        }

        return Identifier(type, namespace, name, version)
    }

    override fun dependenciesFor(dependency: Dependency): List<Dependency> =
        (dependency.dependencies + dependency.optionalDependencies).values.toList()

    override fun linkageFor(dependency: Dependency): PackageLinkage =
        PackageLinkage.DYNAMIC.takeUnless { dependency.isProject() } ?: PackageLinkage.PROJECT_DYNAMIC

    override fun createPackage(dependency: Dependency, issues: MutableCollection<Issue>): Package? =
        dependency.takeUnless { it.isProject() }?.let { parsePackage(File(it.path).resolve("package.json")) }
}
