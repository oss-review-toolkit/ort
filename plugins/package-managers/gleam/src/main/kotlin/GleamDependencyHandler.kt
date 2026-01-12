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

import java.io.File

import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.SourceCodeOrigin
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.utils.DependencyHandler
import org.ossreviewtoolkit.model.utils.prependPath
import org.ossreviewtoolkit.plugins.packagemanagers.gleam.GleamManifest.Package.SourceType
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl

/**
 * A [DependencyHandler] for Gleam dependencies.
 */
internal class GleamDependencyHandler : DependencyHandler<GleamManifest.Package> {
    private lateinit var context: GleamProjectContext
    private val manifestPackagesByName = mutableMapOf<String, GleamManifest.Package>()

    fun setContext(context: GleamProjectContext) {
        this.context = context

        manifestPackagesByName.apply {
            clear()
            context.manifest.packages.associateByTo(this) { it.name }
        }
    }

    override fun identifierFor(dependency: GleamManifest.Package): Identifier = dependency.toIdentifier()

    override fun dependenciesFor(dependency: GleamManifest.Package): List<GleamManifest.Package> =
        dependency.requirements.mapNotNull { manifestPackagesByName[it] }

    override fun linkageFor(dependency: GleamManifest.Package): PackageLinkage =
        if (dependency.isProject(context)) PackageLinkage.PROJECT_DYNAMIC else PackageLinkage.DYNAMIC

    override fun createPackage(dependency: GleamManifest.Package, issues: MutableCollection<Issue>): Package? =
        dependency.toOrtPackage(context, issues)
}

private fun GleamManifest.Package.toIdentifier(): Identifier {
    val type = when (source) {
        SourceType.HEX -> PACKAGE_TYPE_HEX
        SourceType.GIT -> PACKAGE_TYPE_OTP
        SourceType.LOCAL -> PROJECT_TYPE
    }

    return Identifier(
        type = type,
        namespace = "",
        name = name,
        version = version
    )
}

private fun GleamManifest.Package.toOrtPackage(
    context: GleamProjectContext,
    issues: MutableCollection<Issue>
): Package? =
    when (source) {
        SourceType.HEX -> createHexPackage(context)
        SourceType.GIT -> createGitPackage()
        SourceType.LOCAL -> createLocalPackage(context, issues)
    }

private fun GleamManifest.Package.createHexPackage(context: GleamProjectContext): Package {
    val hexInfo = context.hexClient.getPackageInfo(name)
    val repositoryUrl = hexInfo?.meta?.links?.get("Repository").orEmpty()
    val vcs = VcsHost.parseUrl(repositoryUrl)

    return Package(
        id = toIdentifier(),
        cpe = generateCpe(repositoryUrl, version),
        authors = hexInfo?.let { resolveAuthors(it.owners, context.hexClient) }.orEmpty(),
        declaredLicenses = hexInfo?.meta?.licenses?.toSet().orEmpty(),
        description = hexInfo?.meta?.description.orEmpty(),
        homepageUrl = hexInfo?.meta?.links?.get("Website") ?: "https://hex.pm/packages/$name",
        binaryArtifact = RemoteArtifact.EMPTY,
        sourceArtifact = outerChecksum?.let {
            RemoteArtifact(
                url = "https://repo.hex.pm/tarballs/$name-$version.tar",
                hash = Hash(it, HashAlgorithm.SHA256)
            )
        } ?: RemoteArtifact.EMPTY,
        vcs = vcs,
        sourceCodeOrigins = listOf(SourceCodeOrigin.ARTIFACT, SourceCodeOrigin.VCS)
    )
}

private fun GleamManifest.Package.createGitPackage(): Package {
    val vcs = VcsHost.parseUrl(repo.orEmpty()).let {
        if (!commit.isNullOrEmpty()) it.copy(revision = commit) else it
    }

    return Package(
        id = toIdentifier(),
        cpe = generateCpe(repo.orEmpty(), version),
        authors = emptySet(),
        declaredLicenses = emptySet(),
        description = "",
        homepageUrl = toHomepageUrl(repo.orEmpty()),
        binaryArtifact = RemoteArtifact.EMPTY,
        sourceArtifact = RemoteArtifact.EMPTY,
        vcs = vcs,
        sourceCodeOrigins = listOf(SourceCodeOrigin.VCS)
    )
}

