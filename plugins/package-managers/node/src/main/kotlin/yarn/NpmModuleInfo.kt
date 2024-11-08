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
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project

/**
 * A data class storing information about a specific NPM module and its dependencies.
 *
 * Instances of this class are used as the dependency node type when constructing a dependency graph for NPM. They
 * contain all the information required to identify a module, construct a [Package] from it, and traverse its
 * dependency tree.
 */
internal data class NpmModuleInfo(
    /** The identifier for the represented module. */
    val id: Identifier,

    /** The working directory of the NPM project. */
    val workingDir: File,

    /** The file pointing to the package.json for this module. */
    val packageFile: File,

    /** A set with information about the modules this module depends on. */
    val dependencies: Set<NpmModuleInfo>,

    /** A flag indicating whether this module is a [Project] or a [Package]. */
    val isProject: Boolean
) {
    /**
     * [workingDir] and [packageFile] are not relevant when adding this [NpmModuleInfo] to the dependency graph.
     * However, if these values differ the same dependencies are added as duplicates to the set which is used to create
     * the dependency graph. Therefore, remove them from the equals check.
     */
    override fun equals(other: Any?): Boolean =
        (other === this) || (other is NpmModuleInfo && other.id == id && other.dependencies == dependencies)

    override fun hashCode() = 31 * id.hashCode() + dependencies.hashCode()
}
