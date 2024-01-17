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

package org.ossreviewtoolkit.plugins.packagemanagers.spm

import java.lang.invoke.MethodHandles
import java.net.URI

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl

val json = Json { ignoreUnknownKeys = true }

abstract class SpmDependency {
    abstract val repositoryUrl: String
    abstract val vcs: VcsInfo
    abstract val id: Identifier

    fun toPackage(): Package {
        val (author, _) = parseAuthorAndProjectFromRepo(repositoryUrl)
        return Package(
            vcs = vcs,
            description = "",
            id = id,
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            authors = setOfNotNull(author),
            declaredLicenses = emptySet(), // SPM files do not declare any licenses.
            homepageUrl = ""
        )
    }
}

/**
 * The output of the `spm dependencies` command.
 */
@Serializable
data class SpmDependenciesOutput(
    val identity: String,
    val name: String,
    val url: String,
    val version: String,
    val path: String,
    val dependencies: List<LibraryDependency>
)

@Serializable
data class LibraryDependency(
    val name: String,
    val version: String,
    @SerialName("url") override val repositoryUrl: String,
    val dependencies: Set<LibraryDependency>
) : SpmDependency() {
    override val vcs: VcsInfo
        get() {
            val vcsInfoFromUrl = VcsHost.parseUrl(repositoryUrl)
            return vcsInfoFromUrl.takeUnless { it.revision.isBlank() } ?: vcsInfoFromUrl.copy(revision = version)
        }

    override val id: Identifier
        get() {
            val (author, _) = parseAuthorAndProjectFromRepo(repositoryUrl)
            return Identifier(
                type = PACKAGE_TYPE,
                namespace = author.orEmpty(),
                name = name,
                version = version
            )
        }
}

@Serializable
data class PackageResolved(
    @SerialName("object") val objects: Map<String, List<AppDependency>>,
    val version: Int
)

@Serializable
data class AppDependency(
    @SerialName("package") val packageName: String,
    val state: AppDependencyState?,
    @SerialName("repositoryURL") override val repositoryUrl: String
) : SpmDependency() {
    @Serializable
    data class AppDependencyState(
        val version: String? = null,
        val revision: String? = null,
        private val branch: String? = null
    ) {
        override fun toString(): String =
            when {
                !version.isNullOrBlank() -> version
                !revision.isNullOrBlank() -> "revision-$revision"
                !branch.isNullOrBlank() -> "branch-$branch"
                else -> ""
            }
    }

    override val vcs: VcsInfo
        get() {
            val vcsInfoFromUrl = VcsHost.parseUrl(repositoryUrl)

            if (vcsInfoFromUrl.revision.isBlank() && state != null) {
                when {
                    !state.revision.isNullOrBlank() -> return vcsInfoFromUrl.copy(revision = state.revision)
                    !state.version.isNullOrBlank() -> return vcsInfoFromUrl.copy(revision = state.version)
                }
            }

            return vcsInfoFromUrl
        }

    override val id: Identifier
        get() {
            val (author, _) = parseAuthorAndProjectFromRepo(repositoryUrl)
            return Identifier(
                type = PACKAGE_TYPE,
                namespace = author.orEmpty(),
                name = packageName,
                version = state?.toString().orEmpty()
            )
        }
}

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

internal fun parseAuthorAndProjectFromRepo(repositoryURL: String): Pair<String?, String?> {
    val normalizedURL = normalizeVcsUrl(repositoryURL)
    val vcsHost = VcsHost.fromUrl(URI(normalizedURL))
    val project = vcsHost?.getProject(normalizedURL)
    val author = vcsHost?.getUserOrOrganization(normalizedURL)

    if (author.isNullOrBlank()) {
        logger.warn {
            "Unable to parse the author from VCS URL $repositoryURL, results might be incomplete."
        }
    }

    if (project.isNullOrBlank()) {
        logger.warn {
            "Unable to parse the project from VCS URL $repositoryURL, results might be incomplete."
        }
    }

    return author to project
}
