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
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.SourceCodeOrigin
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.utils.prependPath
import org.ossreviewtoolkit.plugins.packagemanagers.gleam.GleamManifest.Package.SourceType
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl

import org.semver4j.Semver
import org.semver4j.range.RangeListFactory

/**
 * A sealed interface representing a Gleam dependency that can be either:
 * - From the manifest.toml lockfile (already resolved with transitive deps)
 * - From gleam.toml (needs dynamic resolution, no transitive deps)
 */
internal sealed interface GleamPackageInfo {
    val name: String
    val dependencies: List<String>

    fun toIdentifier(context: GleamProjectContext): Identifier
    fun toOrtPackage(context: GleamProjectContext, issues: MutableCollection<Issue>): Package?
    fun isProject(context: GleamProjectContext): Boolean
}

/**
 * A dependency from the manifest.toml lockfile - already resolved.
 */
internal data class ManifestPackageInfo(private val pkg: GleamManifest.Package) : GleamPackageInfo {
    override val name: String get() = pkg.name
    override val dependencies: List<String> get() = pkg.requirements

    override fun isProject(context: GleamProjectContext): Boolean {
        if (pkg.source != SourceType.LOCAL) return false
        val resolvedPath = context.workingDir.resolve(pkg.localPath.orEmpty()).canonicalFile
        return resolvedPath in context.projectDirs
    }

    override fun toIdentifier(context: GleamProjectContext): Identifier {
        val type = when (pkg.source) {
            SourceType.HEX -> PACKAGE_TYPE_HEX
            SourceType.GIT -> PACKAGE_TYPE_OTP
            SourceType.LOCAL -> PROJECT_TYPE
        }

        return Identifier(
            type = type,
            namespace = "",
            name = pkg.name,
            version = pkg.version
        )
    }

    override fun toOrtPackage(context: GleamProjectContext, issues: MutableCollection<Issue>): Package? =
        when (pkg.source) {
            SourceType.HEX -> createHexPackage(context)
            SourceType.GIT -> createGitPackage(context)
            SourceType.LOCAL -> createLocalPackage(context, issues)
        }

    private fun createHexPackage(context: GleamProjectContext): Package {
        val hexInfo = context.hexClient.getPackageInfo(pkg.name)
        val repositoryUrl = hexInfo?.meta?.links?.get("Repository").orEmpty()
        val vcs = VcsHost.parseUrl(repositoryUrl)

        return Package(
            id = toIdentifier(context),
            cpe = generateCpe(repositoryUrl, pkg.version),
            authors = hexInfo?.let { resolveAuthors(it.owners, context.hexClient) }.orEmpty(),
            declaredLicenses = hexInfo?.meta?.licenses?.toSet().orEmpty(),
            description = hexInfo?.meta?.description.orEmpty(),
            homepageUrl = hexInfo?.meta?.links?.get("Website") ?: "https://hex.pm/packages/${pkg.name}",
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = pkg.outerChecksum?.let {
                RemoteArtifact(
                    url = "https://repo.hex.pm/tarballs/${pkg.name}-${pkg.version}.tar",
                    hash = Hash(it, HashAlgorithm.SHA256)
                )
            } ?: RemoteArtifact.EMPTY,
            vcs = vcs,
            vcsProcessed = vcs.normalize(),
            sourceCodeOrigins = listOf(SourceCodeOrigin.ARTIFACT, SourceCodeOrigin.VCS)
        )
    }

    private fun createGitPackage(context: GleamProjectContext): Package {
        val vcs = VcsHost.parseUrl(pkg.repo.orEmpty()).let {
            if (!pkg.commit.isNullOrEmpty()) it.copy(revision = pkg.commit) else it
        }

        return Package(
            id = toIdentifier(context),
            cpe = generateCpe(pkg.repo.orEmpty(), pkg.version),
            authors = emptySet(),
            declaredLicenses = emptySet(),
            description = "",
            homepageUrl = toHomepageUrl(pkg.repo.orEmpty()),
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = vcs,
            vcsProcessed = vcs.normalize(),
            sourceCodeOrigins = listOf(SourceCodeOrigin.VCS)
        )
    }

