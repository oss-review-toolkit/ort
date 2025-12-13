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

import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.TomlElement
import net.peanuuutz.tomlkt.decodeFromNativeReader

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.SourceCodeOrigin
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.utils.prependPath
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packagemanagers.gleam.GleamManifest.Package.SourceType
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl

import org.semver4j.Semver
import org.semver4j.range.RangeListFactory

private const val GLEAM_TOML = "gleam.toml"
private const val MANIFEST_TOML = "manifest.toml"
private const val SCOPE_DEPENDENCIES = "dependencies"
private const val SCOPE_DEV_DEPENDENCIES = "dev-dependencies"

private val toml = Toml { ignoreUnknownKeys = true }

/**
 * The [Gleam](https://gleam.run/) package manager for Gleam.
 *
 * This package manager parses `gleam.toml` (project manifest) and `manifest.toml` (lockfile) files
 * to extract dependency information. Gleam packages are hosted on [Hex](https://hex.pm/).
 */
@OrtPlugin(
    displayName = "Gleam",
    description = "The package manager for Gleam.",
    factory = PackageManagerFactory::class
)
class Gleam internal constructor(
    override val descriptor: PluginDescriptor = GleamFactory.descriptor,
    private val hexApiClientFactory: () -> HexApiClient
) : PackageManager("Gleam") {
    constructor(descriptor: PluginDescriptor = GleamFactory.descriptor) : this(descriptor, ::HexApiClient)

    override val globsForDefinitionFiles = listOf(GLEAM_TOML)

    override fun mapDefinitionFiles(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ): List<File> {
        val projectFiles = definitionFiles.toMutableList()

        // Ignore definition files from build directories that reside next to other definition files,
        // to avoid dependency gleam.toml files from being recognized as projects.
        var index = 0
        while (index < projectFiles.size - 1) {
            val projectFile = projectFiles[index++]
            val buildDir = projectFile.resolveSibling("build")
            projectFiles.subList(index, projectFiles.size).removeAll { it.startsWith(buildDir) }
        }

        return projectFiles
    }

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile
        val manifestFile = workingDir / MANIFEST_TOML

        val gleamToml = parseGleamToml(definitionFile)
        val hasDependencies = gleamToml.dependencies.isNotEmpty() || gleamToml.devDependencies.isNotEmpty()

        val vcsUrl = gleamToml.repository?.toUrl().orEmpty()
        val vcs = VcsHost.parseUrl(vcsUrl)
        val projectVcs = processProjectVcs(workingDir, vcs, vcsUrl)

        if (!hasDependencies) {
            val project = createProject(definitionFile, gleamToml, emptySet(), vcs, projectVcs)
            return listOf(ProjectAnalyzerResult(project, packages = emptySet()))
        }

        requireLockfile(analysisRoot, workingDir, analyzerConfig.allowDynamicVersions) {
            manifestFile.isFile
        }

        val hexClient = hexApiClientFactory()

        if (!manifestFile.isFile) {
            return resolveDynamically(workingDir, definitionFile, gleamToml, hexClient, projectVcs)
        }

        val manifest = parseManifest(manifestFile)
        val packagesByName = manifest.packages.associateBy { it.name }

        val issues = mutableListOf<Issue>()
        val validPackages = manifest.packages.filter { pkg ->
            if (pkg.source == SourceType.LOCAL && pkg.localPath != null) {
                if (!isValidLocalPath(workingDir, pkg.localPath)) {
                    issues += Issue(
                        source = projectType,
                        message = "Path dependency '${pkg.name}' with path '${pkg.localPath}' " +
                            "points outside the project directory and was skipped.",
                        severity = Severity.WARNING
                    )

                    return@filter false
                }
            }

            true
        }

        val projectHomepageUrl = gleamToml.findHomepageUrl().ifEmpty { vcsUrl }
        val packages = validPackages
            .filter { it.name != gleamToml.name }
            .mapTo(mutableSetOf()) { it.toOrtPackage(hexClient, projectVcs, projectHomepageUrl) }

        val scopes = buildScopes(gleamToml, packagesByName)
        val project = createProject(definitionFile, gleamToml, scopes, vcs, projectVcs)

        return listOf(ProjectAnalyzerResult(project, packages, issues))
    }

    private fun parseGleamToml(file: File) = file.reader().use { toml.decodeFromNativeReader<GleamToml>(it) }

    private fun parseManifest(file: File) = file.reader().use { toml.decodeFromNativeReader<GleamManifest>(it) }

    private fun buildScopes(gleamToml: GleamToml, packagesByName: Map<String, GleamManifest.Package>): Set<Scope> {
        val dependenciesScope = Scope(
            name = SCOPE_DEPENDENCIES,
            dependencies = buildPackageReferences(gleamToml.dependencies.keys, packagesByName)
        )

        val devDependenciesScope = Scope(
            name = SCOPE_DEV_DEPENDENCIES,
            dependencies = buildPackageReferences(gleamToml.devDependencies.keys, packagesByName)
        )

        return setOfNotNull(
            dependenciesScope.takeIf { it.dependencies.isNotEmpty() },
            devDependenciesScope.takeIf { it.dependencies.isNotEmpty() }
        )
    }

    private fun buildPackageReferences(
        dependencyNames: Set<String>,
        packagesByName: Map<String, GleamManifest.Package>,
        visited: Set<String> = emptySet()
    ): Set<PackageReference> =
        dependencyNames.mapNotNullTo(mutableSetOf()) { name ->
            // Avoid infinite loops for circular dependencies
            if (name in visited) return@mapNotNullTo null

            val pkg = packagesByName[name] ?: return@mapNotNullTo null

            PackageReference(
                id = pkg.toIdentifier(),
                dependencies = buildPackageReferences(
                    pkg.requirements.toSet(),
                    packagesByName,
                    visited + name
                )
            )
        }

    private fun createProject(
        definitionFile: File,
        gleamToml: GleamToml,
        scopes: Set<Scope>,
        vcs: VcsInfo,
        vcsProcessed: VcsInfo
    ): Project {
        val vcsUrl = gleamToml.repository?.toUrl().orEmpty()

        return Project(
            id = Identifier(
                type = projectType,
                namespace = "",
                name = gleamToml.name,
                version = gleamToml.version
            ),
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            authors = emptySet(),
            declaredLicenses = gleamToml.licences.toSet(),
            vcs = vcs,
            vcsProcessed = vcsProcessed,
            homepageUrl = gleamToml.findHomepageUrl().ifEmpty { vcsUrl },
            scopeDependencies = scopes
        )
    }

    /**
     * Resolve dependencies dynamically when no lockfile is present.
     * Only resolves top-level dependencies (no transitive resolution).
     */
    private fun resolveDynamically(
        workingDir: File,
        definitionFile: File,
        gleamToml: GleamToml,
        hexClient: HexApiClient,
        projectVcs: VcsInfo
    ): List<ProjectAnalyzerResult> {
        val issues = mutableListOf<Issue>()
        val vcsUrl = gleamToml.repository?.toUrl().orEmpty()
        val projectHomepageUrl = gleamToml.findHomepageUrl().ifEmpty { vcsUrl }

        val resolvedDeps = resolveTopLevelPackages(
            workingDir,
            gleamToml.dependencies,
            hexClient,
            projectVcs,
            projectHomepageUrl,
            issues
        )
        val resolvedDevDeps = resolveTopLevelPackages(
            workingDir,
            gleamToml.devDependencies,
            hexClient,
            projectVcs,
            projectHomepageUrl,
            issues
        )

        val allResolved = resolvedDeps + resolvedDevDeps
        val packages = allResolved.values.mapTo(mutableSetOf()) { it.toOrtPackage(hexClient) }

        val scopes = buildSet {
            if (resolvedDeps.isNotEmpty()) {
                add(
                    Scope(
                        name = SCOPE_DEPENDENCIES,
                        dependencies = resolvedDeps.values.map { PackageReference(it.toIdentifier()) }.toSet()
                    )
                )
            }

            if (resolvedDevDeps.isNotEmpty()) {
                add(
                    Scope(
                        name = SCOPE_DEV_DEPENDENCIES,
                        dependencies = resolvedDevDeps.values.map { PackageReference(it.toIdentifier()) }.toSet()
                    )
                )
            }
        }

        issues += Issue(
            source = projectType,
            message = "Dependencies were resolved dynamically as no lockfile was present. " +
                "Only the latest matching versions of direct dependencies were resolved without " +
                "transitive dependency resolution. The results are not reproducible. " +
                "Consider running 'gleam deps download' to generate a manifest.toml lockfile.",
            severity = Severity.WARNING
        )

        val vcs = VcsHost.parseUrl(vcsUrl)
        val project = createProject(definitionFile, gleamToml, scopes, vcs, projectVcs)
        return listOf(ProjectAnalyzerResult(project, packages, issues))
    }

    private fun resolveHexDependency(
        name: String,
        requirement: String,
        hexClient: HexApiClient,
        issues: MutableList<Issue>
    ): ResolvedPackage.Hex? {
        val packageInfo = hexClient.getPackageInfo(name) ?: return null

        // Get all versions from the releases list (Hex API returns them newest first)
        val versions = packageInfo.releases.map { it.version }

        // Find the latest version that matches the requirement
        val version = findMatchingVersion(versions, requirement)
        if (version == null) {
            issues += Issue(
                source = projectType,
                message = "No version of '$name' satisfies requirement '$requirement'. Skipping.",
                severity = Severity.WARNING
            )
            return null
        }

        val releaseInfo = hexClient.getReleaseInfo(name, version) ?: return null

        return ResolvedPackage.Hex(
            name = name,
            version = version,
            checksum = releaseInfo.checksum,
            hexInfo = packageInfo
        )
    }

    private fun resolvePathDependency(
        name: String,
        dep: GleamDependency.Path,
        workingDir: File,
        projectVcs: VcsInfo,
        projectHomepageUrl: String,
        issues: MutableList<Issue>
    ): ResolvedPackage.Path? {
        if (!isValidLocalPath(workingDir, dep.path)) {
            issues += Issue(
                source = projectType,
                message = "Path dependency '$name' with path '${dep.path}' " +
                    "points outside the project directory and was skipped.",
                severity = Severity.WARNING
            )
            return null
        }

        val localGleamToml = workingDir.resolve(dep.path).resolve(GLEAM_TOML)
        val version = if (localGleamToml.isFile) parseGleamToml(localGleamToml).version else ""

        return ResolvedPackage.Path(
            name = name,
            version = version,
            path = dep.path,
            projectVcs = projectVcs,
            projectHomepageUrl = projectHomepageUrl
        )
    }

    private fun resolveTopLevelPackages(
        workingDir: File,
        dependencies: Map<String, TomlElement>,
        hexClient: HexApiClient,
        projectVcs: VcsInfo,
        projectHomepageUrl: String,
        issues: MutableList<Issue>
    ): Map<String, ResolvedPackage> =
        dependencies.mapNotNull { (name, element) ->
            when (val dep = GleamDependency.fromToml(element)) {
                is GleamDependency.Hex -> resolveHexDependency(name, dep.version, hexClient, issues)
                    ?.let { name to it }

                is GleamDependency.Git -> name to ResolvedPackage.Git(name, dep.url, dep.ref)
                is GleamDependency.Path -> resolvePathDependency(
                    name,
                    dep,
                    workingDir,
                    projectVcs,
                    projectHomepageUrl,
                    issues
                )?.let { name to it }
            }
        }.toMap()

    private fun findMatchingVersion(versions: List<String>, requirement: String): String? {
        val semverRequirement = convertHexVersionRequirement(requirement)
        val rangeList = RangeListFactory.create(semverRequirement)

        // Sort versions in descending order to find the latest matching version.
        return versions
            .mapNotNull { Semver.coerce(it) }
            .sortedDescending()
            .firstOrNull { it.satisfies(rangeList) }
            ?.version
    }
}

