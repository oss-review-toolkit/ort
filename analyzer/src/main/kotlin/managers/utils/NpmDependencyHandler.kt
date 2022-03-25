/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.analyzer.managers.utils

import java.io.File

import org.ossreviewtoolkit.analyzer.managers.Npm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.utils.DependencyHandler

/**
 * A data class storing information about a specific NPM module and its dependencies.
 *
 * Instances of this class are used as the dependency node type when constructing a dependency graph for NPM. They
 * contain all the information required to identify a module, construct a [Package] from it, and traverse its
 * dependency tree.
 */
data class NpmModuleInfo(
    /** The identifier for the represented module. */
    val id: Identifier,

    /** The working directory of the NPM project. */
    val workingDir: File,

    /** The file pointing to the package.json for this module. */
    val packageFile: File,

    /** A set with information about the modules this module depends on. */
    val dependencies: Set<NpmModuleInfo>
)

/**
 * A specialized [DependencyHandler] implementation for NPM.
 */
class NpmDependencyHandler(private val npm: Npm) : DependencyHandler<NpmModuleInfo> {
    override fun identifierFor(dependency: NpmModuleInfo): Identifier = dependency.id

    override fun dependenciesFor(dependency: NpmModuleInfo): Collection<NpmModuleInfo> = dependency.dependencies

    override fun linkageFor(dependency: NpmModuleInfo): PackageLinkage = PackageLinkage.DYNAMIC

    override fun createPackage(dependency: NpmModuleInfo, issues: MutableList<OrtIssue>): Package =
        npm.parsePackage(dependency.workingDir, dependency.packageFile).second
}