    private fun createLocalPackage(context: GleamProjectContext, issues: MutableCollection<Issue>): Package? {
        // If this is a project being analyzed, don't create a package.
        if (isProject(context)) return null

        val basePackage = Package(
            id = toIdentifier(context),
            authors = emptySet(),
            declaredLicenses = emptySet(),
            description = "",
            homepageUrl = context.project.homepageUrl,
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = VcsInfo.EMPTY,
            vcsProcessed = VcsInfo.EMPTY,
            sourceCodeOrigins = listOf(SourceCodeOrigin.VCS)
        )

        if (!isValidLocalPath(context.analysisRoot, context.workingDir, pkg.localPath.orEmpty())) {
            issues += Issue(
                source = PROJECT_TYPE,
                message = "Path dependency '${pkg.name}' with path '${pkg.localPath}' " +
                    "points outside the project directory.",
                severity = Severity.WARNING
            )

            return basePackage
        }

        val normalizedPath = File(pkg.localPath.orEmpty()).normalize().path
        val vcs = context.project.vcsProcessed.copy(
            path = normalizedPath.prependPath(context.project.vcsProcessed.path)
        )

        return basePackage.copy(vcs = vcs, vcsProcessed = vcs.normalize())
    }
}

/**
 * A dependency from gleam.toml - needs dynamic resolution.
 */
internal data class DependencyPackageInfo(
    private val depName: String,
    private val dep: GleamToml.Dependency
) : GleamPackageInfo {
    override val name: String get() = depName
    override val dependencies: List<String> get() = emptyList()

    override fun isProject(context: GleamProjectContext): Boolean {
        val pathDep = dep as? GleamToml.Dependency.Path ?: return false
        val resolvedPath = context.workingDir.resolve(pathDep.path).canonicalFile
        return resolvedPath in context.projectDirs
    }

    override fun toIdentifier(context: GleamProjectContext): Identifier =
        when (dep) {
            is GleamToml.Dependency.Hex -> {
                val version = resolveHexVersion(context).orEmpty()
                Identifier(PACKAGE_TYPE_HEX, "", depName, version)
            }

            is GleamToml.Dependency.Git -> Identifier(PACKAGE_TYPE_OTP, "", depName, dep.ref.orEmpty())

            is GleamToml.Dependency.Path -> {
                Identifier(PROJECT_TYPE, "", depName, resolvePathVersion(context))
            }
        }

    private fun resolvePathVersion(context: GleamProjectContext): String {
        val pathDep = dep as? GleamToml.Dependency.Path ?: return ""
        if (!isValidLocalPath(context.analysisRoot, context.workingDir, pathDep.path)) return ""
        val localGleamToml = context.workingDir.resolve(pathDep.path).resolve("gleam.toml")
        return if (localGleamToml.isFile) {
            parseGleamToml(localGleamToml).version
        } else {
            ""
        }
    }

    override fun toOrtPackage(context: GleamProjectContext, issues: MutableCollection<Issue>): Package? =
        when (dep) {
            is GleamToml.Dependency.Hex -> createHexPackage(context)
            is GleamToml.Dependency.Git -> createGitPackage(context)
            is GleamToml.Dependency.Path -> createPathPackage(context, issues)
        }

    private fun resolveHexVersion(context: GleamProjectContext): String? {
        val hexDep = dep as? GleamToml.Dependency.Hex ?: return null
        val packageInfo = context.hexClient.getPackageInfo(depName) ?: return null
        val versions = packageInfo.releases.map { it.version }
        return findMatchingVersion(versions, hexDep.version)
    }

    private fun createHexPackage(context: GleamProjectContext): Package {
        val hexDep = dep as GleamToml.Dependency.Hex
        val packageInfo = context.hexClient.getPackageInfo(depName)
        val versions = packageInfo?.releases?.map { it.version }.orEmpty()
        val version = findMatchingVersion(versions, hexDep.version).orEmpty()
        val checksum = version.takeIf { it.isNotEmpty() }?.let {
            context.hexClient.getReleaseInfo(depName, it)?.checksum
        }

        val repositoryUrl = packageInfo?.meta?.links?.get("Repository").orEmpty()
        val vcs = VcsHost.parseUrl(repositoryUrl)

        return Package(
            id = toIdentifier(context),
            cpe = generateCpe(repositoryUrl, version),
            authors = packageInfo?.let { resolveAuthors(it.owners, context.hexClient) }.orEmpty(),
            declaredLicenses = packageInfo?.meta?.licenses?.toSet().orEmpty(),
            description = packageInfo?.meta?.description.orEmpty(),
            homepageUrl = packageInfo?.meta?.links?.get("Website") ?: "https://hex.pm/packages/$depName",
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = checksum?.let {
                RemoteArtifact(
                    url = "https://repo.hex.pm/tarballs/$depName-$version.tar",
                    hash = Hash(it, HashAlgorithm.SHA256)
                )
            } ?: RemoteArtifact.EMPTY,
            vcs = vcs,
            vcsProcessed = vcs.normalize(),
            sourceCodeOrigins = listOf(SourceCodeOrigin.ARTIFACT, SourceCodeOrigin.VCS)
        )
    }

    private fun createGitPackage(context: GleamProjectContext): Package {
        val gitDep = dep as GleamToml.Dependency.Git
        val vcs = VcsHost.parseUrl(gitDep.url).copy(revision = gitDep.ref.orEmpty())

        return Package(
            id = toIdentifier(context),
            cpe = generateCpe(gitDep.url, ""),
            authors = emptySet(),
            declaredLicenses = emptySet(),
            description = "",
            homepageUrl = toHomepageUrl(gitDep.url),
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = vcs,
            vcsProcessed = vcs.normalize(),
            sourceCodeOrigins = listOf(SourceCodeOrigin.VCS)
        )
    }

    private fun createPathPackage(context: GleamProjectContext, issues: MutableCollection<Issue>): Package? {
        // If this is a project being analyzed, don't create a package.
        if (isProject(context)) return null

        val pathDep = dep as GleamToml.Dependency.Path

        val basePackage = Package(
            id = toIdentifier(context),
            authors = emptySet(),
            declaredLicenses = emptySet(),
            description = "",
            homepageUrl = context.project.homepageUrl,
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = VcsInfo.EMPTY,
            vcsProcessed = VcsInfo.EMPTY,
            sourceCodeOrigins = listOf(SourceCodeOrigin.VCS)
        )

        if (!isValidLocalPath(context.analysisRoot, context.workingDir, pathDep.path)) {
            issues += Issue(
                source = PROJECT_TYPE,
                message = "Path dependency '$depName' with path '${pathDep.path}' " +
                    "points outside the project directory.",
                severity = Severity.WARNING
            )

            return basePackage
        }

        val normalizedPath = File(pathDep.path).normalize().path
        val vcs = context.project.vcsProcessed.copy(
            path = normalizedPath.prependPath(context.project.vcsProcessed.path)
        )

        return basePackage.copy(vcs = vcs, vcsProcessed = vcs.normalize())
    }
}