/**
 * Represents a dynamically resolved package.
 */
private sealed interface ResolvedPackage {
    val name: String

    fun toIdentifier(): Identifier
    fun toOrtPackage(hexClient: HexApiClient): Package

    /**
     * A package resolved from the Hex API.
     */
    data class Hex(
        override val name: String,
        val version: String,
        val checksum: String,
        val hexInfo: HexPackageInfo
    ) : ResolvedPackage {
        override fun toIdentifier() = Identifier("Hex", "", name, version)

        override fun toOrtPackage(hexClient: HexApiClient): Package {
            val repositoryUrl = hexInfo.meta?.links?.get("Repository").orEmpty()
            val vcs = VcsHost.parseUrl(repositoryUrl)
            val cpe = generateCpe(repositoryUrl, version)

            return Package(
                id = toIdentifier(),
                cpe = cpe,
                authors = resolveAuthors(hexInfo.owners, hexClient),
                declaredLicenses = hexInfo.meta?.licenses?.toSet().orEmpty(),
                description = hexInfo.meta?.description.orEmpty(),
                homepageUrl = hexInfo.meta?.links?.get("Website") ?: "https://hex.pm/packages/$name",
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact(
                    url = "https://repo.hex.pm/tarballs/$name-$version.tar",
                    hash = Hash(checksum, HashAlgorithm.SHA256)
                ),
                vcs = vcs,
                vcsProcessed = vcs.normalize(),
                sourceCodeOrigins = listOf(SourceCodeOrigin.ARTIFACT, SourceCodeOrigin.VCS)
            )
        }
    }

