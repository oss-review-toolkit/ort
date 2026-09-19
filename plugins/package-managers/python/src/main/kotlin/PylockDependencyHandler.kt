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

import java.io.File

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.utils.DependencyHandler
import org.ossreviewtoolkit.plugins.packagemanagers.python.utils.PythonCoreMetadata

/** A lockfile entry with its [lockfile], as equal entries of different lockfiles can have different dependencies. */
internal data class PylockDependency(val lockfile: File, val entry: PylockFile.Package)

/**
 * A [DependencyHandler] for the entries of PEP 751 lockfiles, which have to be registered via [addEntries]. If several
 * lockfiles lock the same package with different distribution files, the entry of the first lockfile wins.
 */
internal class PylockDependencyHandler : DependencyHandler<PylockDependency> {
    private val dependencies = mutableMapOf<PylockDependency, List<PylockDependency>>()
    private val metadata = mutableMapOf<PylockDependency, PythonCoreMetadata?>()

    /** Register the resolved [dependencies] and the [metadata] of the entries of the given [lockfile]. */
    fun addEntries(
        lockfile: File,
        dependencies: Map<PylockFile.Package, List<PylockFile.Package>>,
        metadata: Map<PylockFile.Package, PythonCoreMetadata?>
    ) {
        dependencies.forEach { (entry, entryDependencies) ->
            this.dependencies[PylockDependency(lockfile, entry)] = entryDependencies.map {
                PylockDependency(lockfile, it)
            }
        }

        metadata.forEach { (entry, entryMetadata) -> this.metadata[PylockDependency(lockfile, entry)] = entryMetadata }
    }

    override fun identifierFor(dependency: PylockDependency): Identifier = dependency.entry.toIdentifier()

    override fun dependenciesFor(dependency: PylockDependency): List<PylockDependency> =
        dependencies[dependency].orEmpty()

    override fun linkageFor(dependency: PylockDependency): PackageLinkage = PackageLinkage.DYNAMIC

    override fun createPackage(dependency: PylockDependency, issues: MutableCollection<Issue>): Package =
        dependency.entry.toOrtPackage(metadata[dependency])
}