private fun GleamManifest.Package.createLocalPackage(
    context: GleamProjectContext,
    issues: MutableCollection<Issue>
): Package? {
    // If this is a project being analyzed, don't create a package.
    if (isProject(context)) return null

    val basePackage = Package(
        id = toIdentifier(),
        authors = emptySet(),
        declaredLicenses = emptySet(),
        description = "",
        homepageUrl = context.project.homepageUrl,
        binaryArtifact = RemoteArtifact.EMPTY,
        sourceArtifact = RemoteArtifact.EMPTY,
        vcs = VcsInfo.EMPTY,
        sourceCodeOrigins = listOf(SourceCodeOrigin.VCS)
    )

    if (!isValidLocalPath(context.analysisRoot, context.workingDir, localPath.orEmpty())) {
        issues += Issue(
            source = PROJECT_TYPE,
            message = "Path dependency '$name' with path '$localPath' points outside the project directory.",
            severity = Severity.WARNING
        )

        return basePackage
    }

    val normalizedPath = File(localPath.orEmpty()).normalize().path
    val vcs = context.project.vcsProcessed.copy(
        path = normalizedPath.prependPath(context.project.vcsProcessed.path)
    )

    return basePackage.copy(vcs = vcs, vcsProcessed = vcs.normalize())
}

private fun GleamManifest.Package.isProject(context: GleamProjectContext): Boolean {
    if (source != SourceType.LOCAL) return false
    val resolvedPath = context.workingDir.resolve(localPath.orEmpty()).canonicalFile
    return resolvedPath in context.projectDirs
}

/**
 * Generate a CPE identifier from a GitHub repository URL.
 */
private fun generateCpe(repositoryUrl: String, version: String): String? {
    if (repositoryUrl.isBlank()) return null

    val vcsInfo = VcsHost.parseUrl(repositoryUrl)
    if (vcsInfo == VcsInfo.EMPTY) return null

    val githubPrefix = "https://github.com/"
    if (!vcsInfo.url.startsWith(githubPrefix)) return null

    val pathSegments = vcsInfo.url.removePrefix(githubPrefix).split("/")
    if (pathSegments.size < 2) return null

    val owner = pathSegments[0]
    val repo = pathSegments[1].removeSuffix(".git")

    return "cpe:2.3:a:$owner:$repo:$version:*:*:*:*:*:*:*"
}

/**
 * Validate that a local path dependency stays within the analysis root directory.
 * Returns true if the resolved path is within the analysis root.
 */
private fun isValidLocalPath(analysisRoot: File, workingDir: File, path: String): Boolean {
    val resolvedPath = workingDir / path
    return resolvedPath.canonicalFile.startsWith(analysisRoot.canonicalFile)
}

/**
 * Resolve owners to formatted author strings by fetching user details from Hex API.
 */
private fun resolveAuthors(owners: List<HexPackageInfo.Owner>, hexClient: HexApiClient): Set<String> =
    owners.mapNotNull { it.username }
        .mapTo(mutableSetOf()) { username ->
            val userInfo = hexClient.getUserInfo(username) ?: HexUserInfo(username)
            val name = userInfo.fullName ?: userInfo.username
            userInfo.email?.takeIf { it.isNotEmpty() }?.let { "$name <$it>" } ?: name
        }

/**
 * Convert a VCS URL to a browsable homepage URL.
 */
private fun toHomepageUrl(vcsUrl: String): String {
    if (vcsUrl.isBlank()) return vcsUrl
    val normalizedUrl = normalizeVcsUrl(vcsUrl)
    val host = VcsHost.fromUrl(normalizedUrl) ?: return vcsUrl
    val vcsInfo = host.toVcsInfo(normalizedUrl) ?: return vcsUrl
    return host.toPermalink(vcsInfo) ?: vcsUrl
}