    /**
     * A git package resolved using the ref from gleam.toml.
     */
    data class Git(
        override val name: String,
        val url: String,
        val ref: String? = null
    ) : ResolvedPackage {
        override fun toIdentifier() = Identifier("OTP", "", name, ref.orEmpty())

        override fun toOrtPackage(hexClient: HexApiClient): Package {
            val vcs = VcsHost.parseUrl(url).copy(revision = ref.orEmpty())
            val cpe = generateCpe(url, "")

            return Package(
                id = toIdentifier(),
                cpe = cpe,
                authors = emptySet(),
                declaredLicenses = emptySet(),
                description = "",
                homepageUrl = toHomepageUrl(url),
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact.EMPTY,
                vcs = vcs,
                vcsProcessed = vcs.normalize(),
                sourceCodeOrigins = listOf(SourceCodeOrigin.VCS)
            )
        }
    }

    /**
     * A local path package resolved by reading its gleam.toml.
     */
    data class Path(
        override val name: String,
        val version: String,
        val path: String,
        val projectVcs: VcsInfo,
        val projectHomepageUrl: String
    ) : ResolvedPackage {
        override fun toIdentifier() = Identifier("OTP", "", name, version)

        override fun toOrtPackage(hexClient: HexApiClient): Package {
            val normalizedPath = File(path).normalize().path
            val vcs = projectVcs.copy(
                path = normalizedPath.prependPath(projectVcs.path)
            )

            return Package(
                id = toIdentifier(),
                authors = emptySet(),
                declaredLicenses = emptySet(),
                description = "",
                homepageUrl = projectHomepageUrl,
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact.EMPTY,
                vcs = vcs,
                vcsProcessed = vcs.normalize(),
                sourceCodeOrigins = listOf(SourceCodeOrigin.VCS)
            )
        }
    }
}

