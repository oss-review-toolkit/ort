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
import org.ossreviewtoolkit.plugins.packagemanagers.node.PackageJson
import org.ossreviewtoolkit.plugins.packagemanagers.node.parsePackage

internal class YarnDependencyHandler(private val yarn: Yarn) : DependencyHandler<YarnListNode> {
    private val packageJsonForModuleId = mutableMapOf<String, PackageJson>()
    private lateinit var workingDir: File

    fun setContext(workingDir: File, packageJsonForModuleId: Map<String, PackageJson>) {
        this.workingDir = workingDir

        this.packageJsonForModuleId.apply {
            clear()
            putAll(packageJsonForModuleId)
        }
    }

    override fun identifierFor(dependency: YarnListNode): Identifier =
        Identifier(
            type = "NPM",
            namespace = dependency.moduleName.substringBefore("/", ""),
            name = dependency.moduleName.substringAfter("/"),
            version = dependency.moduleVersion
        )

    override fun dependenciesFor(dependency: YarnListNode): List<YarnListNode> =
        dependency.children.orEmpty().filter { it.name in packageJsonForModuleId }

    override fun linkageFor(dependency: YarnListNode): PackageLinkage = PackageLinkage.DYNAMIC

    override fun createPackage(dependency: YarnListNode, issues: MutableCollection<Issue>): Package? {
        val packageJson = packageJsonForModuleId[dependency.name] ?: return null

        return parsePackage(workingDir, packageJson, yarn::getRemotePackageDetails)
    }
}