/**
 * Convert a Hex-style version requirement to semver4j range syntax.
 *
 * Handles:
 * - `~>` operator: `~> 0.7` -> `>=0.7.0 <1.0.0`, `~> 0.7.3` -> `>=0.7.3 <0.8.0`
 * - `and` keyword -> space (implicit AND in semver4j)
 * - `or` keyword -> `||`
 */
internal fun convertHexVersionRequirement(requirement: String): String =
    expandTildeOperators(requirement)
        .replace(AND_KEYWORD, " ")
        .replace(OR_KEYWORD, " || ")
        .replace(Regex("\\s+"), " ")
        .trim()

private val AND_KEYWORD = Regex("\\s+and\\s+", RegexOption.IGNORE_CASE)
private val OR_KEYWORD = Regex("\\s+or\\s+", RegexOption.IGNORE_CASE)

/**
 * Expand all ~> (tilde) operators in a version requirement string.
 * - `~> X.Y.Z` -> `>=X.Y.Z <X.(Y+1).0`
 * - `~> X.Y` -> `>=X.Y.0 <(X+1).0.0`
 */
private fun expandTildeOperators(requirement: String): String =
    // Match 3-part versions first, then 2-part versions.
    TILDE_WITH_PATCH.replace(requirement) { match ->
        val (major, minor, patch) = match.destructured
        ">=$major.$minor.$patch <$major.${minor.toInt() + 1}.0"
    }.let { result ->
        TILDE_WITHOUT_PATCH.replace(result) { match ->
            val (major, minor) = match.destructured
            ">=$major.$minor.0 <${major.toInt() + 1}.0.0"
        }
    }

// Regex patterns for converting Hex version requirements to semver4j format.
private val TILDE_WITH_PATCH = Regex("~>\\s*(\\d+)\\.(\\d+)\\.(\\d+)")
private val TILDE_WITHOUT_PATCH = Regex("~>\\s*(\\d+)\\.(\\d+)(?!\\.)")

/**
 * Find the latest version that matches a Hex version requirement.
 */
private fun findMatchingVersion(versions: List<String>, requirement: String): String? {
    val semverRequirement = convertHexVersionRequirement(requirement)
    val rangeList = RangeListFactory.create(semverRequirement)

    return versions
        .mapNotNull { Semver.coerce(it) }
        .sortedDescending()
        .firstOrNull { it.satisfies(rangeList) }
        ?.version
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
    val resolvedPath = workingDir.resolve(path).canonicalFile
    return resolvedPath.startsWith(analysisRoot.canonicalFile)
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