private fun GleamManifest.Package.toIdentifier(): Identifier =
    Identifier(
        type = if (source == SourceType.HEX) "Hex" else "OTP",
        namespace = "",
        name = name,
        version = version
    )

/**
 * Resolve VCS info based on the package source type.
 */
private fun GleamManifest.Package.resolveVcsInfo(hexInfo: HexPackageInfo?, projectVcs: VcsInfo): VcsInfo =
    when (source) {
        SourceType.LOCAL -> {
            val normalizedPath = File(localPath.orEmpty()).normalize().path
            projectVcs.copy(path = normalizedPath.prependPath(projectVcs.path))
        }

        SourceType.GIT -> VcsHost.parseUrl(repo.orEmpty()).let {
            if (!commit.isNullOrEmpty()) it.copy(revision = commit) else it
        }

        SourceType.HEX -> VcsHost.parseUrl(hexInfo?.meta?.links?.get("Repository").orEmpty())
    }

/**
 * Get the repository URL for CPE generation based on package source type.
 */
private fun GleamManifest.Package.resolveRepositoryUrl(hexInfo: HexPackageInfo?): String =
    when (source) {
        SourceType.GIT -> repo.orEmpty()
        SourceType.HEX -> hexInfo?.meta?.links?.get("Repository").orEmpty()
        else -> ""
    }

/**
 * Get the homepage URL based on package source type.
 */
private fun GleamManifest.Package.resolveHomepageUrl(hexInfo: HexPackageInfo?, projectHomepageUrl: String): String =
    when (source) {
        SourceType.HEX -> hexInfo?.meta?.links?.get("Website") ?: "https://hex.pm/packages/$name"
        SourceType.GIT -> toHomepageUrl(repo.orEmpty())
        SourceType.LOCAL -> projectHomepageUrl
    }

/**
 * Create a source artifact for Hex packages with a checksum.
 */
