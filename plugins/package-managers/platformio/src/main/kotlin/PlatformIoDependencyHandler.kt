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

package org.ossreviewtoolkit.plugins.packagemanagers.platformio

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.utils.DependencyHandler

/**
 * A package resolved for a specific PlatformIO project environment.
 */
internal class PlatformIoPackage(
    val manifest: LibraryManifest,
    val authors: Set<String>,
    val owner: String?,
    val version: String
) {
    var dependencies: List<PlatformIoPackage> = emptyList()
}

internal class PlatformIoDependencyHandler : DependencyHandler<PlatformIoPackage> {
    override fun identifierFor(dependency: PlatformIoPackage) = dependency.toIdentifier()

    override fun dependenciesFor(dependency: PlatformIoPackage) = dependency.dependencies

    // Packages are compiled from source together with the firmware, so they are always linked statically.
    override fun linkageFor(dependency: PlatformIoPackage) = PackageLinkage.STATIC

    override fun createPackage(dependency: PlatformIoPackage, issues: MutableCollection<Issue>) = dependency.toPackage()
}

internal fun PlatformIoPackage.toIdentifier() =
    Identifier(
        type = PACKAGE_TYPE,
        namespace = owner.orEmpty(),
        name = manifest.name,
        version = version
    )

private fun PlatformIoPackage.toVcsInfo() =
    VcsInfo(
        type = VcsType.forName(manifest.repository?.type.orEmpty()),
        url = manifest.repository?.url.orEmpty(),
        revision = ""
    )

private fun PlatformIoPackage.toPackage() =
    Package(
        id = toIdentifier(),
        authors = authors,
        declaredLicenses = setOfNotNull(manifest.license?.takeUnless { it.isBlank() }),
        description = manifest.description.orEmpty(),
        homepageUrl = manifest.homepage.orEmpty(),
        binaryArtifact = RemoteArtifact.EMPTY,
        sourceArtifact = RemoteArtifact.EMPTY,
        vcs = toVcsInfo()
    )
