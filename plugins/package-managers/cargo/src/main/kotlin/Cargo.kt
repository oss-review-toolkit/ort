/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

@file:Suppress("TooManyFunctions")

package org.ossreviewtoolkit.plugins.packagemanagers.cargo

import java.io.File

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.decodeFromNativeReader

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.parseAuthorString
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.splitOnWhitespace
import org.ossreviewtoolkit.utils.common.unquote
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.ort.ProcessedDeclaredLicense
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxOperator

private val json = Json {
    ignoreUnknownKeys = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}

private val toml = Toml { ignoreUnknownKeys = true }

/**
 * The [Cargo](https://doc.rust-lang.org/cargo/) package manager for Rust.
 */
class Cargo(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<Cargo>("Cargo") {
        override val globsForDefinitionFiles = listOf("Cargo.toml")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Cargo(type, analysisRoot, analyzerConfig, repoConfig)
    }

    override fun command(workingDir: File?) = "cargo"

    override fun transformVersion(output: String) =
        // The version string can be something like:
        // cargo 1.35.0 (6f3e9c367 2019-04-04)
        output.removePrefix("cargo ").substringBefore(' ')

    /**
     * Cargo.lock is located next to Cargo.toml or in one of the parent directories. The latter is the case when the
     * project is part of a workspace. Cargo.lock is then located next to the Cargo.toml file defining the workspace.
     */
    private fun resolveLockfile(metadata: CargoMetadata): File {
        val workingDir = File(metadata.workspaceRoot)
        val lockfile = workingDir.resolve("Cargo.lock")

        requireLockfile(workingDir) { lockfile.isFile }

        return lockfile
    }

    private fun readHashes(lockfile: File): Map<String, String> {
        if (!lockfile.isFile) {
            logger.debug { "Cannot determine the hashes of remote artifacts because the Cargo lockfile is missing." }
            return emptyMap()
        }

        val contents = lockfile.reader().use { toml.decodeFromNativeReader<CargoLockfile>(it) }
        return when (contents.version) {
            3 -> {
                contents.packages.mapNotNull { pkg ->
                    pkg.checksum?.let { checksum ->
                        val key = "${pkg.name} ${pkg.version} (${pkg.source})"
                        key to checksum
                    }
                }
            }

            else -> {
                contents.metadata.mapNotNull { (k, v) ->
                    k.unquote().withoutPrefix("checksum ")?.let { it to v }
                }
            }
        }.toMap()
    }

    /**
     * Check if a package is a project. All path dependencies inside the analyzer root are treated as project
     * dependencies.
     */
    private fun isProjectDependency(id: String) =
        PATH_DEPENDENCY_REGEX.matchEntire(id)?.groups?.get(1)?.let { match ->
            val packageDir = File(match.value)
            packageDir.startsWith(analysisRoot)
        } ?: false

    private fun buildDependencyTree(
        name: String,
        version: String,
        packages: Map<String, Package>,
        metadata: CargoMetadata
    ): PackageReference {
        val node = metadata.packages.single { it.name == name && it.version == version }

        val dependencies = node.dependencies.filter {
            // Filter dev and build dependencies, because they are not transitive.
            it.kind != "dev" && it.kind != "build"
        }.mapNotNullTo(mutableSetOf()) {
            // TODO: Handle renamed dependencies here, see:
            //       https://doc.rust-lang.org/cargo/reference/specifying-dependencies.html#renaming-dependencies-in-cargotoml
            getResolvedVersion(name, version, it.name, metadata)?.let { dependencyVersion ->
                buildDependencyTree(it.name, dependencyVersion, packages, metadata)
            }
        }

        val pkg = packages.getValue(node.id)
        val linkage = if (isProjectDependency(node.id)) PackageLinkage.PROJECT_STATIC else PackageLinkage.STATIC

        return pkg.toReference(linkage, dependencies)
    }

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        // Get the project name and version. If one of them is missing return null, because this is a workspace
        // definition file that does not contain a project.
        val pkgDefinition = definitionFile.reader().use { toml.decodeFromNativeReader<CargoManifest>(it) }

        val workingDir = definitionFile.parentFile
        val metadataProcess = run(workingDir, "metadata", "--format-version=1")
        val metadata = json.decodeFromString<CargoMetadata>(metadataProcess.stdout)
        val hashes = readHashes(resolveLockfile(metadata))

        val packages = metadata.packages.associateBy(
            { it.id },
            { parsePackage(it, hashes) }
        )

        val projectId = metadata.workspaceMembers.single {
            it.startsWith("${pkgDefinition.pkg.name} ${pkgDefinition.pkg.version}")
        }

        val projectNode = metadata.packages.single { it.id == projectId }
        val groupedDependencies = projectNode.dependencies.groupBy { it.kind.orEmpty() }

        fun getTransitiveDependencies(directDependencies: List<CargoMetadata.Dependency>?, scope: String): Scope? {
            if (directDependencies == null) return null

            val transitiveDependencies = directDependencies
                .mapNotNull { dependency ->
                    val version =
                        getResolvedVersion(pkgDefinition.pkg.name, pkgDefinition.pkg.version, dependency.name, metadata)
                    version?.let { Pair(dependency.name, it) }
                }
                .mapTo(mutableSetOf()) {
                    buildDependencyTree(name = it.first, version = it.second, packages = packages, metadata = metadata)
                }

            return Scope(scope, transitiveDependencies)
        }

        val scopes = setOfNotNull(
            getTransitiveDependencies(groupedDependencies[""], "dependencies"),
            getTransitiveDependencies(groupedDependencies["dev"], "dev-dependencies"),
            getTransitiveDependencies(groupedDependencies["build"], "build-dependencies")
        )

        val projectPkg = packages.values.single { pkg ->
            pkg.id.name == pkgDefinition.pkg.name && pkg.id.version == pkgDefinition.pkg.version
        }.let { it.copy(id = it.id.copy(type = managerName)) }

        val homepageUrl = pkgDefinition.pkg.homepage.orEmpty()
        val authors = pkgDefinition.pkg.authors.mapNotNullTo(mutableSetOf(), ::parseAuthorString)

        val project = Project(
            id = projectPkg.id,
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            authors = authors,
            declaredLicenses = projectPkg.declaredLicenses,
            declaredLicensesProcessed = processDeclaredLicenses(projectPkg.declaredLicenses),
            vcs = projectPkg.vcs,
            vcsProcessed = processProjectVcs(workingDir, projectPkg.vcs, homepageUrl),
            homepageUrl = homepageUrl,
            scopeDependencies = scopes
        )

        val nonProjectPackages = packages
            .filterNot { isProjectDependency(it.key) }
            .mapTo(mutableSetOf()) { it.value }

        return listOf(ProjectAnalyzerResult(project, nonProjectPackages))
    }
}

