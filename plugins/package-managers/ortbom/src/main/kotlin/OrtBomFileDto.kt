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

package org.ossreviewtoolkit.plugins.packagemanagers.ortbom

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.utils.spdx.SpdxExpression

import kotlin.collections.orEmpty

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class OrtBomProjectDto(
    val projectName: String?,
    val authors: Set<String>? = emptySet(),
    val vcs: VcsDto?,
    val homepageUrl: String?,
    val dependencies: List<DependencyDto> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class DependencyDto(
    val purl: String,
    val vcs: VcsDto?,
    val sourceArtifact: SourceArtifactDto?,
    val declaredLicenses: Set<String> = emptySet(),
    val concludedLicense: SpdxExpression?,
    val description: String?,
    val homepageUrl: String?,
    val isExcluded: Boolean = false,
    val isDynamicallyLinked: Boolean = false,
    val labels: Map<String, String> = emptyMap(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class SourceArtifactDto(
    val url: String?,
    val hash: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class VcsDto(
    val type: String?,
    val url: String?,
    val revision: String?,
    val path: String?
)

private const val DEFAULT_PROJECT_NAME = "unknown"
private const val EXCLUDED_SCOPE_NAME = "excluded"
private const val MAIN_SCOPE_NAME = "main"
private const val PROJECT_TYPE = "Unmanaged" // This refers to the package manager (plugin) named "Unmanaged".

internal fun OrtBomProjectDto.extractProject(): Project =
    Project.EMPTY.copy(
        id = Identifier(
            name = projectName?.takeUnless { it.isBlank() } ?: DEFAULT_PROJECT_NAME,
            type = PROJECT_TYPE,
            namespace = "",
            version = ""
        ),
        authors = authors.orEmpty(),
        vcs = vcs.toVcsInfo(),
        homepageUrl = homepageUrl.orEmpty(),
        scopeDependencies = setOfNotNull(
            dependencies.filterNot { it.isExcluded }.toScope(MAIN_SCOPE_NAME),
            dependencies.filter { it.isExcluded }.toScope(EXCLUDED_SCOPE_NAME)
        )
    )

internal fun OrtBomProjectDto.extractPackages(): Set<Package> {
    return dependencies.mapTo(mutableSetOf()) { it.toPackage() }
}

private fun DependencyDto.toPackage(): Package =
    Package(
        id = purlToIdentifier(purl),
        purl = purl,
        sourceArtifact = sourceArtifact?.let { RemoteArtifact(url = it.url.orEmpty(), if (it.hash != null) Hash.create(it.hash) else Hash.NONE) }.orEmpty(),
        vcs = vcs.toVcsInfo(),
        declaredLicenses = declaredLicenses,
        concludedLicense = concludedLicense,
        description = description.orEmpty(),
        homepageUrl = homepageUrl.orEmpty(),
        binaryArtifact = RemoteArtifact.EMPTY,
        labels = labels
    )


private fun VcsDto?.toVcsInfo(): VcsInfo =
    if (this == null) {
        VcsInfo.EMPTY
    } else {
        VcsInfo(
            type = type?.let { VcsType.forName(it) } ?: VcsType.UNKNOWN,
            url = url.orEmpty(),
            revision = revision.orEmpty(),
            path = path.orEmpty()
        )
    }

private fun Collection<DependencyDto>.toScope(name: String): Scope =
    Scope(
        name = name,
        dependencies = mapTo(mutableSetOf()) { dependency ->
            PackageReference(
                id = purlToIdentifier(dependency.purl),
                linkage = PackageLinkage.STATIC.takeUnless { dependency.isDynamicallyLinked } ?: PackageLinkage.DYNAMIC
            )
        }
    )


private fun purlToIdentifier(purl:String): Identifier {
    val purlParts = purl.replaceFirst("pkg:", "").split("/")

    when (purlParts.size) {
        3 -> { // with namespace
            return Identifier(
                type = purlParts[0],
                namespace = purlParts[1],
                name = purlParts[2].split("@")[0],
                version = purlParts[2].split("@")[1]
            )
        }
        2 -> { // No namespace
            return Identifier(
                type = purlParts[0],
                namespace = "",
                name = purlParts[2].split("@")[0],
                version = purlParts[2].split("@")[1]
            )
        }
        else -> {
            return Identifier.EMPTY
        }
    }
}
