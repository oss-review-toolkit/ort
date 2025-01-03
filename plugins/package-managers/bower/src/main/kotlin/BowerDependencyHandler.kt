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

package org.ossreviewtoolkit.plugins.packagemanagers.bower

import java.io.File

import org.ossreviewtoolkit.analyzer.PackageManager.Companion.processProjectVcs
import org.ossreviewtoolkit.analyzer.parseAuthorString
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.utils.DependencyHandler

internal class BowerDependencyHandler : DependencyHandler<PackageInfo> {
    override fun identifierFor(dependency: PackageInfo) = dependency.toIdentifier()

    override fun dependenciesFor(dependency: PackageInfo) = dependency.dependencies.values.toList()

    override fun linkageFor(dependency: PackageInfo) = PackageLinkage.DYNAMIC

    override fun createPackage(dependency: PackageInfo, issues: MutableCollection<Issue>) = dependency.toPackage()
}

internal fun PackageInfo.toIdentifier() =
    Identifier(
        type = "Bower",
        namespace = "",
        name = pkgMeta.name.orEmpty(),
        version = pkgMeta.version.orEmpty()
    )

private fun PackageInfo.toVcsInfo() =
    VcsInfo(
        type = VcsType.forName(pkgMeta.repository?.type.orEmpty()),
        url = pkgMeta.repository?.url ?: pkgMeta.source.orEmpty(),
        revision = pkgMeta.resolution?.commit ?: pkgMeta.resolution?.tag.orEmpty()
    )

private fun PackageInfo.toPackage() =
    Package(
        id = toIdentifier(),
        // See https://github.com/bower/spec/blob/master/json.md#authors.
        authors = pkgMeta.authors.flatMap { parseAuthorString(it.name) }.mapNotNullTo(mutableSetOf()) { it.name },
        declaredLicenses = setOfNotNull(pkgMeta.license?.takeUnless { it.isEmpty() }),
        description = pkgMeta.description.orEmpty(),
        homepageUrl = pkgMeta.homepage.orEmpty(),
        binaryArtifact = RemoteArtifact.EMPTY,
        // TODO: Get the URL for tarballs hosted on private registries. Note that the public Bower registry does not
        //       contain any actual source (or binary) packages. Instead, it is a simple key-value store pointing from a
        //       package name to its belonging Git repository.
        sourceArtifact = RemoteArtifact.EMPTY,
        vcs = toVcsInfo()
    )

internal fun PackageInfo.toProject(definitionFile: File, projectType: String, scopeNames: Set<String>) =
    with(toPackage()) {
        Project(
            id = id.copy(type = projectType),
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            authors = authors,
            declaredLicenses = declaredLicenses,
            vcs = vcs,
            vcsProcessed = processProjectVcs(definitionFile.parentFile, vcs, homepageUrl),
            homepageUrl = homepageUrl,
            scopeNames = scopeNames
        )
    }