private val PATH_DEPENDENCY_REGEX = Regex("""^.*\(path\+file://(.*)\)$""")

private fun parseDeclaredLicenses(pkg: CargoMetadata.Package): Set<String> {
    val declaredLicenses = pkg.license.orEmpty().split('/')
        .map { it.trim() }
        .filterTo(mutableSetOf()) { it.isNotEmpty() }

    // Cargo allows declaring non-SPDX licenses only by referencing a license file. If a license file is specified, add
    // an unknown declared license to indicate that there is a declared license, but we cannot know which it is at this
    // point.
    // See: https://doc.rust-lang.org/cargo/reference/manifest.html#the-license-and-license-file-fields
    if (pkg.licenseFile.orEmpty().isNotBlank()) {
        declaredLicenses += SpdxConstants.NOASSERTION
    }

    return declaredLicenses
}

private fun processDeclaredLicenses(licenses: Set<String>): ProcessedDeclaredLicense =
    // While the previously used "/" was not explicit about the intended license operator, the community consensus
    // seems to be that an existing "/" should be interpreted as "OR", see e.g. the discussions at
    // https://github.com/rust-lang/cargo/issues/2039
    // https://github.com/rust-lang/cargo/pull/4920
    DeclaredLicenseProcessor.process(licenses, operator = SpdxOperator.OR)

private fun parsePackage(pkg: CargoMetadata.Package, hashes: Map<String, String>): Package {
    val declaredLicenses = parseDeclaredLicenses(pkg)
    val declaredLicensesProcessed = processDeclaredLicenses(declaredLicenses)

    return Package(
        id = Identifier(
            type = "Crate",
            // Note that Rust / Cargo do not support package namespaces, see:
            // https://samsieber.tech/posts/2020/09/registry-structure-influence/
            namespace = "",
            name = pkg.name,
            version = pkg.version
        ),
        authors = pkg.authors.mapNotNullTo(mutableSetOf()) { parseAuthorString(it) },
        declaredLicenses = declaredLicenses,
        declaredLicensesProcessed = declaredLicensesProcessed,
        description = pkg.description.orEmpty(),
        binaryArtifact = RemoteArtifact.EMPTY,
        sourceArtifact = parseSourceArtifact(pkg, hashes).orEmpty(),
        homepageUrl = pkg.homepage.orEmpty(),
        vcs = VcsHost.parseUrl(pkg.repository.orEmpty())
    )
}

// Match source dependencies that directly reference git repositories. The specified tag or branch
// name is ignored (i.e. not captured) in favor of the actual commit hash that they currently refer
// to.
// See https://doc.rust-lang.org/cargo/reference/specifying-dependencies.html#specifying-dependencies-from-git-repositories
// for the specification for this kind of dependency.
private val GIT_DEPENDENCY_REGEX = Regex("git\\+(https://.*)\\?(?:rev|tag|branch)=.+#([0-9a-zA-Z]+)")

private fun parseSourceArtifact(pkg: CargoMetadata.Package, hashes: Map<String, String>): RemoteArtifact? {
    val source = pkg.source ?: return null

    if (source == "registry+https://github.com/rust-lang/crates.io-index") {
        val url = "https://crates.io/api/v1/crates/${pkg.name}/${pkg.version}/download"
        val hash = Hash.create(hashes[pkg.id].orEmpty())
        return RemoteArtifact(url, hash)
    }

    val match = GIT_DEPENDENCY_REGEX.matchEntire(source) ?: return null
    val (url, hash) = match.destructured
    return RemoteArtifact(url, Hash.create(hash))
}

private fun getResolvedVersion(
    parentName: String,
    parentVersion: String,
    dependencyName: String,
    metadata: CargoMetadata
): String? {
    val node = metadata.resolve.nodes.single { it.id.startsWith("$parentName $parentVersion") }

    // This is empty if the dependency is optional and the feature was not enabled. In that case the version was not
    // resolved and the dependency should not appear in the dependency tree. An example for a dependency string is
    // "bitflags 1.0.4 (registry+https://github.com/rust-lang/crates.io-index)", for more details see
    // https://doc.rust-lang.org/cargo/commands/cargo-metadata.html.
    node.dependencies.forEach {
        val substrings = it.splitOnWhitespace()
        require(substrings.size > 1) { "Unexpected format while parsing dependency '$it'." }

        if (substrings[0] == dependencyName) return substrings[1]
    }

    return null
}