private fun GleamManifest.Package.createSourceArtifact(): RemoteArtifact =
    if (source == SourceType.HEX && outerChecksum != null) {
        RemoteArtifact(
            url = "https://repo.hex.pm/tarballs/$name-$version.tar",
            hash = Hash(outerChecksum, HashAlgorithm.SHA256)
        )
    } else {
        RemoteArtifact.EMPTY
    }

private fun GleamManifest.Package.toOrtPackage(
    hexClient: HexApiClient,
    projectVcs: VcsInfo,
    projectHomepageUrl: String
): Package {
    val hexInfo = if (source == SourceType.HEX) hexClient.getPackageInfo(name) else null
    val vcs = resolveVcsInfo(hexInfo, projectVcs)
    val repositoryUrl = resolveRepositoryUrl(hexInfo)

    return Package(
        id = toIdentifier(),
        cpe = generateCpe(repositoryUrl, version),
        authors = hexInfo?.let { resolveAuthors(it.owners, hexClient) }.orEmpty(),
        declaredLicenses = hexInfo?.meta?.licenses?.toSet().orEmpty(),
        description = hexInfo?.meta?.description.orEmpty(),
        homepageUrl = resolveHomepageUrl(hexInfo, projectHomepageUrl),
        binaryArtifact = RemoteArtifact.EMPTY,
        sourceArtifact = createSourceArtifact(),
        vcs = vcs,
        vcsProcessed = vcs.normalize(),
        sourceCodeOrigins = if (source == SourceType.HEX) {
            listOf(SourceCodeOrigin.ARTIFACT, SourceCodeOrigin.VCS)
        } else {
            listOf(SourceCodeOrigin.VCS)
        }
    )
}

/**
 * Resolve owners to formatted author strings by fetching user details from Hex API.
 * Falls back to username if user lookup fails.
 */
private fun resolveAuthors(owners: List<HexPackageInfo.Owner>, hexClient: HexApiClient): Set<String> =
    owners.mapNotNull { it.username }
        .mapTo(mutableSetOf()) { username ->
            val userInfo = hexClient.getUserInfo(username) ?: HexUserInfo(username)
            val name = userInfo.fullName ?: userInfo.username
            userInfo.email?.takeIf { it.isNotEmpty() }?.let { "$name <$it>" } ?: name
        }

/**
 * Generate a CPE identifier from a GitHub repository URL.
 * Returns null if the URL is not a valid GitHub URL.
 *
 * This follows the EEF CNA (Erlang Ecosystem Foundation CVE Numbering Authority) documented process
 * for generating CPE identifiers. This code will be updated if the EEF CNA processes change.
 */
private fun generateCpe(repositoryUrl: String, version: String): String? {
    if (repositoryUrl.isBlank()) return null

    // Parse repository URL to extract owner and repo
    // e.g., https://github.com/gleam-lang/stdlib -> owner=gleam-lang, repo=stdlib
    val vcsInfo = VcsHost.parseUrl(repositoryUrl)
    if (vcsInfo == VcsInfo.EMPTY) return null

    // Extract path segments from normalized GitHub URL
    val githubPrefix = "https://github.com/"
    if (!vcsInfo.url.startsWith(githubPrefix)) return null

    val pathSegments = vcsInfo.url.removePrefix(githubPrefix).split("/")
    if (pathSegments.size < 2) return null

    val owner = pathSegments[0]
    val repo = pathSegments[1].removeSuffix(".git")

    return "cpe:2.3:a:$owner:$repo:$version:*:*:*:*:*:*:*"
}

/**
 * Validate that a local path dependency stays within the project directory.
 * Returns true if the resolved path is within the working directory.
 */
private fun isValidLocalPath(workingDir: File, path: String): Boolean {
    val resolvedPath = workingDir.resolve(path).canonicalFile
    return resolvedPath.relativeToOrNull(workingDir.canonicalFile) != null
}

/**
 * Convert a VCS URL to a browsable homepage URL.
 * Uses VcsHost to properly handle different URL formats (SSH, HTTPS, SCP-style).
 */
private fun toHomepageUrl(vcsUrl: String): String {
    if (vcsUrl.isBlank()) return vcsUrl
    // Normalize the URL first to handle SCP-style URLs like git@github.com:user/repo
    val normalizedUrl = normalizeVcsUrl(vcsUrl)
    val host = VcsHost.fromUrl(normalizedUrl) ?: return vcsUrl
    val vcsInfo = host.toVcsInfo(normalizedUrl) ?: return vcsUrl
    return host.toPermalink(vcsInfo) ?: vcsUrl
}
