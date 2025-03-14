/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.conan

import java.io.File

import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.utils.common.ProcessCapture

/**
 * A version handler interface for Conan. Its implementations provide the package manager logic for a specific Conan
 * version.
 */
internal interface ConanVersionHandler {
    /**
     * Get the Conan storage path i.e. the location where Conan caches downloaded packages.
     */
    fun getConanStoragePath(): File

    /**
     * Resolve the dependencies defined in the [definitionFile] and given the lockfile [lockFileName], using [jsonFile]
     * as temporary output. The results returned are the packages, the project package, the dependencies scope and the
     * build dependencies scope.
     */
    fun process(jsonFile: File, definitionFile: File, workingDir: File, lockFileName: String?): HandlerResults

    /**
     * Get the Conan data file for a package with the given [name] and [version] from the [conanStorageDir].
     */
    fun getConanDataFile(name: String, version: String, conanStorageDir: File): File

    /**
     * List configured remotes.
     */
    fun listRemotes(): ProcessCapture

    /**
     * Parse the [remoteList], which is the output of [listRemotes] to a list of remote.
     */
    fun parseRemoteList(remoteList: String): List<Pair<String, String>>

    /**
     * Run the command "conan inspect" for the given [pkgName] and write the output to [jsonFile].
     */
    fun runInspectFieldCommand(workingDir: File, pkgName: String, jsonFile: File)
}

/**
 * A simple container to return several data structures needed to build a [ProjectAnalyzerResult].
 */
internal data class HandlerResults(
    val packages: Map<String, Package>,
    val projectPackage: Package,
    val dependenciesScope: Scope,
    val devDependenciesScope: Scope
)
