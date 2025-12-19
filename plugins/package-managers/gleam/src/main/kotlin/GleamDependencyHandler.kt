/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.gleam

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.utils.DependencyHandler

/**
 * A [DependencyHandler] for Gleam dependencies.
 */
internal class GleamDependencyHandler : DependencyHandler<GleamPackageInfo> {
    private lateinit var context: GleamProjectContext
    private val manifestPackagesByName = mutableMapOf<String, GleamManifest.Package>()

    fun setContext(context: GleamProjectContext) {
        this.context = context

        manifestPackagesByName.apply {
            clear()
            context.manifest.packages.associateByTo(this) { it.name }
        }
    }

    override fun identifierFor(dependency: GleamPackageInfo): Identifier =
        dependency.getManifestPackageInfoOrDefault().toIdentifier(context)

    override fun dependenciesFor(dependency: GleamPackageInfo): List<GleamPackageInfo> =
        dependency
            .getManifestPackageInfoOrDefault()
            .dependencies
            .mapNotNull { manifestPackagesByName[it] }
            .map { ManifestPackageInfo(it) }

    override fun linkageFor(dependency: GleamPackageInfo): PackageLinkage =
        if (dependency.getManifestPackageInfoOrDefault().isProject(context)) {
            PackageLinkage.PROJECT_DYNAMIC
        } else {
            PackageLinkage.DYNAMIC
        }

    override fun createPackage(dependency: GleamPackageInfo, issues: MutableCollection<Issue>): Package? =
        dependency.getManifestPackageInfoOrDefault().toOrtPackage(context, issues)

    private fun GleamPackageInfo.getManifestPackageInfoOrDefault(): GleamPackageInfo =
        when (this) {
            is ManifestPackageInfo -> this
            is DependencyPackageInfo -> manifestPackagesByName[name]?.let {
                ManifestPackageInfo(it)
            } ?: this
            // TODO: This is a dependency entry from a 'gleam.toml' which does not have a corresponding
            // entry in the lockfile. This case happens in case the lockfile is absent and needs to be
            // improved, see #11245.
        }
}
