/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.python

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.utils.DependencyHandler
import org.ossreviewtoolkit.plugins.packagemanagers.python.utils.PythonCoreMetadata

/**
 * A [DependencyHandler] for the entries of PEP 751 lockfiles. The entries of each lockfile have to be registered via
 * [addEntries] before they are added to the dependency graph. As the graph creates a package only once per
 * identifier, the entry of the first lockfile wins if several lockfiles lock the same package with different
 * distribution files.
 */
internal class PylockDependencyHandler : DependencyHandler<PylockFile.Package> {
    private val dependencies = mutableMapOf<PylockFile.Package, List<PylockFile.Package>>()
    private val metadata = mutableMapOf<PylockFile.Package, PythonCoreMetadata?>()

    /**
     * Register the resolved [dependencies] of the entries of a lockfile and the [metadata] retrieved from the index
     * for them. Entries of previous lockfiles are kept, as the dependency graph is shared by all lockfiles.
     */
    fun addEntries(
        dependencies: Map<PylockFile.Package, List<PylockFile.Package>>,
        metadata: Map<PylockFile.Package, PythonCoreMetadata?>
    ) {
        this.dependencies += dependencies
        this.metadata += metadata
    }

    override fun identifierFor(dependency: PylockFile.Package): Identifier = dependency.toIdentifier()

    override fun dependenciesFor(dependency: PylockFile.Package): List<PylockFile.Package> =
        dependencies[dependency].orEmpty()

    override fun linkageFor(dependency: PylockFile.Package): PackageLinkage = PackageLinkage.DYNAMIC

    override fun createPackage(dependency: PylockFile.Package, issues: MutableCollection<Issue>): Package =
        dependency.toOrtPackage(metadata[dependency])
}
