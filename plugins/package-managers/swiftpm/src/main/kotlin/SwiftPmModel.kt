/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.swiftpm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.utils.common.toUri
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl

val json = Json { ignoreUnknownKeys = true }

/**
 * The data model for the output of the command `swift package show-dependencies --format json`.
 */
@Serializable
data class SwiftPackage(
    val identity: String,
    val name: String,
    val url: String,
    val version: String,
    val path: String,
    val dependencies: List<Dependency>
) {
    @Serializable
    data class Dependency(
        val name: String,
        val version: String,
        @SerialName("url") val repositoryUrl: String,
        val dependencies: Set<Dependency>
    )
}

/**
 * See https://github.com/apple/swift-package-manager/blob/3ef830dddff459e569d6e49c186c3ded33c39bcc/Sources/PackageGraph/PinsStore.swift#L285-L384.
 */
@Serializable
data class PinV1(
    @SerialName("package") val packageName: String,
    val state: State?,
    @SerialName("repositoryURL") val repositoryUrl: String
) {
    @Serializable
    data class State(
        val version: String? = null,
        val revision: String? = null,
        val branch: String? = null
    )
}

/**
 * See https://github.com/apple/swift-package-manager/blob/3ef830dddff459e569d6e49c186c3ded33c39bcc/Sources/PackageGraph/PinsStore.swift#L387-L462.
 */
@Serializable
data class PinV2(
    val identity: String,
    val state: State?,
    val location: String
) {
    @Serializable
    data class State(
        val version: String? = null,
        val revision: String? = null,
        val branch: String? = null
    )
}

internal val SwiftPackage.Dependency.id: Identifier
    get() = Identifier(
        type = PACKAGE_TYPE,
        namespace = "",
        name = getCanonicalName(repositoryUrl),
        version = version.takeUnless { it == "unspecified" }.orEmpty()
    )

internal fun SwiftPackage.Dependency.toPackage(): Package {
    val vcsInfoFromUrl = VcsHost.parseUrl(repositoryUrl)
    val vcsInfo = vcsInfoFromUrl.takeUnless { it.revision.isBlank() } ?: vcsInfoFromUrl.copy(revision = version)

    return createPackage(id, vcsInfo)
}

internal fun PinV1.toPackage(): Package {
    val id = Identifier(
        type = PACKAGE_TYPE,
        namespace = "",
        name = getCanonicalName(repositoryUrl),
        version = state?.run {
            when {
                !version.isNullOrBlank() -> version
                !revision.isNullOrBlank() -> "revision-$revision"
                !branch.isNullOrBlank() -> "branch-$branch"
                else -> ""
            }
        }.orEmpty()
    )

    val vcsInfoFromUrl = VcsHost.parseUrl(repositoryUrl)
    val vcsInfo = if (vcsInfoFromUrl.revision.isBlank() && state != null) {
        when {
            !state.revision.isNullOrBlank() -> vcsInfoFromUrl.copy(revision = state.revision)
            !state.version.isNullOrBlank() -> vcsInfoFromUrl.copy(revision = state.version)
            else -> vcsInfoFromUrl
        }
    } else {
        vcsInfoFromUrl
    }

    return createPackage(id, vcsInfo)
}

internal fun PinV2.toPackage(): Package {
    val id = Identifier(
        type = PACKAGE_TYPE,
        namespace = "",
        name = getCanonicalName(location),
        version = state?.run {
            when {
                !version.isNullOrBlank() -> version
                !revision.isNullOrBlank() -> "revision-$revision"
                !branch.isNullOrBlank() -> "branch-$branch"
                else -> ""
            }
        }.orEmpty()
    )

    val vcsInfoFromUrl = VcsHost.parseUrl(location)
    val vcsInfo = if (vcsInfoFromUrl.revision.isBlank() && state != null) {
        when {
            !state.revision.isNullOrBlank() -> vcsInfoFromUrl.copy(revision = state.revision)
            !state.version.isNullOrBlank() -> vcsInfoFromUrl.copy(revision = state.version)
            else -> vcsInfoFromUrl
        }
    } else {
        vcsInfoFromUrl
    }

    return createPackage(id, vcsInfo)
}

private fun createPackage(id: Identifier, vcsInfo: VcsInfo) =
    Package(
        vcs = vcsInfo,
        description = "",
        id = id,
        binaryArtifact = RemoteArtifact.EMPTY,
        sourceArtifact = RemoteArtifact.EMPTY,
        declaredLicenses = emptySet(), // SPM files do not declare any licenses.
        homepageUrl = ""
    )

/**
 * Return the canonical name for a package based on the given [repositoryUrl].
 * The algorithm assumes that the repository URL does not point to the local file
 * system, as support for local dependencies is not implemented yet in ORT. Otherwise,
 * the algorithm tries to effectively mimic the algorithm described in
 * https://github.com/apple/swift-package-manager/blob/24bfdd180afdf78160e7a2f6f6deb2c8249d40d3/Sources/PackageModel/PackageIdentity.swift#L345-L415.
 */
internal fun getCanonicalName(repositoryUrl: String): String {
    val normalizedUrl = normalizeVcsUrl(repositoryUrl)
    return normalizedUrl.toUri {
        it.host + it.path.removeSuffix(".git")
    }.getOrDefault(normalizedUrl).lowercase()
}
