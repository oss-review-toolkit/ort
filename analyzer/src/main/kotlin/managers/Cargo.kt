/*
 * Copyright (C) 2019 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.analyzer.managers

import com.fasterxml.jackson.databind.JsonNode

import com.moandjiezana.toml.Toml

import java.io.File
import java.util.SortedSet

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
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.textValueOrEmpty
import org.ossreviewtoolkit.utils.core.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.core.ProcessedDeclaredLicense
import org.ossreviewtoolkit.utils.core.log
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxOperator

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
        ) = Cargo(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    override fun command(workingDir: File?) = "cargo"

    override fun transformVersion(output: String) =
        // The version string can be something like:
        // cargo 1.35.0 (6f3e9c367 2019-04-04)
        output.removePrefix("cargo ").substringBefore(' ')

    private fun runMetadata(workingDir: File): String = run(workingDir, "metadata", "--format-version=1").stdout

    /**
     * Cargo.lock is located next to Cargo.toml or in one of the parent directories. The latter is the case when the
     * project is part of a workspace. Cargo.lock is then located next to the Cargo.toml file defining the workspace.
     */
    private fun resolveLockfile(metadata: JsonNode): File {
        val workspaceRoot = metadata["workspace_root"].textValueOrEmpty()
        val workingDir = File(workspaceRoot)
        val lockfile = workingDir.resolve("Cargo.lock")

        requireLockfile(workingDir) { lockfile.isFile }

        return lockfile
    }

    private fun readHashes(lockfile: File): Map<String, String> {
        if (!lockfile.isFile) {
            log.debug { "Cannot determine the hashes of remote artifacts because the Cargo lockfile is missing." }
            return emptyMap()
        }

        val contents = Toml().read(lockfile)
        val metadata = contents.getTable("metadata") ?: return emptyMap()
        val metadataMap = metadata.toMap()

        val metadataMapNotNull = mutableMapOf<String, String>()
        metadataMap.forEach { (key, value) ->
            if (key != null && (value as? String) != null) {
                metadataMapNotNull[key] = value
            }
        }

        return metadataMapNotNull
    }

    /**
     * Check if a package is a project. All path dependencies inside of the analyzer root are treated as project
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
        metadata: JsonNode
    ): PackageReference {
        val node = metadata["packages"].single {
            it["name"].textValue() == name && it["version"].textValue() == version
        }

        val dependencies = node["dependencies"].filter {
            // Filter dev and build dependencies, because they are not transitive.
            val kind = it["kind"].textValueOrEmpty()
            kind != "dev" && kind != "build"
        }.mapNotNull {
            // TODO: Handle renamed dependencies here, see:
            //       https://doc.rust-lang.org/cargo/reference/specifying-dependencies.html#renaming-dependencies-in-cargotoml
            val dependencyName = it["name"].textValue()

            getResolvedVersion(name, version, dependencyName, metadata)?.let { dependencyVersion ->
                buildDependencyTree(dependencyName, dependencyVersion, packages, metadata)
            }
        }.toSortedSet()

        val id = parseCargoId(node)
        val pkg = packages.getValue(id)
        val linkage = if (isProjectDependency(id)) PackageLinkage.PROJECT_STATIC else PackageLinkage.STATIC

        return pkg.toReference(linkage, dependencies)
    }

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        // Get the project name and version. If one of them is missing return null, because this is a workspace
        // definition file that does not contain a project.
        val pkgDefinition = Toml().read(definitionFile)
        val projectName = pkgDefinition.getString("package.name") ?: return emptyList()
        val projectVersion = pkgDefinition.getString("package.version") ?: return emptyList()

        val workingDir = definitionFile.parentFile
        val metadataJson = runMetadata(workingDir)
        val metadata = jsonMapper.readTree(metadataJson)
        val hashes = readHashes(resolveLockfile(metadata))

        val packages = metadata["packages"].associateBy(
            { parseCargoId(it) },
            { parsePackage(it, hashes) }
        )

        val projectId = metadata["workspace_members"]
            .map { it.textValueOrEmpty() }
            .single { it.startsWith("$projectName $projectVersion") }

        val projectNode = metadata["packages"].single { it["id"].textValueOrEmpty() == projectId }
        val groupedDependencies = projectNode["dependencies"].groupBy { it["kind"].textValueOrEmpty() }

        fun getTransitiveDependencies(directDependencies: List<JsonNode>?, scope: String): Scope? {
            if (directDependencies == null) return null

            val transitiveDependencies = directDependencies
                .mapNotNull { dependency ->
                    val dependencyName = dependency["name"].textValue()
                    val version = getResolvedVersion(projectName, projectVersion, dependencyName, metadata)
                    version?.let { Pair(dependencyName, it) }
                }
                .map {
                    buildDependencyTree(name = it.first, version = it.second, packages = packages, metadata = metadata)
                }
                .toSortedSet()

            return Scope(scope, transitiveDependencies)
        }

        val scopes = listOfNotNull(
            getTransitiveDependencies(groupedDependencies[""], "dependencies"),
            getTransitiveDependencies(groupedDependencies["dev"], "dev-dependencies"),
            getTransitiveDependencies(groupedDependencies["build"], "build-dependencies")
        )

        val projectPkg = packages.values.single { pkg ->
            pkg.id.name == projectName && pkg.id.version == projectVersion
        }.let { it.copy(id = it.id.copy(type = managerName)) }

        val homepageUrl = pkgDefinition.getString("package.homepage").orEmpty()
        val authors = pkgDefinition.getList("package.authors", emptyList<String>())
            .mapNotNullTo(sortedSetOf(), ::parseAuthorString)

        val project = Project(
            id = projectPkg.id,
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            authors = authors,
            declaredLicenses = projectPkg.declaredLicenses,
            declaredLicensesProcessed = processDeclaredLicenses(projectPkg.declaredLicenses),
            vcs = projectPkg.vcs,
            vcsProcessed = processProjectVcs(workingDir, projectPkg.vcs, homepageUrl),
            homepageUrl = homepageUrl,
            scopeDependencies = scopes.toSortedSet()
        )

        val nonProjectPackages = packages
            .filterNot { isProjectDependency(it.key) }
            .mapTo(sortedSetOf()) { it.value }

        return listOf(ProjectAnalyzerResult(project, nonProjectPackages))
    }
}

private val PATH_DEPENDENCY_REGEX = Regex("""^.*\(path\+file://(.*)\)$""")

private fun checksumKeyOf(metadata: JsonNode): String {
    val id = parseCargoId(metadata)
    return "\"checksum $id\""
}

private fun parseCargoId(node: JsonNode) = node["id"].textValueOrEmpty()

private fun parseDeclaredLicenses(node: JsonNode): SortedSet<String> {
    val declaredLicenses = node["license"].textValueOrEmpty().split('/')
        .map { it.trim() }
        .filterTo(sortedSetOf()) { it.isNotEmpty() }

    // Cargo allows to declare non-SPDX licenses only by referencing a license file. If a license file is specified, add
    // an unknown declared license to indicate that there is a declared license, but we cannot know which it is at this
    // point.
    // See: https://doc.rust-lang.org/cargo/reference/manifest.html#the-license-and-license-file-fields
    if (node["license_file"].textValueOrEmpty().isNotBlank()) {
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

private fun parsePackage(node: JsonNode, hashes: Map<String, String>): Package {
    val declaredLicenses = parseDeclaredLicenses(node)
    val declaredLicensesProcessed = processDeclaredLicenses(declaredLicenses)

    return Package(
        id = parsePackageId(node),
        authors = parseAuthors(node["authors"]),
        declaredLicenses = declaredLicenses,
        declaredLicensesProcessed = declaredLicensesProcessed,
        description = node["description"].textValueOrEmpty(),
        binaryArtifact = RemoteArtifact.EMPTY,
        sourceArtifact = parseSourceArtifact(node, hashes).orEmpty(),
        homepageUrl = "",
        vcs = parseVcsInfo(node)
    )
}

private fun parsePackageId(node: JsonNode) =
    Identifier(
        type = "Crate",
        // Note that Rust / Cargo do not support package namespaces, see:
        // https://samsieber.tech/posts/2020/09/registry-structure-influence/
        namespace = "",
        name = node["name"].textValueOrEmpty(),
        version = node["version"].textValueOrEmpty()
    )

private fun parseRepositoryUrl(node: JsonNode) = node["repository"].textValueOrEmpty()

private fun parseSourceArtifact(
    node: JsonNode,
    hashes: Map<String, String>
): RemoteArtifact? {
    if (node["source"].textValueOrEmpty() != "registry+https://github.com/rust-lang/crates.io-index") {
        return null
    }

    val name = node["name"]?.textValue() ?: return null
    val version = node["version"]?.textValue() ?: return null
    val url = "https://crates.io/api/v1/crates/$name/$version/download"
    val checksum = checksumKeyOf(node)
    val hash = Hash.create(hashes[checksum].orEmpty())
    return RemoteArtifact(url, hash)
}

private fun parseVcsInfo(node: JsonNode) =
    VcsHost.parseUrl(parseRepositoryUrl(node))

private fun getResolvedVersion(
    parentName: String,
    parentVersion: String,
    dependencyName: String,
    metadata: JsonNode
): String? {
    val node = metadata["resolve"]["nodes"].single {
        it["id"].textValue().startsWith("$parentName $parentVersion")
    }

    // This is null if the dependency is optional and the feature was not enabled. In this case the version was not
    // resolved and the dependency should not appear in the dependency tree. An example for a dependency string is
    // "bitflags 1.0.4 (registry+https://github.com/rust-lang/crates.io-index)", for more details see
    // https://doc.rust-lang.org/cargo/commands/cargo-metadata.html.
    node["dependencies"].forEach {
        val substrings = it.textValue().split(' ')
        require(substrings.size > 1) { "Unexpected format while parsing dependency JSON node." }

        if (substrings[0] == dependencyName) return substrings[1]
    }

    return null
}

/**
 * Parse information about authors from the given [node] with package metadata.
 */
private fun parseAuthors(node: JsonNode?): SortedSet<String> =
    node?.mapNotNullTo(sortedSetOf()) { parseAuthorString(it.textValue()) } ?: sortedSetOf()
