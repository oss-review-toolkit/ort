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

package org.ossreviewtoolkit.plugins.packagemanagers.ortproject

import java.io.File

import org.ossreviewtoolkit.analyzer.PackageManager.Companion.processProjectVcs
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.model.utils.toIdentifier
import org.ossreviewtoolkit.model.utils.toPackageUrl
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.plugins.packagemanagers.ortproject.OrtProject.Dependency
import org.ossreviewtoolkit.plugins.packagemanagers.ortproject.OrtProject.SourceArtifact
import org.ossreviewtoolkit.plugins.packagemanagers.ortproject.OrtProject.Vcs

private const val DEFAULT_PROJECT_NAME = "unknown"
private const val PROJECT_TYPE = "OrtProjectFile"

internal fun OrtProject.mapToProject(definitionFile: File) =
    Project(
        id = Identifier(
            name = projectName?.takeUnless { it.isBlank() } ?: DEFAULT_PROJECT_NAME,
            type = PROJECT_TYPE,
            namespace = "",
            version = ""
        ),
        vcs = processProjectVcs(definitionFile.parentFile),
        description = description.orEmpty(),
        authors = authors,
        homepageUrl = homepageUrl.orEmpty(),
        definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
        declaredLicenses = declaredLicenses,
        scopeDependencies = dependencies.toScopes()
    )

internal fun OrtProject.mapToPackages(): Set<Package> = dependencies.mapTo(mutableSetOf()) { it.toPackage() }

private fun Dependency.toPackage(): Package =
    Package(
        id = toId(),
        purl = toPurl(),
        sourceArtifact = sourceArtifact?.toRemoteArtifact().orEmpty(),
        vcs = vcs?.toVcsInfo().orEmpty(),
        declaredLicenses = declaredLicenses,
        description = description.orEmpty(),
        homepageUrl = homepageUrl.orEmpty(),
        binaryArtifact = RemoteArtifact.EMPTY,
        authors = authors,
        labels = labels,
        isModified = isModified ?: false,
        isMetadataOnly = isMetadataOnly ?: false
    )

private fun Vcs.toVcsInfo(): VcsInfo =
    VcsInfo(
        type = VcsType.forName(type.uppercase()),
        url = url,
        revision = revision,
        path = path
    )

private fun SourceArtifact.toRemoteArtifact(): RemoteArtifact =
    RemoteArtifact(
        url = url,
        hash = Hash(hash.value.lowercase(), hash.algorithm)
    )

private fun Dependency.toId(): Identifier = id ?: checkNotNull(purl?.toPackageUrl()?.toIdentifier())

private fun Dependency.toPurl(): String = purl ?: checkNotNull(id).toPurl()

private fun Collection<Dependency>.toScopes(): Set<Scope> {
    val idsForScopeName = buildMap {
        this@toScopes.forEach { dependency ->
            dependency.scopes.orEmpty().forEach { scopeName ->
                getOrPut(scopeName) { mutableSetOf() } += dependency.toId()
            }
        }
    }

    return idsForScopeName.mapTo(mutableSetOf()) { (scopeName, ids) ->
        Scope(
            name = scopeName,
            dependencies = ids.mapTo(mutableSetOf()) { id -> PackageReference(id = id) }
        )
    }
}
